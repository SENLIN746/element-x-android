/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MatrixTokenLoginTest {
    @Test
    fun `token login omits device id and restores only the newly allocated Android device`() = runTest {
        lateinit var request: Request
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    """{"user_id":"@agent:example.org","device_id":"NEW_ANDROID","access_token":"synthetic-new-device"}"""
                    .toResponseBody("application/json".toMediaType())
                ).build()
        }.build()
        val client = mockk<Client>()
        every { client.homeserver() } returns "https://example.org"
        every { client.slidingSyncVersion() } returns SlidingSyncVersion.NATIVE
        val session = slot<Session>()
        coEvery { client.restoreSession(capture(session)) } returns Unit

        MatrixTokenLogin(http).login(client, "synthetic-short-lived-token")

        val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        assertThat(request.url.encodedPath).isEqualTo("/_matrix/client/v3/login")
        assertThat(body.getString("type")).isEqualTo("m.login.token")
        assertThat(body.has("device_id")).isFalse()
        assertThat(body.getString("initial_device_display_name")).isEqualTo("AgentIM Android")
        assertThat(session.captured.deviceId).isEqualTo("NEW_ANDROID")
        assertThat(session.captured.accessToken).isEqualTo("synthetic-new-device")
        assertThat(session.captured.homeserverUrl).isEqualTo("https://example.org")
        coVerify(exactly = 1) { client.restoreSession(any()) }
    }

    @Test
    fun `expired token cannot initialize a local SDK session`() = runTest {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = mockk<Client>()
        every { client.homeserver() } returns "https://example.org"
        val result = runCatching { MatrixTokenLogin(http).login(client, "expired-synthetic-token") }
        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { client.restoreSession(any()) }
    }
}
