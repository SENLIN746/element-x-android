/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.contacts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.element.android.features.agentim.impl.AgentimEvent
import io.element.android.features.agentim.impl.AgentimState
import io.element.android.features.agentim.impl.R
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton

@Composable
internal fun AgentimContactsView(state: AgentimState) {
    if (state.contacts.isEmpty() && !state.busy) Text(stringResource(R.string.screen_agentim_empty))
    state.contacts.forEach { contact ->
        HorizontalDivider()
        Text(contact.userId)
        Text(stringResource(
            when (contact.state) {
            "incoming" -> R.string.screen_agentim_incoming
            "outgoing" -> R.string.screen_agentim_outgoing
            "accepted" -> R.string.screen_agentim_accepted
            "rejected" -> R.string.screen_agentim_rejected
            else -> R.string.screen_agentim_none
        }
        ))
        if (contact.blocked) Text(stringResource(R.string.screen_agentim_blocked))
        val actions = if (contact.blocked) {
            listOf("unblock" to R.string.action_agentim_unblock)
        } else {
            buildList {
            if (contact.state == "incoming") {
                add("accept" to R.string.action_agentim_accept)
                add("reject" to R.string.action_agentim_reject)
            }
            if (contact.state == "accepted" || contact.state == "outgoing") add("remove" to R.string.action_agentim_remove)
            add("block" to R.string.action_agentim_block)
        }
        }
        actions.forEach { (action, title) ->
            TextButton(stringResource(title), onClick = { state.eventSink(AgentimEvent.Contact(contact.userId, action)) }, enabled = !state.busy)
        }
        if (contact.state == "accepted" && !contact.blocked) {
            TextButton(stringResource(R.string.action_agentim_chat), onClick = { state.eventSink(AgentimEvent.Chat(contact.userId)) }, enabled = !state.busy)
        }
    }
}
