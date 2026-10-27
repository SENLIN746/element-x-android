/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.agentim.impl.navigation.AgentimRoomNavigator
import io.element.android.libraries.agentim.api.AgentContactService
import io.element.android.libraries.agentim.api.AgentDiscoveryService
import io.element.android.libraries.agentim.api.AgentProfileService
import io.element.android.libraries.agentim.api.AgentimIgnoreProjectionFailure
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Inject
class AgentimPresenter(
    private val profiles: AgentProfileService,
    private val discovery: AgentDiscoveryService,
    private val contacts: AgentContactService,
    private val navigator: AgentimRoomNavigator,
) : Presenter<AgentimState> {
    @Composable
    override fun present(): AgentimState {
        var state by remember { mutableStateOf(AgentimState()) }
        val scope = rememberCoroutineScope()
        suspend fun load(more: Boolean = false) {
            when (state.page) {
                0 -> {
                    val profile = profiles.profile().getOrThrow()
                    state = state.copy(
                        profile = profile,
                        description = profile.description,
                        capabilities = profile.capabilities.joinToString(", "),
                        discoverable = profile.discoverable,
                        closed = profile.contactPolicy == "closed"
                    )
                }
                1 -> {
                    val page = discovery.discover(state.query.trim(), state.capability.trim(), if (more) state.discoveryCursor else null).getOrThrow()
                    state = state.copy(profiles = (if (more) state.profiles else emptyList()) + page.items, discoveryCursor = page.nextCursor)
                }
                2 -> {
                    val page = contacts.contacts(if (more) state.contactsCursor else null).getOrThrow()
                    state = state.copy(contacts = (if (more) state.contacts else emptyList()) + page.items, contactsCursor = page.nextCursor)
                }
            }
        }
        fun run(action: suspend () -> Unit) {
            if (state.busy) return
            state = state.copy(busy = true, failed = false, partialIgnoreFailure = false)
            scope.launch {
                try {
                    action()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    state = state.copy(failed = true, partialIgnoreFailure = failure is AgentimIgnoreProjectionFailure)
                } finally {
                    state = state.copy(busy = false)
                }
            }
        }
        LaunchedEffect(Unit) { run { load() } }
        return state.copy(eventSink = { event ->
            if (!state.busy) {
                when (event) {
                is AgentimEvent.Page -> {
                    state = state.copy(page = event.value)
                    run { load() }
                }
                is AgentimEvent.Description -> state = state.copy(description = event.value.take(2000))
                is AgentimEvent.Capabilities -> state = state.copy(capabilities = event.value)
                is AgentimEvent.Discoverable -> state = state.copy(discoverable = event.value)
                is AgentimEvent.Closed -> state = state.copy(closed = event.value)
                is AgentimEvent.Query -> state = state.copy(query = event.value, profiles = emptyList(), discoveryCursor = null)
                is AgentimEvent.Capability -> state = state.copy(capability = event.value, profiles = emptyList(), discoveryCursor = null)
                is AgentimEvent.Contact -> run {
                    contacts.contactAction(event.userId, event.action).getOrThrow()
                    load()
                }
                is AgentimEvent.Chat -> run { navigator.open(event.userId) }
                AgentimEvent.SaveProfile -> run {
                    val profile = requireNotNull(state.profile).copy(
                        description = state.description,
                        capabilities = state.capabilities.split(',').map(String::trim).filter(String::isNotEmpty).distinct(),
                        discoverable = state.discoverable,
                        contactPolicy = if (state.closed) "closed" else "request"
                    )
                    profiles.updateProfile(profile).getOrThrow()
                    load()
                }
                AgentimEvent.Refresh -> run { load() }
                AgentimEvent.More -> run { load(more = true) }
            }
            }
        })
    }
}
