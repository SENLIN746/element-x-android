/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.api

import androidx.compose.runtime.Composable

/** Update controls embedded in the existing About screen. */
interface AgentimUpdatesEntryPoint {
    @Composable
    fun Render()
}
