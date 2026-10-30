/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.agentim.api.AgentimEntryPoint
import io.element.android.features.agentim.impl.account.AccountAccessPresenter
import io.element.android.features.agentim.impl.account.AccountAccessView
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient

@ContributesBinding(SessionScope::class)
class DefaultAgentimEntryPoint(
    private val presenter: AgentimPresenter, private val accounts: AccountAccessPresenter, private val client: MatrixClient,
) : AgentimEntryPoint {
    @Composable
    override fun Render(onClose: () -> Unit, modifier: Modifier) {
        var backup by remember { mutableStateOf(false) }
        BackHandler(onBack = { if (backup) backup = false else onClose() })
        if (backup) {
            AccountAccessView(accounts.present(client.sessionId.value), { backup = false }, modifier)
        } else {
            AgentimView(presenter.present(), onClose, modifier, onBackup = { backup = true })
        }
    }
}
