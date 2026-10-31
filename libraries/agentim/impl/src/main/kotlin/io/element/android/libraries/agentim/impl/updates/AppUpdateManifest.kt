/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.updates

import io.element.android.libraries.agentim.api.updates.AppUpdate
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

internal const val UPDATE_ORIGIN = "https://agentims.shipkitx.com"
internal const val UPDATE_FEED = "$UPDATE_ORIGIN/downloads/android/updates.json"

internal fun parseAppUpdate(json: JSONObject): AppUpdate {
    require(json.keys().asSequence().toSet() == setOf("version", "product", "package", "release"))
    require(json.get("version") == 1 && json.getString("product") == "AgentIM" && json.getString("package") == "im.agentim.android")
    val item = json.getJSONObject("release")
    require(item.keys().asSequence().toSet() == setOf("version", "version_code", "url", "sha256", "bytes"))
    require(item.get("version") is String && item.get("url") is String && item.get("sha256") is String)
    require(item.get("version_code") is Int || item.get("version_code") is Long)
    require(item.get("bytes") is Int || item.get("bytes") is Long)
    val update = AppUpdate(
        item.getString("version"),
        item.getLong("version_code"),
        item.getString("url"),
        item.getString("sha256"),
        item.getLong("bytes")
    )
    require(update.version.matches(Regex("[a-zA-Z0-9.-]{1,80}")))
    require(update.code in 1..Int.MAX_VALUE.toLong() && update.bytes in 1..512L * 1024 * 1024)
    require(update.sha256.matches(Regex("[a-f0-9]{64}")))
    val uri = URI(update.url)
    require(uri.scheme == "https" && uri.host == "agentims.shipkitx.com" && uri.port == -1)
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    require(uri.rawPath.matches(Regex("/downloads/android/android-arm64-[a-f0-9]{12}\\.apk")))
    require(uri.rawPath.endsWith("${update.sha256.take(12)}.apk"))
    return update
}

internal class AppUpdateManifest {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    fun fetch(): JSONObject = client.newCall(Request.Builder().url(UPDATE_FEED).build()).execute().use { response ->
        check(response.isSuccessful)
        decodeAppUpdate(requireNotNull(response.body))
    }
}

internal fun decodeAppUpdate(body: ResponseBody): JSONObject {
    val buffer = Buffer()
    val source = body.source()
    while (source.read(buffer, 8192) != -1L) {
        require(buffer.size <= 64 * 1024)
    }
    return JSONObject(buffer.readUtf8()).also(::parseAppUpdate)
}
