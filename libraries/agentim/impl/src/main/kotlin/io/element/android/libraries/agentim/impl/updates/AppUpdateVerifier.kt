/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import io.element.android.libraries.agentim.api.updates.AppUpdate
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Android's installed signing identity, rather than server metadata, authorizes replacements. */
internal class AppUpdateVerifier(private val context: Context) {
    fun verify(file: File, update: AppUpdate) {
        require(file.isFile && file.length() == update.bytes)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == update.sha256)
        ZipFile(file).use { archive ->
            val abis = archive.entries().asSequence().map { it.name.split('/') }
                .filter { it.size == 3 && it[0] == "lib" && it[2].endsWith(".so") }.map { it[1] }.toSet()
            require(Build.SUPPORTED_ABIS.any { it in abis }) { "Update does not support this device architecture" }
        }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = requireNotNull(pm.getPackageArchiveInfo(file.absolutePath, flags))
        require(
            archive.packageName == context.packageName && PackageInfoCompat.getLongVersionCode(archive) == update.code &&
                update.code > PackageInfoCompat.getLongVersionCode(installed)
        )
        if (Build.VERSION.SDK_INT < 28) {
            val signatures = requireNotNull(installed.signatures)
            require(signatures.isNotEmpty())
            require(signatures.map { it.toCharsString() }.toSet() == archive.signatures?.map { it.toCharsString() }?.toSet())
            return
        }
        val current = requireNotNull(installed.signingInfo)
        val candidate = requireNotNull(archive.signingInfo)
        val installedSigners = current.apkContentsSigners.map { it.toCharsString() }.toSet()
        if (current.hasMultipleSigners() || candidate.hasMultipleSigners()) {
            require(installedSigners == candidate.apkContentsSigners.map { it.toCharsString() }.toSet())
        } else {
            // Android validates rotation proofs while parsing the signed archive.
            require(candidate.signingCertificateHistory.any { it.toCharsString() in installedSigners })
        }
    }
}
