/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.updates

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.agentim.api.updates.AgentAppUpdates
import io.element.android.libraries.agentim.api.updates.AppUpdatePhase
import io.element.android.libraries.agentim.api.updates.AppUpdateStatus
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppUpdateAction { Check, Download, Install, Cancel, Refresh, Pause }

data class AppUpdatesState(
    val status: AppUpdateStatus = AppUpdateStatus(AppUpdatePhase.Current),
    val busy: Boolean = false,
    val failed: Boolean = false,
    val action: (AppUpdateAction) -> Unit = {},
)

@Inject
class AppUpdatesPresenter(private val updates: AgentAppUpdates, @ApplicationContext private val context: Context) : Presenter<AppUpdatesState> {
    @Composable
    override fun present(): AppUpdatesState {
        var state by remember { mutableStateOf(AppUpdatesState()) }
        val scope = rememberCoroutineScope()
        var foreground by remember { mutableStateOf(true) }
        fun run(action: AppUpdateAction) {
            if (action == AppUpdateAction.Pause) {
                foreground = false
                return
            }
            if (action == AppUpdateAction.Refresh) foreground = true
            if (state.busy) return
            state = state.copy(busy = true, failed = false)
            scope.launch {
                try {
                    val status = when (action) {
                        AppUpdateAction.Check -> updates.check().getOrThrow()
                        AppUpdateAction.Download -> updates.download().getOrThrow()
                        AppUpdateAction.Cancel -> {
                            updates.cancel().getOrThrow()
                            updates.refresh().getOrThrow()
                        }
                        AppUpdateAction.Install -> {
                            context.startActivity(updates.installIntent().getOrThrow())
                            state.status
                        }
                        AppUpdateAction.Refresh -> updates.refresh().getOrThrow()
                        AppUpdateAction.Pause -> state.status
                    }
                    state = state.copy(status = status)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    state = state.copy(failed = true)
                } finally {
                    state = state.copy(busy = false)
                }
            }
        }
        LaunchedEffect(Unit) { run(AppUpdateAction.Refresh) }
        LaunchedEffect(state.status.phase, state.failed, foreground) {
            if (state.status.phase == AppUpdatePhase.Downloading && !state.failed && foreground) {
                while (true) {
                    delay(2_000)
                    run(AppUpdateAction.Refresh)
                }
            }
        }
        return state.copy(action = ::run)
    }
}
