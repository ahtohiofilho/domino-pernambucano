package com.ahtohiofilho.dominopernambucano.domain

data class DominoPlayer(
    val id: Int,
    val name: String,
    val hand: List<DominoPiece>,
    val participantType: DominoParticipantType =
        DominoParticipantType.HUMAN,
)