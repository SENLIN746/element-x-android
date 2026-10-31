/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.updates

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.agentim.api.AgentimUpdatesEntryPoint
import io.element.android.libraries.di.SessionScope

@ContributesBinding(SessionScope::class)
class DefaultAgentimUpdatesEntryPoint(private val presenter: AppUpdatesPresenter) : AgentimUpdatesEntryPoint {
    @Composable
    override fun Render() {
        AppUpdatesView(presenter.present())
    }
}
