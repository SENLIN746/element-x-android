/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import org.json.JSONObject

/** Fixed attempt/device survives response loss; the native journal precedes crypto initialization. */
@Inject
class AccountLogin(private val vault: AccountVault, private val keys: AccountKeyManager, private val http: AccountHttp,
    private val authentication: MatrixAuthenticationService) {
    internal suspend fun restore(key: AccountCredential): String {
        keys.prove(key)
        // Reject a conflicting pending attempt before replacing any saved account key.
        val pending = vault.begin(key)
        vault.save(key, true, exported = true)
        return resume(pending)
    }
    internal suspend fun resume(pending: JSONObject = requireNotNull(vault.pending())): String {
        val home = pending.getString("home")
        val user = pending.getString("user")
        val device = pending.getString("device")
        val stored = requireNotNull(vault.read(home, user))
        val key = AccountCredential.parse(stored.getJSONObject("credential"))
        require(key.id == pending.getString("credential"))
        authentication.resumeAgentBootstrap(home, user, device).getOrThrow()?.let { return it.value }
        keys.prove(key)
        authentication.cancelOAuthLogin().getOrThrow()
        require(http.base(authentication.setHomeserver(home).getOrThrow().url) == home)
        val body = key.proof().put("attempt_id", pending.getString("attempt")).put("device_id", device).put("expected_user_id", user)
        val grant = http.request(home, "/_synapse/client/agentim/v2/account/login", body)
        require(
            grant.getString("user_id") == user && grant.getString("device_id") == device &&
            grant.getString("attempt_id") == pending.getString("attempt")
        ) { "Account device binding changed" }
        return authentication.loginWithAccountSession(
            grant.getString("access_token"),
            grant.optString("refresh_token").takeUnless { it.isBlank() || it == "null" },
            user,
            device
        ).getOrThrow().value
    }
}
