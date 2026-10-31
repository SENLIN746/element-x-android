/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.appnav.loggedin

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.push.api.PusherRegistrationFailure
import org.junit.Test

class PusherErrorReasonTest {
    @Test
    fun `missing setup does not show an error modal on every resume`() {
        assertThat(PusherRegistrationFailure.AccountNotVerified().getReason()).isNull()
        assertThat(PusherRegistrationFailure.NoDistributorsAvailable().getReason()).isNull()
        assertThat(PusherRegistrationFailure.NoProvidersAvailable().getReason()).isNull()
        assertThat(IllegalStateException("Synthetic unexpected failure").getReason()).isNotNull()
    }
}
