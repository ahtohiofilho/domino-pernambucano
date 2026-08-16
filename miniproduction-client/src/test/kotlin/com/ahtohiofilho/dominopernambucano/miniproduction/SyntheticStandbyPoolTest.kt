package com.ahtohiofilho.dominopernambucano.miniproduction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticStandbyPoolTest {
    @Test
    fun idle_population_keeps_only_the_configured_standby_pool() {
        val selected = selectSyntheticStandbyProfileIndexes(
            profileIndexesInPriorityOrder = (1..16).toList(),
            matchedProfileIndexes = emptySet(),
            currentStandbyProfileIndexes = emptySet(),
            targetSize = 6,
        )

        assertEquals(setOf(1, 2, 3, 4, 5, 6), selected)
        assertEquals(6, selected.size)
        assertFalse(7 in selected)
    }

    @Test
    fun matched_player_is_replaced_by_a_dormant_profile() {
        val selected = selectSyntheticStandbyProfileIndexes(
            profileIndexesInPriorityOrder = (1..16).toList(),
            matchedProfileIndexes = setOf(1),
            currentStandbyProfileIndexes = setOf(1, 2, 3, 4, 5, 6),
            targetSize = 6,
        )

        assertEquals(setOf(2, 3, 4, 5, 6, 7), selected)
        assertFalse(1 in selected)
        assertTrue(7 in selected)
    }

    @Test
    fun returning_player_does_not_churn_a_full_existing_standby_pool() {
        val selected = selectSyntheticStandbyProfileIndexes(
            profileIndexesInPriorityOrder = (1..16).toList(),
            matchedProfileIndexes = emptySet(),
            currentStandbyProfileIndexes = setOf(2, 3, 4, 5, 6, 7),
            targetSize = 6,
        )

        assertEquals(setOf(2, 3, 4, 5, 6, 7), selected)
        assertFalse(1 in selected)
    }

    @Test
    fun standby_pool_shrinks_safely_when_most_profiles_are_in_matches() {
        val selected = selectSyntheticStandbyProfileIndexes(
            profileIndexesInPriorityOrder = (1..16).toList(),
            matchedProfileIndexes = (1..13).toSet(),
            currentStandbyProfileIndexes = setOf(14, 15),
            targetSize = 6,
        )

        assertEquals(setOf(14, 15, 16), selected)
    }
}
