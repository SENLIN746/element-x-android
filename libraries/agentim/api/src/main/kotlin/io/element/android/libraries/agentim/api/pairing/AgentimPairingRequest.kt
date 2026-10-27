/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api.pairing

/** Public metadata only: device labels and origins are applicant claims, not authority. */
data class AgentimPairingRequest(
    val pairingId: String,
    val targetUserId: String,
    val userCode: String,
    val codeChallenge: String,
    val deviceName: String,
    val clientOrigin: String,
    val state: AgentimPairingPhase,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val serverTimeMs: Long,
    val pollIntervalMs: Long,
) {
    fun fingerprint(): String = """{"pairing_id":"$pairingId","user_code":"$userCode","code_challenge":"$codeChallenge"}"""
}

enum class AgentimPairingPhase {
    Pending,
    Approved,
    Rejected,
    Cancelled,
    Consuming,
    Consumed,
    Expired,
    Failed
}
