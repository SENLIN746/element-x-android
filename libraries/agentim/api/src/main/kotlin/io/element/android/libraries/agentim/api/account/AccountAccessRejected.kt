/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.libraries.agentim.api.account

class AccountAccessRejected(val code: String, val status: Int, val requestId: String?) :
    Exception("Account access failed (HTTP $status, $code, request ${requestId ?: "unavailable"})")
