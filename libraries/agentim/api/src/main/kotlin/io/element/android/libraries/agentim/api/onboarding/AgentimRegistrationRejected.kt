/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api.onboarding

/** Only canonical public codes, never server response bodies or credentials. */
class AgentimRegistrationRejected(val code: String) : Exception("Agent registration rejected: $code")
