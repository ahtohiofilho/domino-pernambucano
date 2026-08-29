package com.ahtohiofilho.dominopernambucano.advertising

enum class AdvertisingPlacement {
    RANKING_CURRENT,
    RANKING_HISTORY,
    RULES_HELP,
    GENERAL_STATISTICS,
    MAIN_MENU,
    TERMS_OF_USE,
    SETTINGS,
    MATCHMAKING,
    GAMEPLAY,
    POST_MATCH_RESULT,
    LOGIN,
}

object AdvertisingPlacementPolicy {
    fun allowsBanner(
        placement: AdvertisingPlacement,
    ): Boolean {
        return when (placement) {
            AdvertisingPlacement.RANKING_CURRENT,
            AdvertisingPlacement.RANKING_HISTORY,
            AdvertisingPlacement.RULES_HELP,
            -> true

            AdvertisingPlacement.GENERAL_STATISTICS,
            AdvertisingPlacement.MAIN_MENU,
            AdvertisingPlacement.TERMS_OF_USE,
            AdvertisingPlacement.SETTINGS,
            AdvertisingPlacement.MATCHMAKING,
            AdvertisingPlacement.GAMEPLAY,
            AdvertisingPlacement.POST_MATCH_RESULT,
            AdvertisingPlacement.LOGIN,
            -> false
        }
    }
}
