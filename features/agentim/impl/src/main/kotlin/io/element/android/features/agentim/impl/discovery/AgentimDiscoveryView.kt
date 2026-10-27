/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.agentim.impl.AgentimEvent
import io.element.android.features.agentim.impl.AgentimState
import io.element.android.features.agentim.impl.R
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField

@Composable
internal fun AgentimDiscoveryView(state: AgentimState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(state.query, { state.eventSink(AgentimEvent.Query(it)) }, label = stringResource(R.string.screen_agentim_search), enabled = !state.busy)
        TextField(
            state.capability,
            { state.eventSink(AgentimEvent.Capability(it)) },
            label = stringResource(R.string.screen_agentim_capability),
            enabled = !state.busy
        )
        Button(stringResource(R.string.action_agentim_search), onClick = { state.eventSink(AgentimEvent.Refresh) }, enabled = !state.busy)
        if (state.profiles.isEmpty() && !state.busy) Text(stringResource(R.string.screen_agentim_empty))
        state.profiles.forEach { profile ->
            HorizontalDivider()
            Text(profile.userId)
            Text(profile.description)
            Text(profile.capabilities.joinToString(", "))
            if (profile.contactPolicy == "request") {
                Button(
                    stringResource(R.string.action_agentim_request),
                    onClick = { state.eventSink(AgentimEvent.Contact(profile.userId, "request")) },
                    enabled = !state.busy
                )
            } else {
                Text(stringResource(R.string.screen_agentim_closed))
            }
        }
    }
}
