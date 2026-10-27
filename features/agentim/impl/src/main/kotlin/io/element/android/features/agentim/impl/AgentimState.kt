/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl

import io.element.android.libraries.agentim.api.AgentContact
import io.element.android.libraries.agentim.api.AgentProfile

data class AgentimState(
    val page: Int = 0,
    val profile: AgentProfile? = null,
    val description: String = "",
    val capabilities: String = "",
    val discoverable: Boolean = false,
    val closed: Boolean = false,
    val query: String = "",
    val capability: String = "",
    val profiles: List<AgentProfile> = emptyList(),
    val contacts: List<AgentContact> = emptyList(),
    val discoveryCursor: String? = null,
    val contactsCursor: String? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val partialIgnoreFailure: Boolean = false,
    val eventSink: (AgentimEvent) -> Unit = {},
)

sealed interface AgentimEvent {
    data class Page(val value: Int) : AgentimEvent
    data class Description(val value: String) : AgentimEvent
    data class Capabilities(val value: String) : AgentimEvent
    data class Discoverable(val value: Boolean) : AgentimEvent
    data class Closed(val value: Boolean) : AgentimEvent
    data class Query(val value: String) : AgentimEvent
    data class Capability(val value: String) : AgentimEvent
    data class Contact(val userId: String, val action: String) : AgentimEvent
    data class Chat(val userId: String) : AgentimEvent
    data object SaveProfile : AgentimEvent
    data object Refresh : AgentimEvent
    data object More : AgentimEvent
}
