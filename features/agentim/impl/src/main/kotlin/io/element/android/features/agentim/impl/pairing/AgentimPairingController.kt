/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.pairing

import io.element.android.features.agentim.impl.login.AgentimDeviceLogin
import io.element.android.libraries.agentim.api.pairing.AgentimPairingPhase
import io.element.android.libraries.agentim.api.pairing.AgentimPairingService
import io.element.android.libraries.agentim.api.pairing.AgentimPairingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One screen owns one proof session. Public state never contains proof or login grants. */
internal class AgentimPairingController(
    private val service: AgentimPairingService,
    private val login: AgentimDeviceLogin,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutableState = MutableStateFlow(AgentimPairingState())
    val state = mutableState.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private var job: Job? = null
    private var generation = 0
    private var foregroundGeneration = 0

    fun active(value: Boolean) {
        if (value && !foreground.value) foregroundGeneration++
        foreground.value = value
    }

    fun toggleDetails() {
        mutableState.value = state.value.copy(detailsVisible = !state.value.detailsVisible)
    }

    fun edit(event: AgentimPairingEvent) {
        if (state.value.busy) return
        mutableState.value = when (event) {
            is AgentimPairingEvent.Homeserver -> state.value.copy(homeserver = event.value, phase = PairingScreenPhase.Idle)
            is AgentimPairingEvent.Target -> state.value.copy(targetUserId = event.value, phase = PairingScreenPhase.Idle)
            is AgentimPairingEvent.DeviceName -> state.value.copy(deviceName = event.value, phase = PairingScreenPhase.Idle)
            else -> state.value
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        mutableState.value = state.value.copy(phase = PairingScreenPhase.Idle, request = null, seconds = 0, detailsVisible = false)
    }

    fun submit() {
        if (state.value.busy || job?.isActive == true) return
        val form = state.value
        val epoch = ++generation
        mutableState.value = form.copy(phase = PairingScreenPhase.Creating, request = null, detailsVisible = false)
        job = scope.launch {
            var session: AgentimPairingSession? = null
            var connected = false
            try {
                foreground.first { it }
                var observedForeground = foregroundGeneration
                val homeserver = login.prepare(form.homeserver.trim()).getOrThrow()
                session = service.start(homeserver, form.targetUserId.trim(), form.deviceName.trim()).getOrThrow()
                var request = session.request
                var deadline = now() + (request.expiresAtMs - request.serverTimeMs).coerceIn(0, 300_000)
                mutableState.value = state.value.copy(phase = PairingScreenPhase.Waiting, request = request)
                while (epoch == generation) {
                    foreground.first { it }
                    if (observedForeground != foregroundGeneration) {
                        request = session.status().getOrThrow()
                        deadline = minOf(deadline, now() + (request.expiresAtMs - request.serverTimeMs).coerceIn(0, 300_000))
                        observedForeground = foregroundGeneration
                    }
                    val seconds = ((deadline - now()).coerceAtLeast(0) + 999) / 1_000
                    mutableState.value = state.value.copy(request = request, seconds = seconds)
                    if (seconds == 0L || request.state == AgentimPairingPhase.Expired) {
                        mutableState.value = state.value.copy(phase = PairingScreenPhase.Expired)
                        return@launch
                    }
                    when (request.state) {
                        AgentimPairingPhase.Pending -> Unit
                        AgentimPairingPhase.Approved -> {
                            // Query again after background resume; never silently reuse a stale approval.
                            if (!foreground.value) continue
                            mutableState.value = state.value.copy(phase = PairingScreenPhase.Connecting)
                            val grant = session.consume().getOrThrow()
                            login.connect(grant).getOrThrow()
                            connected = true
                            mutableState.value = state.value.copy(phase = PairingScreenPhase.Connected)
                            return@launch
                        }
                        AgentimPairingPhase.Rejected -> {
                            mutableState.value = state.value.copy(phase = PairingScreenPhase.Rejected)
                            return@launch
                        }
                        else -> {
                            mutableState.value = state.value.copy(phase = PairingScreenPhase.Failed)
                            return@launch
                        }
                    }
                    delay(request.pollIntervalMs)
                    foreground.first { it }
                    request = session.status().getOrThrow()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (epoch == generation) mutableState.value = state.value.copy(phase = PairingScreenPhase.Failed)
            } finally {
                if (!connected) withContext(NonCancellable) { withTimeoutOrNull(3_000) { session?.cancel() } }
            }
        }
    }
}
