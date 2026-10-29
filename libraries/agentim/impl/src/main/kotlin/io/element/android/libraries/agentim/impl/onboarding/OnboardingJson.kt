/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.onboarding

import io.element.android.libraries.agentim.api.onboarding.AgentimRegistrationPolicy
import org.json.JSONObject

/** Narrow canonical v2 decoding; secret grants never become diagnostic data classes. */
internal object OnboardingJson {
    fun policy(json: JSONObject): AgentimRegistrationPolicy {
        require(json.keys().asSequence().toSet() == setOf("mode", "protocol", "attempt_lifetime_ms"))
        require(json.get("protocol") == 2 && integer(json, "attempt_lifetime_ms") == 86_400_000L)
        return when (json.getString("mode")) {
            "open" -> AgentimRegistrationPolicy.Open
            "closed" -> AgentimRegistrationPolicy.Closed
            else -> error("Unsupported registration policy")
        }
    }

    fun status(json: JSONObject, attempt: OnboardingAttempt): Long {
        val required = setOf("attempt_id", "device_id", "expires_at_ms", "state")
        require(json.keys().asSequence().toSet().let { required.all(it::contains) && (it - required - "user_id").isEmpty() })
        require(json.getString("attempt_id") == attempt.attemptId && json.getString("device_id") == attempt.deviceId)
        require(json.getString("state") in setOf("reserved", "provisioned", "completed"))
        if (json.has("user_id")) user(json.getString("user_id"), attempt)
        return integer(json, "expires_at_ms").also { require(it > 0) }
    }

    fun grant(json: JSONObject, attempt: OnboardingAttempt, expiresAt: Long): Grant {
        require(json.keys().asSequence().toSet() == setOf("attempt_id", "device_id", "expires_at_ms", "state", "user_id", "login_token", "expires_in_ms"))
        require(json.getString("attempt_id") == attempt.attemptId && json.getString("device_id") == attempt.deviceId)
        require(json.getString("state") == "provisioned" && integer(json, "expires_at_ms") == expiresAt)
        require(integer(json, "expires_in_ms") in 1..60_000)
        val userId = json.getString("user_id").also { user(it, attempt) }
        val token = json.getString("login_token").also { require(it.isNotBlank() && it.length <= 4096) }
        return Grant(userId, token)
    }

    private fun integer(json: JSONObject, name: String): Long {
        val value = json.get(name)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }

    private fun user(value: String, attempt: OnboardingAttempt) {
        require(value.length <= 255 && value.startsWith("@${attempt.localpart}:") && value.none(Char::isWhitespace))
        require(value.substringAfter(':').isNotBlank() && (attempt.userId == null || value == attempt.userId))
    }

    class Grant(val userId: String, val token: String) {
        override fun toString(): String = "OnboardingGrant(<private>)"
    }
}
