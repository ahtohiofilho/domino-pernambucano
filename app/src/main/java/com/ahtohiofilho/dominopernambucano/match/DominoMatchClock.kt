package com.ahtohiofilho.dominopernambucano.match

fun createInitialPlayerClockMillis(
    playerCount: Int,
): List<Long> {
    return List(playerCount) {
        DominoMatchTiming.PlayerRoundTimeMillis
    }
}

fun decrementPlayerClockMillis(
    clocks: List<Long>,
    playerIndex: Int,
    elapsedMillis: Long,
): List<Long> {
    if (playerIndex !in clocks.indices) {
        return clocks
    }

    return clocks.mapIndexed { index, remainingMillis ->
        if (index == playerIndex) {
            (remainingMillis - elapsedMillis).coerceAtLeast(0L)
        } else {
            remainingMillis
        }
    }
}