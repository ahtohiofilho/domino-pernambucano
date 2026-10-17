package com.ahtohiofilho.dominopernambucano.advertising

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.ui.game.PostMatchRecoveryPlayer
import com.ahtohiofilho.dominopernambucano.ui.game.PostMatchRecoverySnapshot
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostMatchRecoveryStoreTest {
    @Test
    fun result_receipt_survives_restart_and_expires_after_sixty_seconds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "v35_receipt_${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        var now = 10_000L
        val snapshot = PostMatchRecoverySnapshot(
            recordedAtEpochMillis = now,
            matchMode = DominoMatchMode.PUBLIC_RANKED,
            localTeamIndex = 1,
            localTeamWon = true,
            localScore = 6,
            opponentScore = 4,
            completedRounds = 5,
            players = (0..3).map { seat ->
                PostMatchRecoveryPlayer(
                    seatIndex = seat,
                    name = "Player $seat",
                    pointsScored = seat,
                    touchesGiven = 2,
                    automaticRounds = 0,
                )
            },
            teamBalanceDeltaByTeam = listOf(-2, 2),
            mvpSeatIndex = 1,
        )
        try {
            PostMatchRecoveryStore(preferences) { now }.save(snapshot)

            now += 59_999L
            val restartedStore = PostMatchRecoveryStore(preferences) { now }
            assertEquals(snapshot, restartedStore.readValid())

            now += 1L
            assertNull(restartedStore.readValid())
            assertNull(PostMatchRecoveryStore(preferences) { now }.readValid())
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun a_corrupt_receipt_is_removed_without_crashing_startup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "v35_corrupt_${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("pending_post_match_receipt_v35", "{broken").commit()
            val store = PostMatchRecoveryStore(prefs) { 10_000L }
            assertNull(store.readValid())
            assertNull(store.readValid())
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
