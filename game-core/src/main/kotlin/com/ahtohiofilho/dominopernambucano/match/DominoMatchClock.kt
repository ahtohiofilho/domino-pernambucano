package com.ahtohiofilho.dominopernambucano.match

fun createInitialPlayerClockMillis(
    playerCount: Int,
    clockPolicy: DominoMatchClockPolicy,
): List<Long> {
    if (!clockPolicy.enabled) {
        return emptyList()
    }

    return List(playerCount) {
        clockPolicy.playerRoundTimeMillis
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

fun isPlayerClockExpired(
    clocks: List<Long>,
    playerIndex: Int,
): Boolean {
    val remainingMillis = clocks.getOrNull(playerIndex) ?: return false

    return remainingMillis <= 0L
}