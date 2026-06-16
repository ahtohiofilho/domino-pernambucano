package com.ahtohiofilho.dominopernambucano.match

sealed interface DominoMatchClockPolicy {
    val enabled: Boolean
    val playerRoundTimeMillis: Long

    data object Disabled : DominoMatchClockPolicy {
        override val enabled: Boolean = false
        override val playerRoundTimeMillis: Long = 0L
    }

    data object OnlinePerPlayerRound : DominoMatchClockPolicy {
        override val enabled: Boolean = true
        override val playerRoundTimeMillis: Long =
            DominoMatchTiming.OnlinePlayerRoundTimeMillis
    }
}