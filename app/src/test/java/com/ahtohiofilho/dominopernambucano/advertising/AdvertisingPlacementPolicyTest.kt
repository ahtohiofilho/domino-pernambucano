package com.ahtohiofilho.dominopernambucano.advertising

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertisingPlacementPolicyTest {
    @Test
    fun banner_is_allowed_only_on_approved_g1_surfaces() {
        assertTrue(
            AdvertisingPlacementPolicy.allowsBanner(
                AdvertisingPlacement.RANKING_CURRENT,
            ),
        )
        assertTrue(
            AdvertisingPlacementPolicy.allowsBanner(
                AdvertisingPlacement.RANKING_HISTORY,
            ),
        )
        assertTrue(
            AdvertisingPlacementPolicy.allowsBanner(
                AdvertisingPlacement.RULES_HELP,
            ),
        )

        listOf(
            AdvertisingPlacement.GENERAL_STATISTICS,
            AdvertisingPlacement.MAIN_MENU,
            AdvertisingPlacement.TERMS_OF_USE,
            AdvertisingPlacement.SETTINGS,
            AdvertisingPlacement.MATCHMAKING,
            AdvertisingPlacement.GAMEPLAY,
            AdvertisingPlacement.POST_MATCH_RESULT,
            AdvertisingPlacement.LOGIN,
        ).forEach { placement ->
            assertFalse(
                AdvertisingPlacementPolicy.allowsBanner(placement),
            )
        }
    }
}
