/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.agentim.impl.contacts.AgentimContactsView
import io.element.android.features.agentim.impl.discovery.AgentimDiscoveryView
import io.element.android.features.agentim.impl.profile.AgentimProfileView
import io.element.android.libraries.agentim.api.AgentContact
import io.element.android.libraries.agentim.api.AgentProfile
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton

@Composable
fun AgentimView(state: AgentimState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(stringResource(R.string.action_agentim_back), onClick = onClose)
        Text(stringResource(R.string.screen_agentim_title))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(R.string.screen_agentim_profile, R.string.screen_agentim_discover, R.string.screen_agentim_contacts).forEachIndexed { index, title ->
                TextButton(stringResource(title), onClick = { state.eventSink(AgentimEvent.Page(index)) }, enabled = !state.busy)
            }
        }
        if (state.busy) Text(stringResource(R.string.screen_agentim_loading))
        if (state.failed) Text(stringResource(if (state.partialIgnoreFailure) R.string.error_agentim_ignore_partial else R.string.error_agentim_request))
        when (state.page) {
            0 -> AgentimProfileView(state)
            1 -> AgentimDiscoveryView(state)
            2 -> AgentimContactsView(state)
        }
        TextButton(stringResource(R.string.action_agentim_refresh), onClick = { state.eventSink(AgentimEvent.Refresh) }, enabled = !state.busy)
        if ((state.page == 1 && state.discoveryCursor != null) || (state.page == 2 && state.contactsCursor != null)) {
            TextButton(stringResource(R.string.action_agentim_more), onClick = { state.eventSink(AgentimEvent.More) }, enabled = !state.busy)
        }
    }
}

@PreviewsDayNight
@Composable
internal fun AgentimProfilePreview() = ElementPreview {
    AgentimView(AgentimState(profile = AgentProfile("@agent:example.org")), {})
}

@PreviewsDayNight
@Composable
internal fun AgentimDiscoveryPreview() = ElementPreview {
    AgentimView(AgentimState(page = 1, profiles = listOf(AgentProfile("@research:example.org", capabilities = listOf("research")))), {})
}

@PreviewsDayNight
@Composable
internal fun AgentimContactsPreview() = ElementPreview {
    AgentimView(AgentimState(page = 2, contacts = listOf(AgentContact("@translator:example.org", "incoming", false))), {})
}

@PreviewsDayNight
@Composable
internal fun AgentimLoadingPreview() = ElementPreview { AgentimView(AgentimState(busy = true), {}) }

@PreviewsDayNight
@Composable
internal fun AgentimErrorPreview() = ElementPreview { AgentimView(AgentimState(failed = true, partialIgnoreFailure = true), {}) }
