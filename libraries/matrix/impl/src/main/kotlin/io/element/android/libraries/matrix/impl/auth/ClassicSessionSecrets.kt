/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.ElementClassicSession
import io.element.android.libraries.matrix.api.core.UserId
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.SecretsBundleWithUserId
import timber.log.Timber

/** Element Classic secret import is independent from session/device creation. */
internal object ClassicSessionSecrets {
    suspend fun importInto(session: ElementClassicSession?, client: Client) {
        session
            ?.takeIf {
                // Note: the SDK will also do this check
                it.userId.value == client.userId()
            }
            ?.let {
                val secrets = it.secrets
                val roomKeysVersion = it.roomKeysVersion
                if (secrets == null || roomKeysVersion == null) {
                    Timber.d("No secrets or roomKeysVersion found for Element Classic session ${it.userId}, skipping import")
                } else {
                    Timber.d("Trying to import secrets for Element Classic session ${it.userId}")
                    runCatchingExceptions {
                        SecretsBundleWithUserId.fromStr(
                            userId = it.userId.value,
                            bundle = secrets,
                            backupInfo = roomKeysVersion,
                        ).use { secretsBundle ->
                            client.encryption().importSecretsBundle(secretsBundle)
                        }
                    }.onFailure { failure ->
                        Timber.e(failure, "Failed to import secrets for Element Classic session ${it.userId}")
                    }
                }
            }
    }

    fun containsBackupKey(
        userId: UserId,
        secrets: String,
        backupInfo: String,
    ): Boolean {
        return try {
            SecretsBundleWithUserId.fromStr(
                userId = userId.value,
                bundle = secrets,
                backupInfo = backupInfo,
            ).use { secretsBundle ->
                secretsBundle.containsBackupKey()
            }
        } catch (failure: Exception) {
            Timber.e(failure, "Failed to parse secrets for Element Classic session $userId")
            false
        }
    }
}
