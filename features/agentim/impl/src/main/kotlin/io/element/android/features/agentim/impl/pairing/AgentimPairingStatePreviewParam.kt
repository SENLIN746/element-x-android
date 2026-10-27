/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.agentim.impl.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.agentim.api.pairing.AgentimPairingPhase
import io.element.android.libraries.agentim.api.pairing.AgentimPairingRequest
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

class AgentimPairingStatePreviewParam : PreviewParameterProvider<AgentimPairingState> {
    override val values: Sequence<AgentimPairingState> = PairingScreenPhase.entries.asSequence().map { phase ->
        val request = AgentimPairingRequest(
            "p".repeat(43),
            "@research:agentim.test",
            "ABCD-EFGH",
            "c".repeat(43),
            "AgentIM Android",
            "android-app://im.agentim.android",
            AgentimPairingPhase.Pending,
            0,
            300_000,
            0,
            2_000
        )
        AgentimPairingState(
            homeserver = "https://agents.example",
            targetUserId = request.targetUserId,
            phase = phase,
            request = request.takeIf { phase in setOf(PairingScreenPhase.Waiting, PairingScreenPhase.Connecting, PairingScreenPhase.Connected) },
            seconds = 300
        )
    }
}

@PreviewsDayNight
@Composable
internal fun AgentimPairingViewPreview(@PreviewParameter(AgentimPairingStatePreviewParam::class) state: AgentimPairingState) = ElementPreview {
    AgentimPairingView(state, {}, {})
}
