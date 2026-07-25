package com.ahtohiofilho.dominopernambucano.match

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DominoMatchModeTest {
    @Test
    fun canonical_modes_have_stable_serializable_names() {
        assertEquals(
            listOf(
                "PUBLIC_RANKED",
                "PRIVATE_UNRANKED",
                "OFFLINE_LOCAL",
                "PUBLIC_CASUAL",
            ),
            DominoMatchMode.entries.map { mode -> mode.name },
        )
    }

    @Test
    fun public_ranked_is_the_only_mvp_public_competitive_pool() {
        val mode = DominoMatchMode.PUBLIC_RANKED

        assertTrue(mode.isServerHosted)
        assertTrue(mode.usesPublicMatchmaking)
        assertTrue(mode.requiresAuthenticatedAccount)
        assertFalse(mode.allowsAnonymousIdentity)
        assertTrue(mode.contributesToRanking)
        assertTrue(mode.isEnabledInMvp)
        assertEquals(
            RankedMatchClassification.RANKED,
            mode.rankedMatchClassification,
        )
    }

    @Test
    fun private_unranked_accepts_visitors_without_competitive_persistence() {
        val mode = DominoMatchMode.PRIVATE_UNRANKED

        assertTrue(mode.isServerHosted)
        assertFalse(mode.usesPublicMatchmaking)
        assertFalse(mode.requiresAuthenticatedAccount)
        assertTrue(mode.allowsAnonymousIdentity)
        assertFalse(mode.contributesToRanking)
        assertTrue(mode.isEnabledInMvp)
        assertEquals(
            RankedMatchClassification.UNRANKED,
            mode.rankedMatchClassification,
        )
    }

    @Test
    fun offline_local_stays_outside_the_online_competitive_boundary() {
        val mode = DominoMatchMode.OFFLINE_LOCAL

        assertFalse(mode.isServerHosted)
        assertFalse(mode.usesPublicMatchmaking)
        assertFalse(mode.requiresAuthenticatedAccount)
        assertTrue(mode.allowsAnonymousIdentity)
        assertFalse(mode.contributesToRanking)
        assertTrue(mode.isEnabledInMvp)
        assertEquals(
            RankedMatchClassification.UNRANKED,
            mode.rankedMatchClassification,
        )
    }

    @Test
    fun public_casual_is_reserved_and_disabled_in_the_mvp() {
        val mode = DominoMatchMode.PUBLIC_CASUAL

        assertTrue(mode.isServerHosted)
        assertTrue(mode.usesPublicMatchmaking)
        assertTrue(mode.requiresAuthenticatedAccount)
        assertFalse(mode.allowsAnonymousIdentity)
        assertFalse(mode.contributesToRanking)
        assertFalse(mode.isEnabledInMvp)
        assertEquals(
            RankedMatchClassification.UNRANKED,
            mode.rankedMatchClassification,
        )
    }

    @Test
    fun no_other_mode_can_feed_the_ranked_accumulator() {
        assertEquals(
            listOf(DominoMatchMode.PUBLIC_RANKED),
            DominoMatchMode.entries.filter { mode ->
                mode.contributesToRanking
            },
        )
    }

    @Test
    fun mvp_does_not_expose_a_second_public_pool() {
        assertEquals(
            listOf(DominoMatchMode.PUBLIC_RANKED),
            DominoMatchMode.entries.filter { mode ->
                mode.isEnabledInMvp && mode.usesPublicMatchmaking
            },
        )
    }
}
