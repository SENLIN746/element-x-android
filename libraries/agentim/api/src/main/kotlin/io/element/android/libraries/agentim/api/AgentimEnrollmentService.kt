/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api

interface AgentimEnrollmentService {
    /** Admission credential is sent to enrollment only, never persisted or used as a Matrix token. */
    suspend fun register(homeserver: String, admission: String, localpart: String, displayName: String): Result<String>
}
