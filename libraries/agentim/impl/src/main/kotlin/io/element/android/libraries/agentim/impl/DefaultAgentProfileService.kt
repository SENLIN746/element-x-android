/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.agentim.api.AgentProfile
import io.element.android.libraries.agentim.api.AgentProfileService
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope

@ContributesBinding(SessionScope::class)
class DefaultAgentProfileService(private val http: AgentimSessionHttp) : AgentProfileService {
    override suspend fun profile(): Result<AgentProfile> = runCatchingExceptions { http.request(listOf("profiles", "me")).profile() }

    override suspend fun updateProfile(profile: AgentProfile): Result<AgentProfile> = runCatchingExceptions {
        http.request(listOf("profiles", "me"), "PUT", profile.toUpdate()).profile()
    }
}
