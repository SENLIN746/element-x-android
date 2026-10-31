/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.features.preferences.impl.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.push.api.PushService
import io.element.android.libraries.push.api.PusherRegistrationFailure
import io.element.android.libraries.pushproviders.api.Distributor
import io.element.android.libraries.pushproviders.api.PushProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

enum class PushRegistrationState { Checking, Disabled, NeedsVerification, NeedsDistributor, Registered, Failed }

internal data class NotificationPushSettings(
    val current: AsyncData<Distributor>,
    val available: ImmutableList<Distributor>,
    val registration: PushRegistrationState,
    val showPicker: Boolean,
    val openPicker: () -> Unit,
    val closePicker: () -> Unit,
    val select: (Int) -> Unit,
    val retry: () -> Unit,
)

internal fun pushRegistrationFailure(error: Throwable): PushRegistrationState = when (error) {
    is PusherRegistrationFailure.AccountNotVerified -> PushRegistrationState.NeedsVerification
    is PusherRegistrationFailure.NoDistributorsAvailable,
    is PusherRegistrationFailure.NoProvidersAvailable -> PushRegistrationState.NeedsDistributor
    else -> PushRegistrationState.Failed
}

/** Refresh native provider discovery on resume; never bypass native device verification. */
@Composable
internal fun rememberNotificationPushSettings(
    client: MatrixClient,
    service: PushService,
    dispatchers: CoroutineDispatchers,
    enabled: Boolean,
    refreshKey: Int,
): NotificationPushSettings {
    val verified by client.sessionVerificationService.sessionVerifiedStatus.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    var choice by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<AsyncData<Distributor>>(AsyncData.Uninitialized) }
    var registration by remember { mutableStateOf(PushRegistrationState.Checking) }
    var discoveryFailed by remember { mutableStateOf(false) }
    val providers by produceState<List<Pair<PushProvider, Distributor>>?>(null, refreshKey, attempt) {
        val discovery = runCatchingExceptions {
            withContext(dispatchers.io) {
                service.getAvailablePushProviders().flatMap { provider -> provider.getDistributors().map { provider to it } }
            }
        }
        discoveryFailed = discovery.isFailure
        value = discovery.getOrDefault(emptyList())
    }
    val available = providers.orEmpty().map { it.second }.toImmutableList()
    LaunchedEffect(refreshKey, attempt, verified, enabled, providers, discoveryFailed) {
        registration = when {
            !enabled -> PushRegistrationState.Disabled
            verified == SessionVerifiedStatus.NotVerified -> PushRegistrationState.NeedsVerification
            verified == SessionVerifiedStatus.Unknown || providers == null -> PushRegistrationState.Checking
            discoveryFailed -> PushRegistrationState.Failed
            providers.isNullOrEmpty() -> PushRegistrationState.NeedsDistributor
            else -> {
                current = AsyncData.Loading(current.dataOrNull())
                val selected = providers?.firstOrNull { it.second.value == choice }
                val result = runCatchingExceptions {
                    withContext(dispatchers.io) {
                        if (selected != null) {
                            service.registerWith(client, selected.first, selected.second).getOrThrow()
                        } else {
                            service.ensurePusherIsRegistered(client).getOrThrow()
                        }
                        service.getCurrentPushProvider(client.sessionId)?.getCurrentDistributor(client.sessionId)
                            ?: error("Push distributor unavailable")
                    }
                }
                current = result.fold(onSuccess = { AsyncData.Success(it) }, onFailure = { AsyncData.Failure(it) })
                result.fold(onSuccess = { PushRegistrationState.Registered }, onFailure = ::pushRegistrationFailure)
            }
        }
        if (registration == PushRegistrationState.NeedsDistributor || registration == PushRegistrationState.NeedsVerification) {
            current = AsyncData.Failure(IllegalStateException("Push setup incomplete"))
        }
    }
    return NotificationPushSettings(
        current,
        available,
        registration,
        showPicker,
        openPicker = { showPicker = true },
        closePicker = { showPicker = false },
        select = { index ->
            showPicker = false
            choice = providers?.getOrNull(index)?.second?.value
            attempt++
        },
        retry = { attempt++ },
    )
}
