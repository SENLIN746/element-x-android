/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api.account

import android.net.Uri

/** Portable account authority is distinct from the device's encrypted message history. */
interface AgentAccountAccessService {
    suspend fun ensure(userId: String): Result<AccountBackupStatus>
    suspend fun export(userId: String, destination: Uri): Result<AccountBackupStatus>
    suspend fun verify(userId: String, source: Uri): Result<AccountBackupStatus>
    suspend fun revoke(userId: String): Result<Unit>
    suspend fun restore(source: Uri): Result<String>
    suspend fun resume(): Result<String>
    suspend fun pending(): Result<Boolean>
    fun start()
}

data class AccountBackupStatus(val userId: String, val homeserver: String, val exportedAndVerified: Boolean)
