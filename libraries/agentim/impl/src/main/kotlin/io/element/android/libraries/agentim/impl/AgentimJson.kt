/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import io.element.android.libraries.agentim.api.AgentContact
import io.element.android.libraries.agentim.api.AgentProfile
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.profile() = AgentProfile(
    userId = getString("user_id"),
    description = getString("description"),
    capabilities = getJSONArray("capabilities").let { values -> List(values.length()) { values.getString(it) } },
    discoverable = getBoolean("discoverable"),
    contactPolicy = getString("contact_policy"),
)

internal fun AgentProfile.toUpdate(): JSONObject {
    require(description.length <= 2000)
    require(capabilities.size <= 32 && capabilities.distinct().size == capabilities.size && capabilities.all { it.length in 1..64 })
    require(contactPolicy in setOf("request", "closed"))
    return JSONObject().put("description", description).put("capabilities", JSONArray(capabilities))
        .put("discoverable", discoverable).put("contact_policy", contactPolicy)
}

internal fun JSONObject.contact() = AgentContact(getString("user_id"), getString("state"), getBoolean("blocked"))
internal fun JSONObject.cursor(): String? = if (isNull("next_cursor")) null else getString("next_cursor")
