/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.updates.AppUpdate
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateVerifierTest {
    private val context = mockk<Context>()
    private val manager = mockk<PackageManager>()
    private fun info(name: String, code: Long, signer: String): PackageInfo = PackageInfo().apply {
        packageName = name
        setLongVersionCode(code)
        signingInfo = mockk<SigningInfo>().also {
            val signature = Signature(signer.toByteArray())
            every { it.apkContentsSigners } returns arrayOf(signature)
            every { it.signingCertificateHistory } returns arrayOf(signature)
            every { it.hasMultipleSigners() } returns false
        }
    }
    @Test
    fun `only exact bytes with a matching installed signing identity and increasing package version are accepted`() {
        val file = File.createTempFile("synthetic-update-", ".apk")
        try {
            ZipOutputStream(file.outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("lib/${Build.SUPPORTED_ABIS.first()}/libsynthetic.so"))
                archive.write("Synthetic native payload; PackageManager is mocked".toByteArray())
                archive.closeEntry()
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            val update = AppUpdate("synthetic", 2, "", hash, file.length())
            every { context.packageName } returns "im.agentim.android"
            every { context.packageManager } returns manager
            every { manager.getPackageInfo("im.agentim.android", any<Int>()) } returns info("im.agentim.android", 1, "trusted")
            every { manager.getPackageArchiveInfo(file.absolutePath, any<Int>()) } returns info("im.agentim.android", 2, "trusted")
            val verifier = AppUpdateVerifier(context)
            verifier.verify(file, update)
            for (candidate in listOf(
                    info("im.agentim.android", 2, "attacker"),
                    info("im.other", 2, "trusted"),
                info("im.agentim.android", 1, "trusted"),
                    info("im.agentim.android", 3, "trusted")
                )) {
                every { manager.getPackageArchiveInfo(file.absolutePath, any<Int>()) } returns candidate
                assertThat(runCatching { verifier.verify(file, update) }.isFailure).isTrue()
            }
            every { manager.getPackageArchiveInfo(file.absolutePath, any<Int>()) } returns info("im.agentim.android", 2, "trusted")
            assertThat(runCatching { verifier.verify(file, update.copy(sha256 = "a".repeat(64))) }.isFailure).isTrue()
            assertThat(runCatching { verifier.verify(file, update.copy(bytes = file.length() + 1)) }.isFailure).isTrue()
        } finally {
            file.delete()
        }
    }
}
