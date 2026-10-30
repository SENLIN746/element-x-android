/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.login.impl.screens.onboarding

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import org.junit.Test

class HumanSignupLinkTest {
    @Test
    fun `only a managed HTTPS origin supplies the website signup link`() {
        fun managed(url: String) = AccountProvider.Managed("example.org", url, false)
        assertThat(humanSignupUrl(managed("https://example.org/"))).isEqualTo("https://example.org/#/register")
        listOf("http://example.org", "https://user:secret@example.org", "https://example.org/path", "https://example.org?x=1").forEach {
            assertThat(humanSignupUrl(managed(it))).isNull()
        }
        assertThat(humanSignupUrl(AccountProvider.Generic("example.org"))).isNull()
    }
}
