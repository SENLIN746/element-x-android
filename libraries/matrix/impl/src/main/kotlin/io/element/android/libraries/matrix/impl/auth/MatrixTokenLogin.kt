/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.auth

import android.os.Build
import android.security.NetworkSecurityPolicy
import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.impl.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.Session
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The pinned FFI has no token-login method; consume m.login.token before initializing the new SDK store. */
class MatrixTokenLogin @Inject constructor() {
    private var http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    internal constructor(http: OkHttpClient) : this() {
        this.http = http
    }

    private fun allowsDevelopmentHttp(host: String): Boolean {
        if (!BuildConfig.DEBUG || host !in setOf("10.0.2.2", "localhost", "127.0.0.1")) return false
        val policy = NetworkSecurityPolicy.getInstance()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) policy.isCleartextTrafficPermitted(host) else policy.isCleartextTrafficPermitted
    }

    suspend fun login(client: Client, token: String) {
        require(token.isNotBlank())
        val base = client.homeserver().toHttpUrl()
        require(base.isHttps || allowsDevelopmentHttp(base.host))
        val body = JSONObject().put("type", "m.login.token").put("token", token)
            .put("initial_device_display_name", "AgentIM Android")
            .put("refresh_token", true)
        // device_id is deliberately absent: the server allocates a new independent device.
        val request = Request.Builder().url(base.newBuilder().addPathSegments("_matrix/client/v3/login").build())
            .header("Cache-Control", "no-store")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val session = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Matrix token login failed (HTTP ${response.code})")
            val source = response.body.source()
            if (source.request(65_537)) throw IOException("Invalid Matrix login response")
            val json = JSONObject(source.readUtf8())
            Session(
                accessToken = json.getString("access_token"),
                refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null,
                userId = json.getString("user_id"),
                deviceId = json.getString("device_id"),
                homeserverUrl = client.homeserver(),
                oauthData = null,
                slidingSyncVersion = client.slidingSyncVersion(),
            )
        }
        // These credentials were created by this Android login. No external device/store/key is imported.
        client.restoreSession(session)
    }
}
