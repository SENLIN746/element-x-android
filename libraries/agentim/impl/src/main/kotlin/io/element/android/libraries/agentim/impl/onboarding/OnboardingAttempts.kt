/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.onboarding

import android.util.Base64
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentim.api.onboarding.PendingAgentRegistration
import io.element.android.libraries.androidutils.crypto.BootstrapFiles
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

internal class OnboardingAttempt(
    val homeserver: String, val localpart: String, val displayName: String,
    val attemptId: String, val deviceId: String, val verifier: String, val userId: String? = null,
) {
    fun challenge(): String = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun metadata() = PendingAgentRegistration(homeserver, localpart, displayName)
    override fun toString(): String = "OnboardingAttempt(<private>)"
}

private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

@Inject
@SingleIn(AppScope::class)
class OnboardingAttempts(private val files: BootstrapFiles) {
    internal val changes = MutableStateFlow(0L)

    @Synchronized
    internal fun pending(): OnboardingAttempt? = files.read("agentim-registration")?.let {
        val json = JSONObject(it.toString(Charsets.UTF_8))
        OnboardingAttempt(
            json.getString("homeserver"),
            json.getString("localpart"),
            json.getString("displayName"),
            json.getString("attempt"),
            json.getString("device"),
            json.getString("verifier"),
            json.optString("user").takeIf(String::isNotEmpty),
        )
    }

    @Synchronized
    internal fun begin(homeserver: String, localpart: String, displayName: String): OnboardingAttempt {
        pending()?.let {
            check(it.homeserver == homeserver && it.localpart == localpart) { "Resume the pending Agent registration first" }
            return it
        }
        fun random() = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        return OnboardingAttempt(homeserver, localpart, displayName, random(), random(), random()).also(::save)
    }

    @Synchronized
    internal fun bind(attempt: OnboardingAttempt, userId: String) {
        val current = requireNotNull(pending())
        check(current.attemptId == attempt.attemptId && (current.userId == null || current.userId == userId))
        save(OnboardingAttempt(current.homeserver, current.localpart, current.displayName, current.attemptId, current.deviceId, current.verifier, userId))
    }

    private fun save(attempt: OnboardingAttempt) {
        val json = JSONObject().put("homeserver", attempt.homeserver).put("localpart", attempt.localpart)
            .put("displayName", attempt.displayName).put("attempt", attempt.attemptId).put("device", attempt.deviceId)
            .put("verifier", attempt.verifier).put("user", attempt.userId)
        files.write("agentim-registration", json.toString().toByteArray(Charsets.UTF_8))
        changes.value += 1
    }

    @Synchronized
    internal fun finish(attempt: OnboardingAttempt) {
        check(pending()?.attemptId == attempt.attemptId)
        files.delete("agentim-registration")
        changes.value += 1
    }
}
