/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.androidutils.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Atomic, device-bound private records. Neither plaintext nor keys enter Android backup. */
@Inject
@SingleIn(AppScope::class)
class BootstrapFiles(@ApplicationContext private val context: Context) {
    private fun file(name: String): AtomicFile {
        require(Regex("[a-z][a-z0-9-]{1,63}").matches(name))
        return AtomicFile(File(context.noBackupFilesDir, name))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "agentim-bootstrap-v1"
        return store.getKey(alias, null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            )
            generateKey()
        }
    }

    @Synchronized
    fun read(name: String): ByteArray? {
        val record = file(name)
        if (!record.baseFile.exists() && !File(record.baseFile.path + ".bak").exists()) return null
        val bytes = record.openRead().use { it.readBytesLimited() }
        require(bytes.size >= 29 && bytes[0] == 1.toByte()) { "Unsupported private bootstrap record" }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            updateAAD(name.toByteArray(Charsets.UTF_8))
            doFinal(bytes.copyOfRange(13, bytes.size))
        }
    }

    @Synchronized
    fun write(name: String, bytes: ByteArray) {
        require(bytes.size <= 65_536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(name.toByteArray(Charsets.UTF_8))
        }
        require(cipher.iv.size == 12)
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(bytes)
        val record = file(name)
        val stream = record.startWrite()
        try {
            stream.write(encrypted)
            stream.fd.sync()
            record.finishWrite(stream)
        } catch (failure: Throwable) {
            record.failWrite(stream)
            throw failure
        }
    }

    @Synchronized
    fun delete(name: String) = file(name).delete()

    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val buffer = ByteArray(65_566)
        var size = 0
        while (size < buffer.size) {
            val read = read(buffer, size, buffer.size - size)
            if (read == -1) return buffer.copyOf(size)
            size += read
        }
        error("Private bootstrap record exceeds its limit")
    }
}
