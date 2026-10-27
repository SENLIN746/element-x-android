/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.agentim.api.AgentimEntryPoint
import io.element.android.libraries.di.SessionScope

@ContributesBinding(SessionScope::class)
class DefaultAgentimEntryPoint(private val presenter: AgentimPresenter) : AgentimEntryPoint {
    @Composable
    override fun Render(onClose: () -> Unit, modifier: Modifier) {
        BackHandler(onBack = onClose)
        AgentimView(presenter.present(), onClose, modifier)
    }
}
