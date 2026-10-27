/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl

import android.os.Build
import android.security.NetworkSecurityPolicy
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Dedicated transport: no body logger, cache, redirect or credential forwarding. */
@Inject
@SingleIn(AppScope::class)
class AgentimHttp {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun allowsDevelopmentHttp(host: String): Boolean {
        if (!BuildConfig.DEBUG || host !in setOf("10.0.2.2", "localhost", "127.0.0.1")) return false
        val policy = NetworkSecurityPolicy.getInstance()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) policy.isCleartextTrafficPermitted(host) else policy.isCleartextTrafficPermitted
    }

    private fun base(homeserver: String): HttpUrl {
        val base = homeserver.toHttpUrl()
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
        require(base.isHttps || allowsDevelopmentHttp(base.host)) { "Use an HTTPS homeserver" }
        return base
    }

    internal fun accountDataUrl(homeserver: String, userId: String): HttpUrl = base(homeserver).newBuilder()
        .addPathSegments("_matrix/client/v3/user").addPathSegment(userId)
        .addPathSegments("account_data/m.ignored_user_list").build()

    internal fun endpoint(homeserver: String, path: List<String>, query: Map<String, String?> = emptyMap()): HttpUrl =
        base(homeserver).newBuilder().addPathSegments("_synapse/client/agentim/v1").apply {
            path.forEach(::addPathSegment)
            query.forEach { (key, value) -> if (!value.isNullOrBlank()) addQueryParameter(key, value) }
        }.build()

    suspend fun request(url: HttpUrl, bearer: String, method: String = "GET", body: JSONObject? = null, allowMissing: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            require(bearer.isNotBlank()) { "Authentication is required" }
            val request = Request.Builder().url(url).header("Authorization", "Bearer $bearer")
                .header("Cache-Control", "no-store")
                .method(method, body?.toString()?.toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (allowMissing && response.code == 404) return@withContext JSONObject()
                if (response.code == 401) throw AgentimAuthenticationExpired()
                if (!response.isSuccessful) throw IOException("AgentIM request failed (HTTP ${response.code})")
                val source = response.body.source()
                if (source.request(1_048_577)) throw IOException("AgentIM response exceeded its limit")
                JSONObject(source.readUtf8())
            }
        }
}

/** An explicit authentication rejection; no authorized write was performed. */
internal class AgentimAuthenticationExpired : IOException("AgentIM authentication expired")
