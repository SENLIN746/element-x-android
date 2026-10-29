/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.x.agentim

import android.app.Activity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.features.enterprise.api.AppStartupHook
import io.element.android.libraries.agentim.api.onboarding.AgentimOnboardingService

/** Only assembles the app-owned registration recovery lifecycle. */
@ContributesIntoSet(AppScope::class)
class AgentimStartupHook(private val onboarding: AgentimOnboardingService) : AppStartupHook {
    override suspend fun onAppStartup(activity: Activity) = onboarding.start()
}
