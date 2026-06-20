package com.ahtohiofilho.dominopernambucano.domain

fun getNextCounterClockwisePlayerIndex(
    currentPlayerIndex: Int,
    playerCount: Int,
): Int {
    return (currentPlayerIndex - 1 + playerCount) % playerCount
}