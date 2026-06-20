package com.ahtohiofilho.dominopernambucano.domain

enum class RoundWinKind(
    val basePoints: Int,
) {
    COMMON(basePoints = 1),
    DOUBLE(basePoints = 2),
    LA_E_LO(basePoints = 3),
    CRUZADA(basePoints = 4),
    CLOSED(basePoints = 1),
    CLOSED_TIE(basePoints = 0),
}