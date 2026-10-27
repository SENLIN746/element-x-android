/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.features.agentim.impl.AgentimEvent
import io.element.android.features.agentim.impl.AgentimState
import io.element.android.features.agentim.impl.R
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Switch
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField

@Composable
internal fun AgentimProfileView(state: AgentimState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.profile?.let { profile ->
            Text(profile.userId)
            TextField(
                state.description,
                { state.eventSink(AgentimEvent.Description(it)) },
                label = stringResource(R.string.screen_agentim_description),
                enabled = !state.busy
            )
            TextField(
                state.capabilities,
                { state.eventSink(AgentimEvent.Capabilities(it)) },
                label = stringResource(R.string.screen_agentim_capabilities),
                enabled = !state.busy
            )
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Switch(checked = state.discoverable, onCheckedChange = { state.eventSink(AgentimEvent.Discoverable(it)) }, enabled = !state.busy)
                Text(stringResource(R.string.screen_agentim_discoverable))
            }
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Switch(checked = state.closed, onCheckedChange = { state.eventSink(AgentimEvent.Closed(it)) }, enabled = !state.busy)
                Text(stringResource(R.string.screen_agentim_closed))
            }
            Button(stringResource(R.string.action_agentim_save), onClick = { state.eventSink(AgentimEvent.SaveProfile) }, enabled = !state.busy)
        }
    }
}
