/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.updates

import com.google.common.truth.Truth.assertThat
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateManifestTest {
    private fun valid() = JSONObject().put("version", 1).put("product", "AgentIM").put("package", "im.agentim.android")
        .put("release", JSONObject().put("version", "26.10.4-agentim10").put("version_code", 202610042)
            .put("url", "$UPDATE_ORIGIN/downloads/android/android-arm64-aaaaaaaaaaaa.apk")
            .put("sha256", "a".repeat(64)).put("bytes", 123L))

    @Test
    fun `reads a short real HTTP body through EOF without requiring a full buffer`() {
        val json = decodeAppUpdate(valid().toString().toResponseBody())
        assertThat(parseAppUpdate(json).code).isEqualTo(202610042L)
        assertThat(runCatching { decodeAppUpdate(" ".repeat(64 * 1024 + 1).toResponseBody()) }.isFailure).isTrue()
    }

    @Test
    fun `parses bounded direct download and rejects forged origins and paths`() {
        assertThat(parseAppUpdate(valid()).code).isEqualTo(202610042L)
        for (url in listOf(
                "http://agentims.shipkitx.com/downloads/android/x.apk",
                "https://evil.example/a.apk",
            "$UPDATE_ORIGIN/downloads/android/../x.apk",
                "$UPDATE_ORIGIN/downloads/android/android-arm64-aaaaaaaaaaaa.apk?token=x",
            "https://user@agentims.shipkitx.com/downloads/android/android-arm64-aaaaaaaaaaaa.apk",
            "$UPDATE_ORIGIN/downloads/android/android-arm64-bbbbbbbbbbbb.apk"
            )) {
            val json = valid().also { it.getJSONObject("release").put("url", url) }
            assertThat(runCatching { parseAppUpdate(json) }.isFailure).isTrue()
        }
    }
    @Test
    fun `rejects malformed identity size versions and secrets instead of coercing types`() {
        for ((key, value) in listOf(
                "bytes" to -1,
                "bytes" to 1024L * 1024 * 1024,
                "bytes" to "123",
            "version_code" to 0,
                "version_code" to 12.5,
                "version" to "../x",
                "sha256" to "bad",
                "token" to "synthetic"
            )) {
            val json = valid().also { it.getJSONObject("release").put(key, value) }
            assertThat(runCatching { parseAppUpdate(json) }.isFailure).isTrue()
        }
        assertThat(runCatching { parseAppUpdate(valid().put("package", "im.other")) }.isFailure).isTrue()
    }
}
