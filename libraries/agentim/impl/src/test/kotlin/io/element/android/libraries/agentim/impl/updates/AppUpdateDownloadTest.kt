/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.updates

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.MatrixCursor
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.updates.AppUpdatePhase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateDownloadTest {
    @After
    fun cleanup() = unmockkAll()

    @Test
    fun `system download survives presenter recreation and cancellation only removes its recorded download`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val preferences = app.getSharedPreferences("synthetic-app-updates", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val directory = Files.createTempDirectory("synthetic-app-updates-").toFile()
        val context = mockk<Context>()
        val manager = mockk<DownloadManager>()
        val pm = mockk<PackageManager>()
        every { context.packageName } returns "im.agentim.android"
        every { context.packageManager } returns pm
        every { context.getExternalFilesDir(any()) } returns directory
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { context.getSystemService(DownloadManager::class.java) } returns manager
        every { pm.getPackageInfo("im.agentim.android", any<Int>()) } returns PackageInfo().apply { versionCode = 1 }
        every { pm.getInstallerPackageName("im.agentim.android") } returns null
        val manifest = JSONObject().put("version", 1).put("product", "AgentIM").put("package", "im.agentim.android")
            .put("release", JSONObject().put("version", "synthetic").put("version_code", 2)
                .put("url", "$UPDATE_ORIGIN/downloads/android/android-arm64-aaaaaaaaaaaa.apk").put("sha256", "a".repeat(64)).put("bytes", 100))
        mockkConstructor(AppUpdateManifest::class)
        every { anyConstructed<AppUpdateManifest>().fetch() } returns manifest
        every { manager.enqueue(any()) } returns 71
        every { manager.query(any()) } answers {
            MatrixCursor(arrayOf(DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                .apply { addRow(arrayOf(DownloadManager.STATUS_RUNNING, 20)) }
        }
        every { manager.remove(71) } returns 1
        try {
            val first = DefaultAgentAppUpdates(context)
            assertThat(first.check().getOrThrow().phase).isEqualTo(AppUpdatePhase.Available)
            assertThat(first.download().getOrThrow().percent).isEqualTo(20)
            val restored = DefaultAgentAppUpdates(context)
            assertThat(restored.refresh().getOrThrow().phase).isEqualTo(AppUpdatePhase.Downloading)
            verify(exactly = 1) { manager.enqueue(any()) }
            assertThat(restored.cancel().isSuccess).isTrue()
            verify(exactly = 1) { manager.remove(71) }
            assertThat(restored.refresh().getOrThrow().phase).isEqualTo(AppUpdatePhase.Current)
            every { pm.getInstallerPackageName("im.agentim.android") } returns "com.android.vending"
            assertThat(restored.check().getOrThrow().phase).isEqualTo(AppUpdatePhase.Store)
            assertThat(restored.installIntent().getOrThrow().`package`).isEqualTo("com.android.vending")
        } finally {
            directory.deleteRecursively()
            preferences.edit().clear().commit()
        }
    }
}
