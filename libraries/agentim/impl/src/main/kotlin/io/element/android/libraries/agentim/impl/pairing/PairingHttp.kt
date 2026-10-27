/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.pairing

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentim.impl.AgentimHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

/** Anonymous proof transport: no cookies, bearer, cache, redirects or automatic write retries. */
@Inject
@SingleIn(AppScope::class)
class PairingHttp(private val origins: AgentimHttp) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun post(homeserver: String, path: List<String>, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        require(path.firstOrNull() == "pairings")
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 2_048)
        val url = origins.endpoint(homeserver, path)
        require(url.encodedPath.startsWith("/_synapse/client/agentim/v1/pairings"))
        val request = Request.Builder().url(url).header("Cache-Control", "no-store")
            .post(bytes.toRequestBody("application/json".toMediaType()))
            .build()
        await(client.newCall(request)).use { response ->
            if (!response.isSuccessful) throw IOException("AgentIM pairing request failed (HTTP ${response.code})")
            if (response.body.contentType()?.let { it.type == "application" && it.subtype == "json" } != true) {
                throw IOException("Invalid AgentIM pairing response")
            }
            val source = response.body.source()
            if (source.request(32_769)) throw IOException("AgentIM pairing response exceeded its limit")
            try {
                JSONObject(source.readUtf8())
            } catch (_: Exception) {
                throw IOException("Invalid AgentIM pairing response")
            }
        }
    }

    private suspend fun await(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("AgentIM pairing connection failed"))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}
