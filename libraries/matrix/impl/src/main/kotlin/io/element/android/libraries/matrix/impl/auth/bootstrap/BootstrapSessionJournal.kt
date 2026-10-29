/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth.bootstrap

import dev.zacsweers.metro.Inject
import io.element.android.libraries.androidutils.crypto.BootstrapFiles
import io.element.android.libraries.sessionstorage.api.SessionData

/** Owns a newly issued Android session before the SDK is allowed to initialize its device keys. */
@Inject
class BootstrapSessionJournal(private val files: BootstrapFiles) {
    fun read(): SessionData? = files.read("agentim-native-session")?.let(BootstrapSessionJson::decode)

    fun write(session: SessionData) {
        val current = read()
        check(current == null || (current.userId == session.userId && current.deviceId == session.deviceId &&
            current.sessionPath == session.sessionPath)) { "Restore the pending native device first" }
        files.write("agentim-native-session", BootstrapSessionJson.encode(session))
    }

    fun finish(userId: String, deviceId: String) {
        read()?.let {
            check(it.userId == userId && it.deviceId == deviceId)
            files.delete("agentim-native-session")
        }
    }
}
