package com.ahtohiofilho.dominopernambucano.ui.game

data class DominoRelativeSeatLayout(
    val localPlayerIndex: Int,
    val topPlayerIndex: Int,
    val leftPlayerIndex: Int,
    val rightPlayerIndex: Int,
)

fun calculateDominoRelativeSeatLayout(
    localPlayerIndex: Int,
    playerCount: Int,
): DominoRelativeSeatLayout {
    require(playerCount == 4) {
        "A mesa de dominó pernambucano exige exatamente quatro jogadores."
    }

    require(localPlayerIndex in 0 until playerCount) {
        "Índice local inválido: $localPlayerIndex."
    }

    return DominoRelativeSeatLayout(
        localPlayerIndex = localPlayerIndex,
        topPlayerIndex = relativePlayerIndex(
            localPlayerIndex = localPlayerIndex,
            offset = 2,
            playerCount = playerCount,
        ),
        leftPlayerIndex = relativePlayerIndex(
            localPlayerIndex = localPlayerIndex,
            offset = 1,
            playerCount = playerCount,
        ),
        rightPlayerIndex = relativePlayerIndex(
            localPlayerIndex = localPlayerIndex,
            offset = 3,
            playerCount = playerCount,
        ),
    )
}

private fun relativePlayerIndex(
    localPlayerIndex: Int,
    offset: Int,
    playerCount: Int,
): Int {
    return (localPlayerIndex + offset) % playerCount
}