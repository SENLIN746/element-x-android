/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Native Compound screens mounted in the existing login and preferences surfaces. */
interface AgentimLoginEntryPoint {
    @Composable
    fun Render(onBack: () -> Unit, modifier: Modifier = Modifier)
}

interface AgentimEntryPoint {
    @Composable
    fun Render(onClose: () -> Unit, modifier: Modifier = Modifier)
}
