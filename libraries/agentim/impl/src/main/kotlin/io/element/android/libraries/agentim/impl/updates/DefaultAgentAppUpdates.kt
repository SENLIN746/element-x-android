/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.updates

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentim.api.updates.AgentAppUpdates
import io.element.android.libraries.agentim.api.updates.AppUpdate
import io.element.android.libraries.agentim.api.updates.AppUpdatePhase
import io.element.android.libraries.agentim.api.updates.AppUpdateStatus
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultAgentAppUpdates(@ApplicationContext private val context: Context) : AgentAppUpdates {
    private val lock = Mutex()
    private val store = context.getSharedPreferences("agentim-app-updates", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val manifest = AppUpdateManifest()
    private val verifier = AppUpdateVerifier(context)
    private var offer: AppUpdate? = null
    private val directory get() = File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), "updates")
    private fun file(update: AppUpdate) = File(directory, "${update.sha256}.apk")
    private fun installedCode() = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    private fun installer(): String? = if (Build.VERSION.SDK_INT >= 30) {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        context.packageManager.getInstallerPackageName(context.packageName)
    }
    private fun fromStore() = installer()?.let { it == "com.android.vending" || it.startsWith("org.fdroid.") } == true
    private suspend fun <T> operation(action: () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatchingExceptions { lock.withLock { action() } }
    }
    private fun saved(): AppUpdate? = store.getString("manifest", null)?.let { parseAppUpdate(JSONObject(it)) }
    private fun clear() {
        val id = store.getLong("download", -1)
        if (id != -1L) manager.remove(id)
        runCatching { saved() }.getOrNull()?.let { file(it).delete() }
        check(store.edit().clear().commit())
    }
    private fun status(): AppUpdateStatus {
        if (fromStore()) return AppUpdateStatus(AppUpdatePhase.Store)
        val update = saved() ?: return AppUpdateStatus(if (offer == null) AppUpdatePhase.Current else AppUpdatePhase.Available, offer)
        if (update.code <= installedCode()) {
            clear()
            offer = null
            return AppUpdateStatus(AppUpdatePhase.Current)
        }
        manager.query(DownloadManager.Query().setFilterById(store.getLong("download", -1))).use { cursor ->
            check(cursor.moveToFirst()) { "Download unavailable; cancel and retry" }
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    verifier.verify(file(update), update)
                    return AppUpdateStatus(if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) {
                        AppUpdatePhase.Ready
                    } else {
                        AppUpdatePhase.InstallPermission
                    }, update, 100)
                }
                DownloadManager.STATUS_FAILED -> error("Download failed; cancel and retry")
                else -> {
                    val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    require(bytes <= update.bytes)
                    return AppUpdateStatus(AppUpdatePhase.Downloading, update, (bytes * 100 / update.bytes).toInt())
                }
            }
        }
    }
    override suspend fun refresh() = operation { status() }
    override suspend fun check() = operation {
        if (fromStore()) return@operation AppUpdateStatus(AppUpdatePhase.Store)
        if (saved() != null) return@operation status()
        offer = parseAppUpdate(manifest.fetch()).takeIf { it.code > installedCode() }
        status()
    }
    override suspend fun download() = operation {
        check(!fromStore())
        if (saved() != null) return@operation status()
        val json = manifest.fetch()
        val update = parseAppUpdate(json)
        check(update.code > installedCode())
        directory.mkdirs()
        check(!file(update).exists()) { "A previous download remains; cancel and retry" }
        val request = DownloadManager.Request(Uri.parse(update.url))
            .setTitle("AgentIM ${update.version}")
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/${update.sha256}.apk")
        // Persist metadata before enqueueing so interrupted setup is recoverable through Cancel.
        check(store.edit().putString("manifest", json.toString()).commit())
        val id = manager.enqueue(request)
        check(store.edit().putLong("download", id).commit())
        offer = update
        status()
    }
    override suspend fun installIntent() = operation {
        if (fromStore()) {
            return@operation Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                .setPackage(installer()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val state = status()
        if (Build.VERSION.SDK_INT >= 26 && state.phase == AppUpdatePhase.InstallPermission) {
            return@operation Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        check(state.phase == AppUpdatePhase.Ready)
        val update = requireNotNull(state.update)
        verifier.verify(file(update), update)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file(update))
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    override suspend fun cancel() = operation {
        clear()
        offer = null
    }
}
