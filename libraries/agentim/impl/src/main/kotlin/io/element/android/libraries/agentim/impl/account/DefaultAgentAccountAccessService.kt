/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.agentim.impl.account

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentim.api.account.AccountAccessRejected
import io.element.android.libraries.agentim.api.account.AgentAccountAccessService
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultAgentAccountAccessService(
    private val vault: AccountVault, private val keys: AccountKeyManager, private val login: AccountLogin,
    private val completion: AccountLoginCompletion, private val http: AccountHttp, private val sessions: SessionStore,
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val scope: CoroutineScope,
) : AgentAccountAccessService {
    private val mutex = Mutex()
    private val started = AtomicBoolean()
    private suspend fun <T> operation(action: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatchingExceptions { mutex.withLock { action() } }.recoverCatching { error ->
            if (error is AccountAccessRejected) throw error
            throw java.io.IOException("Account operation could not finish; private state was preserved")
        }
    }
    override suspend fun ensure(userId: String) = operation { keys.status(keys.ensure(userId)) }
    override suspend fun export(userId: String, destination: Uri) = operation {
        val key = keys.ensure(userId)
        val bytes = key.json().toString().toByteArray(Charsets.UTF_8)
        requireNotNull(context.contentResolver.openOutputStream(destination, "wt")).use {
            it.write(bytes)
            it.flush()
        }
        val saved = read(destination)
        require(saved.json().toString() == key.json().toString()) { "Account file readback did not match" }
        keys.prove(saved)
        vault.save(key, true, exported = true)
        keys.status(key)
    }
    override suspend fun verify(userId: String, source: Uri) = operation {
        val key = read(source)
        val session = keys.session(userId)
        require(key.userId == userId && key.homeserver == http.base(session.homeserverUrl))
        keys.prove(key)
        vault.save(key, true, exported = true)
        keys.status(key)
    }
    override suspend fun revoke(userId: String) = operation {
        val key = keys.ensure(userId)
        val session = keys.session(userId)
        val reply = http.request(key.homeserver, "/_synapse/client/agentim/v2/account/credentials/${key.id}/revoke", JSONObject(), session.accessToken)
        require(reply.getBoolean("ok"))
        // Keep the device; the next explicit ensure/export generates a fresh portable key.
        Unit
    }
    override suspend fun restore(source: Uri) = operation {
        val key = read(source)
        keys.prove(key)
        sessions.getSession(key.userId)?.let {
            require(it.isTokenValid && http.base(it.homeserverUrl) == key.homeserver) { "Preserve or sign out the original native device first" }
            vault.save(key, true, exported = true)
            return@operation key.userId
        }
        login.restore(key)
    }
    override suspend fun resume() = operation { login.resume() }
    override suspend fun pending() = operation { vault.pending() != null }
    override fun start() {
        if (started.compareAndSet(false, true)) scope.launch(Dispatchers.IO) { completion.observe() }
    }
    private fun read(uri: Uri): AccountCredential {
        require(uri.scheme == "content") { "Select an account credential file" }
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 16_384)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= 16_384)
        return AccountCredential.parse(JSONObject(bytes.toString(Charsets.UTF_8)))
    }
}
