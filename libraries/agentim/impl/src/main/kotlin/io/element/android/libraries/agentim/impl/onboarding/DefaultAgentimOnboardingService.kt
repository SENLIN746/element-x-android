/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.onboarding

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentim.api.onboarding.AgentimOnboardingService
import io.element.android.libraries.agentim.api.onboarding.AgentimRegistrationPolicy
import io.element.android.libraries.agentim.api.onboarding.AgentimRegistrationRejected
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultAgentimOnboardingService(
    private val http: OnboardingHttp,
    private val attempts: OnboardingAttempts,
    private val authentication: MatrixAuthenticationService,
    private val completion: OnboardingCompletion,
    @AppCoroutineScope private val scope: CoroutineScope,
) : AgentimOnboardingService {
    private val started = AtomicBoolean()
    private val mutex = Mutex()
    private val path = "/_synapse/client/agentim/v2/registration"

    override suspend fun policy(homeserver: String) = runCatchingExceptions { OnboardingJson.policy(http.request(homeserver, path)) }

    override suspend fun pending() = withContext(Dispatchers.IO) { runCatchingExceptions { attempts.pending()?.metadata() } }

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        // Only published sessions restore in the background. An unpublished journal resumes
        // through the login screen, so it cannot race another native authentication flow.
        scope.launch(Dispatchers.IO) { completion.observe() }
    }

    override suspend fun register(homeserver: String, localpart: String, displayName: String) = withContext(Dispatchers.IO) {
        runCatchingExceptions {
            mutex.withLock {
                val base = http.base(homeserver)
                require(localpart.matches(Regex("[a-z][a-z0-9._=-]{2,63}"))) { "Use a valid Agent ID" }
                require(displayName.length <= 128 && displayName.none(Char::isISOControl))
                val previous = attempts.pending()
                if (previous != null) {
                    require(previous.homeserver == base && previous.localpart == localpart) { "Resume the original registration first" }
                    resume(previous)?.let { return@withLock it }
                } else {
                    require(policy(base).getOrThrow() == AgentimRegistrationPolicy.Open) { "Registration is closed" }
                }
                // This proof must exist durably before the server can reserve any identity.
                val attempt = attempts.begin(base, localpart, displayName)
                authentication.cancelOAuthLogin().getOrThrow()
                val server = authentication.setHomeserver(base).getOrThrow()
                require(http.base(server.url) == base) { "Enter the actual homeserver URL directly" }
                val body = JSONObject().put("attempt_id", attempt.attemptId).put("device_id", attempt.deviceId)
                    .put("code_challenge", attempt.challenge()).put("localpart", attempt.localpart)
                if (attempt.displayName.isNotBlank()) body.put("display_name", attempt.displayName)
                val status = allocate(attempt, path, body)
                val expiry = OnboardingJson.status(status, attempt)
                check(status.getString("state") != "completed") { "Restore this identity's original device" }
                if (status.has("user_id")) attempts.bind(attempt, status.getString("user_id"))
                val bound = requireNotNull(attempts.pending())
                val grant = OnboardingJson.grant(
                    allocate(bound, "$path/${attempt.attemptId}/consume", JSONObject().put("verifier", attempt.verifier)),
                    bound,
                    expiry,
                )
                attempts.bind(bound, grant.userId)
                authentication.loginWithAgentBootstrap(grant.token, grant.userId, attempt.deviceId).getOrThrow().value
            }
        }
    }

    private suspend fun resume(attempt: OnboardingAttempt): String? = attempt.userId?.let {
        authentication.resumeAgentBootstrap(attempt.homeserver, it, attempt.deviceId).getOrThrow()?.value
    }

    private suspend fun allocate(attempt: OnboardingAttempt, endpoint: String, body: JSONObject): JSONObject = try {
        http.request(attempt.homeserver, endpoint, body)
    } catch (error: AgentimRegistrationRejected) {
        // A definitive name rejection created no identity. Unknown outcomes retain the proof.
        if (error.code == "M_USER_IN_USE" && attempt.userId == null) attempts.finish(attempt)
        throw error
    }
}
