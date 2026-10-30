/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import dev.zacsweers.metro.Inject
import io.element.android.libraries.agentim.api.account.AccountAccessRejected
import io.element.android.libraries.agentim.impl.AgentimHttp
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Inject
class AccountHttp(private val urls: AgentimHttp) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    internal fun base(homeserver: String): String {
        val url = urls.base(homeserver)
        require(url.encodedPath == "/") { "Enter the actual homeserver origin" }
        return url.toString().trimEnd('/')
    }

    internal suspend fun request(homeserver: String, path: String, body: JSONObject? = null, bearer: String? = null): JSONObject {
        require(path.startsWith("/_synapse/client/agentim/v2/account/") || path == "/_matrix/client/v3/keys/query")
        val request = Request.Builder().url(base(homeserver) + path).header("Cache-Control", "no-store").apply {
            if (bearer != null) header("Authorization", "Bearer $bearer")
            if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
        }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("AgentIM connection failed"))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val json = response.use {
                            val source = it.body.source()
                            if (source.request(1_048_577)) throw IOException("AgentIM response exceeds its limit")
                            val json = runCatching { JSONObject(source.readUtf8()) }.getOrNull()
                            val requestId = (json?.optString("request_id") ?: it.header("X-Request-ID"))
                                ?.takeIf { value -> value.matches(Regex("[a-f0-9]{32}")) }
                            if (!it.isSuccessful) {
                                val code = json?.optString("errcode")?.takeIf { value -> value.matches(Regex("M_[A-Z_]{1,62}")) } ?: "M_UNKNOWN"
                                throw AccountAccessRejected(code, it.code, requestId)
                            }
                            requireNotNull(json)
                        }
                        if (continuation.isActive) continuation.resume(json)
                    } catch (error: Exception) {
                        val safe = if (error is AccountAccessRejected) error else IOException("AgentIM account request could not finish")
                        if (continuation.isActive) continuation.resumeWithException(safe)
                    }
                }
            })
        }
    }
}
