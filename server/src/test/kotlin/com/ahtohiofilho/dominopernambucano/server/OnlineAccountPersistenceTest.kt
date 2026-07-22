package com.ahtohiofilho.dominopernambucano.server

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineAccountPersistenceTest {
    @Test
    fun promoted_account_is_idempotent_and_survives_restart() {
        val root = File(
            System.getProperty("java.io.tmpdir"),
            "online-account-persistence-${UUID.randomUUID()}",
        )
        val stateFile = File(root, "authoritative-state.json")

        try {
            val firstStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 1_000L },
                accountIdFactory = { "account-1" },
            )
            val firstPromotion = requireNotNull(
                firstStore.promoteAccount(
                    playerId = "player-1",
                ),
            )
            val repeatedPromotion = firstStore.promoteAccount(
                playerId = "player-1",
            )

            assertEquals(firstPromotion, repeatedPromotion)
            firstStore.close()

            val restartedStore = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                nowEpochMillis = { 2_000L },
                accountIdFactory = { "account-must-not-be-created" },
            )
            val accountRetry = restartedStore.promoteAccount(
                playerId = "player-1",
                expectedAccountId = "account-1",
            )
            val anonymousRetry = restartedStore.promoteAccount(
                playerId = "player-1",
            )

            assertEquals(firstPromotion, accountRetry)
            assertEquals(firstPromotion, anonymousRetry)
            restartedStore.close()
        } finally {
            root.deleteRecursively()
        }
    }
}
