/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.agentim.api.AgentimLoginEntryPoint

@ContributesBinding(AppScope::class)
class DefaultAgentimLoginEntryPoint(private val presenter: AgentimLoginPresenter) : AgentimLoginEntryPoint {
    @Composable
    override fun Render(onBack: () -> Unit, modifier: Modifier) {
        AgentimLoginView(presenter.present(), onBack, modifier)
    }
}
