package com.ahtohiofilho.dominopernambucano.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PasswordPostAuthHandoffRegressionTest {
    @Test
    fun parentOwnedProfileSyncSurvivesAuthScreenScopeCancellation() =
        runBlocking {
            val appJob = SupervisorJob()
            val appScope = CoroutineScope(
                appJob + Dispatchers.Unconfined,
            )
            val authScreenJob = Job(appJob)
            val authScreenScope = CoroutineScope(
                authScreenJob + Dispatchers.Unconfined,
            )
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()

            val profileSyncJob =
                launchAccountProfileSyncAfterAuthentication(
                    scope = appScope,
                ) {
                    entered.complete(Unit)
                    release.await()
                    completed.complete(Unit)
                }

            withTimeout(1_000) {
                entered.await()
            }

            authScreenScope.cancel()

            assertTrue(authScreenJob.isCancelled)
            assertFalse(profileSyncJob.isCancelled)
            assertFalse(completed.isCompleted)

            release.complete(Unit)

            withTimeout(1_000) {
                profileSyncJob.join()
            }

            assertTrue(completed.isCompleted)

            appScope.cancel()
        }
}