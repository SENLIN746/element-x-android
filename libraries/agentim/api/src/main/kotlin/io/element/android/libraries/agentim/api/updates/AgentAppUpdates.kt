/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.api.updates

import android.content.Intent

data class AppUpdate(val version: String, val code: Long, val url: String, val sha256: String, val bytes: Long)

enum class AppUpdatePhase { Current, Available, Downloading, Ready, InstallPermission, Store }

data class AppUpdateStatus(val phase: AppUpdatePhase, val update: AppUpdate? = null, val percent: Int = 0)

/** Direct downloads are handed to Android only after package, signer, version and digest checks. */
interface AgentAppUpdates {
    suspend fun check(): Result<AppUpdateStatus>
    suspend fun refresh(): Result<AppUpdateStatus>
    suspend fun download(): Result<AppUpdateStatus>
    suspend fun installIntent(): Result<Intent>
    suspend fun cancel(): Result<Unit>
}
