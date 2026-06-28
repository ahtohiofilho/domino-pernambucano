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

fun createInitialPlayerClockReserveMillis(
    playerCount: Int,
    clockPolicy: DominoMatchClockPolicy,
): List<Long> {
    if (!clockPolicy.enabled) {
        return emptyList()
    }

    return List(playerCount) {
        clockPolicy.playerRoundReserveMillis
    }
}

data class PlayerClockReloadResult(
    val playerClockMillis: List<Long>,
    val playerClockReserveMillis: List<Long>,
)

fun reloadPlayerClockFromReserveMillis(
    clocks: List<Long>,
    reserves: List<Long>,
    playerIndex: Int,
    playerRoundTimeMillis: Long,
): PlayerClockReloadResult {
    if (
        playerIndex !in clocks.indices ||
        playerIndex !in reserves.indices ||
        playerRoundTimeMillis <= 0L
    ) {
        return PlayerClockReloadResult(
            playerClockMillis = clocks,
            playerClockReserveMillis = reserves,
        )
    }

    val currentClockMillis = clocks[playerIndex]

    /*
     * O estouro do relógio principal entra no fluxo automático antes de
     * qualquer ação humana ser aceita. Esta guarda impede que uma chamada
     * tardia ressuscite um relógio já expirado usando a reserva.
     */
    if (currentClockMillis <= 0L) {
        return PlayerClockReloadResult(
            playerClockMillis = clocks,
            playerClockReserveMillis = reserves,
        )
    }

    val missingMillis = (playerRoundTimeMillis - currentClockMillis)
        .coerceAtLeast(0L)
    val availableReserveMillis = reserves[playerIndex].coerceAtLeast(0L)
    val transferredMillis = minOf(
        missingMillis,
        availableReserveMillis,
    )

    if (transferredMillis == 0L) {
        return PlayerClockReloadResult(
            playerClockMillis = clocks,
            playerClockReserveMillis = reserves,
        )
    }

    return PlayerClockReloadResult(
        playerClockMillis = clocks.mapIndexed { index, remainingMillis ->
            if (index == playerIndex) {
                remainingMillis + transferredMillis
            } else {
                remainingMillis
            }
        },
        playerClockReserveMillis = reserves.mapIndexed { index, remainingMillis ->
            if (index == playerIndex) {
                (remainingMillis - transferredMillis).coerceAtLeast(0L)
            } else {
                remainingMillis
            }
        },
    )
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