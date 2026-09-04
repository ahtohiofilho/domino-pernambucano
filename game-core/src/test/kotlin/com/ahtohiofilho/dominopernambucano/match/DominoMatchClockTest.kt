package com.ahtohiofilho.dominopernambucano.match

import org.junit.Assert.assertEquals
import org.junit.Test

class DominoMatchClockTest {
    @Test
    fun reload_transfers_only_the_time_needed_to_fill_the_main_clock() {
        val result = reloadPlayerClockFromReserveMillis(
            clocks = listOf(8_000L, 18_000L),
            reserves = listOf(18_000L, 18_000L),
            playerIndex = 0,
            playerRoundTimeMillis = 18_000L,
        )

        assertEquals(listOf(18_000L, 18_000L), result.playerClockMillis)
        assertEquals(
            listOf(8_000L, 18_000L),
            result.playerClockReserveMillis,
        )
    }

    @Test
    fun reload_stops_when_the_reserve_is_smaller_than_the_missing_time() {
        val result = reloadPlayerClockFromReserveMillis(
            clocks = listOf(8_000L),
            reserves = listOf(5_000L),
            playerIndex = 0,
            playerRoundTimeMillis = 18_000L,
        )

        assertEquals(listOf(13_000L), result.playerClockMillis)
        assertEquals(listOf(0L), result.playerClockReserveMillis)
    }

    @Test
    fun online_clock_starts_with_twenty_seconds_and_twenty_seconds_of_reserve() {
        val clocks = createInitialPlayerClockMillis(
            playerCount = 2,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
        )
        val reserves = createInitialPlayerClockReserveMillis(
            playerCount = 2,
            clockPolicy = DominoMatchClockPolicy.OnlinePerPlayerRound,
        )

        assertEquals(listOf(20_000L, 20_000L), clocks)
        assertEquals(listOf(20_000L, 20_000L), reserves)
    }

    @Test
    fun reload_does_not_revive_an_expired_main_clock() {
        val result = reloadPlayerClockFromReserveMillis(
            clocks = listOf(0L),
            reserves = listOf(18_000L),
            playerIndex = 0,
            playerRoundTimeMillis = 18_000L,
        )

        assertEquals(listOf(0L), result.playerClockMillis)
        assertEquals(listOf(18_000L), result.playerClockReserveMillis)
    }
}
