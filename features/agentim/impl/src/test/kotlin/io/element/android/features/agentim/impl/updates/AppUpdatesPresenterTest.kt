/*
 * Copyright (c) 2026 AgentIM contributors.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */
package io.element.android.features.agentim.impl.updates

import android.content.Context
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentim.api.updates.AgentAppUpdates
import io.element.android.libraries.agentim.api.updates.AppUpdatePhase
import io.element.android.libraries.agentim.api.updates.AppUpdateStatus
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppUpdatesPresenterTest {
    @Test
    fun `download failure stays actionable and installation always requires an explicit action`() = runTest {
        val updates = mockk<AgentAppUpdates>()
        val context = mockk<Context>()
        val intent = mockk<Intent>()
        coEvery { updates.refresh() } returns Result.success(AppUpdateStatus(AppUpdatePhase.Current))
        coEvery { updates.check() } returns Result.success(AppUpdateStatus(AppUpdatePhase.Available))
        coEvery { updates.download() } returns Result.failure(IllegalStateException("Synthetic network failure"))
        coEvery { updates.cancel() } returns Result.success(Unit)
        coEvery { updates.installIntent() } returns Result.success(intent)
        every { context.startActivity(intent) } just Runs
        AppUpdatesPresenter(updates, context).test {
            val initial = awaitLastSequentialItem()
            initial.action(AppUpdateAction.Check)
            val available = consumeItemsUntilPredicate { !it.busy && it.status.phase == AppUpdatePhase.Available }.last()
            available.action(AppUpdateAction.Download)
            val failed = consumeItemsUntilPredicate { !it.busy && it.failed }.last()
            assertThat(failed.status.phase).isEqualTo(AppUpdatePhase.Available)
            coVerify(exactly = 0) { updates.installIntent() }
            failed.action(AppUpdateAction.Cancel)
            val cancelled = consumeItemsUntilPredicate { !it.busy && !it.failed && it.status.phase == AppUpdatePhase.Current }.last()
            coEvery { updates.check() } returns Result.success(AppUpdateStatus(AppUpdatePhase.Store))
            cancelled.action(AppUpdateAction.Check)
            val store = consumeItemsUntilPredicate { !it.busy && it.status.phase == AppUpdatePhase.Store }.last()
            coVerify(exactly = 0) { updates.installIntent() }
            store.action(AppUpdateAction.Install)
            awaitLastSequentialItem()
            coVerify(exactly = 1) { updates.installIntent() }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
