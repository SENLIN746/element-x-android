/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.androidutils.crypto.BootstrapFiles
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.security.MessageDigest

@Inject
@SingleIn(AppScope::class)
class AccountVault(private val files: BootstrapFiles) {
    internal val changes = MutableStateFlow(0L)
    private fun name(home: String, user: String): String = "agentim-account-" + MessageDigest.getInstance("SHA-256")
        .digest("$home\n$user".toByteArray(Charsets.UTF_8)).take(20).joinToString("") { "%02x".format(it) }

    internal fun read(home: String, user: String): JSONObject? = files.read(name(home, user))?.let { JSONObject(it.toString(Charsets.UTF_8)) }
    internal fun save(key: AccountCredential, ready: Boolean, exported: Boolean = false) {
        val record = JSONObject().put("credential", key.json()).put("ready", ready).put("exported", exported)
        val bytes = record.toString().toByteArray(Charsets.UTF_8)
        files.write(name(key.homeserver, key.userId), bytes)
        check(files.read(name(key.homeserver, key.userId))?.contentEquals(bytes) == true) { "Private account backup could not be verified" }
    }

    internal fun pending(): JSONObject? = files.read("agentim-account-login")?.let { JSONObject(it.toString(Charsets.UTF_8)) }
    internal fun begin(key: AccountCredential): JSONObject {
        pending()?.let {
            require(it.getString("home") == key.homeserver && it.getString("user") == key.userId && it.getString("credential") == key.id) {
                "Resume the pending account login first"
            }
            return it
        }
        return JSONObject().put("home", key.homeserver).put("user", key.userId).put("credential", key.id)
            .put("attempt", randomAccountId()).put("device", randomAccountId()).also {
                files.write("agentim-account-login", it.toString().toByteArray(Charsets.UTF_8))
                changes.value += 1
            }
    }
    internal fun finish(attempt: String) {
        require(pending()?.getString("attempt") == attempt)
        files.delete("agentim-account-login")
        changes.value += 1
    }
}
