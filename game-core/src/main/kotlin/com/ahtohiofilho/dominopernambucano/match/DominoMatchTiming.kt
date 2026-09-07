package com.ahtohiofilho.dominopernambucano.match

object DominoMatchTiming {
    /* Teto do relógio ativo em cada jogada manual. */
    const val OnlinePlayerRoundTimeMillis = 20_000L

    /* Reserva entregue a cada jogador no início de toda rodada. */
    const val OnlinePlayerRoundReserveMillis = 20_000L
    const val ClockTickMillis = 250L
    const val BotDecisionDelayMillis = 2_000L

    /*
     * A animacao visual normal termina antes deste teto.
     * O fallback existe apenas para clientes antigos/desconectados.
     */
    const val RoundIntroServerFallbackMillis = 4_000L

    const val RoundSummaryAutoAdvanceMillis = 3_000L
}