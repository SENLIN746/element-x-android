/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.libraries.push.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.pusher.UnsetHttpPusherData
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.pushers.FakePushersService
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PusherIdentityMigrationTest {
    @Test
    fun `migration only retires the old app id for the same push endpoint`() = runTest {
        val removed = mutableListOf<UnsetHttpPusherData>()
        val client = FakeMatrixClient(pushersService = FakePushersService(unsetHttpPusherResult = {
            removed += it
            Result.success(Unit)
        }))
        assertThat(migrateAgentIMPusherIdentity(client, "im.agentim.android", "synthetic-endpoint").isSuccess).isTrue()
        assertThat(removed).containsExactly(UnsetHttpPusherData(pushKey = "synthetic-endpoint", appId = "im.vector.app.android"))
        migrateAgentIMPusherIdentity(client, "another.app", "another-endpoint")
        assertThat(removed).hasSize(1)
    }
}
