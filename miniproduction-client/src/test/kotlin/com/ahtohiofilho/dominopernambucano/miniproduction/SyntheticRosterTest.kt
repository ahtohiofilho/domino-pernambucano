package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.isValidOnlineAccountTableCode
import com.ahtohiofilho.dominopernambucano.online.normalizeOnlinePublicDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticRosterTest {
    @Test
    fun roster_is_realistic_valid_and_unique() {
        assertEquals(60, syntheticRoster.size)
        assertEquals(
            DEFAULT_SYNTHETIC_POPULATION_SIZE,
            syntheticRoster.size,
        )
        assertEquals(
            syntheticRoster.size,
            syntheticRoster.map { profile -> profile.index }.distinct().size,
        )
        assertEquals(
            syntheticRoster.size,
            syntheticRoster.map { profile -> profile.publicDisplayName }
                .distinct().size,
        )
        assertEquals(
            syntheticRoster.size,
            syntheticRoster.map { profile -> profile.tableCode }.distinct().size,
        )

        syntheticRoster.forEach { profile ->
            assertEquals(
                profile.publicDisplayName,
                normalizeOnlinePublicDisplayName(profile.publicDisplayName),
            )
            assertTrue(isValidOnlineAccountTableCode(profile.tableCode))
        }
    }
}
