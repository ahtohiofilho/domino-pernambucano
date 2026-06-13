package com.ahtohiofilho.dominopernambucano.domain

fun getTeamIndexForPlayer(
    playerIndex: Int,
): Int {
    return playerIndex % 2
}