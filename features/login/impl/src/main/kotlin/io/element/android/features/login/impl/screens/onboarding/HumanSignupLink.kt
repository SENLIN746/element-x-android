/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.features.login.impl.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import java.net.URI

/** Standard Matrix signup lives in the configured Web client; return here for native password login. */
internal fun humanSignupUrl(provider: AccountProvider?): String? {
    val managed = provider as? AccountProvider.Managed ?: return null
    val uri = runCatching { URI(managed.baseUrl) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host == null || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
        uri.path !in listOf("", "/")) {
            return null
        }
    return "${uri.scheme}://${uri.rawAuthority}/#/register"
}

@Composable
internal fun HumanSignupLink(provider: AccountProvider?) {
    val url = humanSignupUrl(provider) ?: return
    val handler = LocalUriHandler.current
    TextButton(stringResource(R.string.screen_onboarding_create_account_web), onClick = { handler.openUri(url) })
}
