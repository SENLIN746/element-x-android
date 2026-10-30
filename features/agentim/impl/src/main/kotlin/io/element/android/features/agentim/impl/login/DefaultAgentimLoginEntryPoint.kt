/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.login

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.agentim.api.AgentimLoginEntryPoint
import io.element.android.features.agentim.impl.account.AccountAccessPresenter
import io.element.android.features.agentim.impl.account.AccountAccessView
import io.element.android.features.agentim.impl.pairing.AgentimPairingPresenter
import io.element.android.features.agentim.impl.pairing.AgentimPairingView

@ContributesBinding(AppScope::class)
class DefaultAgentimLoginEntryPoint(
    private val presenter: AgentimLoginPresenter,
    private val pairing: AgentimPairingPresenter,
    private val accounts: AccountAccessPresenter,
) : AgentimLoginEntryPoint {
    @Composable
    override fun Render(onBack: () -> Unit, modifier: Modifier) {
        var creating by remember { mutableStateOf(false) }
        var restoring by remember { mutableStateOf(false) }
        BackHandler(enabled = creating || restoring) {
            creating = false
            restoring = false
        }
        if (restoring) {
            AccountAccessView(accounts.present(), { restoring = false }, modifier)
        } else if (creating) {
            AgentimLoginView(presenter.present(), { creating = false }, modifier, onExisting = { creating = false })
        } else {
            AgentimPairingView(pairing.present(), onBack, { creating = true }, modifier, onRestore = { restoring = true })
        }
    }
}
