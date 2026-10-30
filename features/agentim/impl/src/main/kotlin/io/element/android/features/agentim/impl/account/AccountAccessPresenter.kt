/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.account

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.agentim.api.account.AgentAccountAccessService
import kotlinx.coroutines.launch

sealed interface AccountAccessEvent {
    data class Export(val uri: Uri) : AccountAccessEvent
    data class Import(val uri: Uri) : AccountAccessEvent
    data object Resume : AccountAccessEvent
    data object Revoke : AccountAccessEvent
}

data class AccountAccessState(val signedIn: Boolean = false, val busy: Boolean = false, val failed: Boolean = false,
    val verified: Boolean = false, val pending: Boolean = false, val revoked: Boolean = false,
    val eventSink: (AccountAccessEvent) -> Unit = {})

@Inject
class AccountAccessPresenter(private val access: AgentAccountAccessService) {
    @Composable
    fun present(userId: String? = null): AccountAccessState {
        var state by remember(userId) { mutableStateOf(AccountAccessState(signedIn = userId != null)) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(userId) {
            access.start()
            if (userId != null) {
                val result = access.ensure(userId)
                state = state.copy(failed = result.isFailure, verified = result.getOrNull()?.exportedAndVerified == true)
            } else {
                state = state.copy(pending = access.pending().getOrDefault(false))
            }
        }
        return state.copy(eventSink = { event ->
            if (!state.busy) {
                state = state.copy(busy = true, failed = false)
                scope.launch {
                    val result = when (event) {
                        is AccountAccessEvent.Export -> access.export(requireNotNull(userId), event.uri).map { it.exportedAndVerified }
                        is AccountAccessEvent.Import -> if (userId == null) {
                            access.restore(event.uri).map { true }
                        } else {
                            access.verify(userId, event.uri).map { it.exportedAndVerified }
                        }
                        AccountAccessEvent.Resume -> access.resume().map { true }
                        AccountAccessEvent.Revoke -> access.revoke(requireNotNull(userId)).map { false }
                    }
                    state = state.copy(
                        busy = false,
                        failed = result.isFailure,
                        verified = result.getOrDefault(state.verified),
                        pending = access.pending().getOrDefault(false),
                        revoked = event == AccountAccessEvent.Revoke && result.isSuccess
                    )
                }
            }
        })
    }
}
