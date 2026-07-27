package com.ahtohiofilho.dominopernambucano.ui.ranking

import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class PublicRankingScope {
    CURRENT,
    CLOSED,
}

internal fun PublicRankingScope.publicLabel(): String {
    return when (this) {
        PublicRankingScope.CURRENT -> "Atual"
        PublicRankingScope.CLOSED -> "Encerrados"
    }
}

internal fun PublicRankingCycleSummaryDto.publicPeriodLabel(): String {
    return publicRankingPeriodLabel(
        cycle = cycle,
        startsAtEpochMillis = startsAtEpochMillis,
        endsAtEpochMillis = endsAtEpochMillis,
    )
}

internal fun PublicRankingResponseDto.publicContextLabel(): String {
    val context = if (isClosed) {
        "Ranking encerrado"
    } else {
        "Ranking atual"
    }

    return "$context · ${
        publicRankingPeriodLabel(
            cycle = cycle,
            startsAtEpochMillis = startsAtEpochMillis,
            endsAtEpochMillis = endsAtEpochMillis,
        )
    }"
}

internal fun PublicRankingResponseDto.publicSummaryLabel(): String {
    val base =
        "$totalEligiblePlayers jogadores elegíveis · " +
            "$resultCount partidas"

    return if (
        isClosed &&
        retainedRankingSize < totalEligiblePlayers
    ) {
        "$base · Top $retainedRankingSize preservado"
    } else {
        base
    }
}

internal fun PublicRankingResponseDto.publicRetentionNotice(): String? {
    return if (
        isClosed &&
        retainedRankingSize < totalEligiblePlayers
    ) {
        "Este histórico preserva somente o Top " +
            "$retainedRankingSize do período."
    } else {
        null
    }
}

internal fun mergeClosedRankingCycles(
    current: List<PublicRankingCycleSummaryDto>,
    incoming: List<PublicRankingCycleSummaryDto>,
): List<PublicRankingCycleSummaryDto> {
    return (current + incoming)
        .distinctBy { summary ->
            summary.cycleId
        }
        .sortedWith(
            compareByDescending<PublicRankingCycleSummaryDto> { summary ->
                summary.endsAtEpochMillis
            }.thenByDescending { summary ->
                summary.cycleId
            },
        )
}

private fun publicRankingPeriodLabel(
    cycle: PublicRankingCycleDto,
    startsAtEpochMillis: Long,
    endsAtEpochMillis: Long,
): String {
    val inclusiveEnd = (
        endsAtEpochMillis - 1L
    ).coerceAtLeast(startsAtEpochMillis)

    return when (cycle) {
        PublicRankingCycleDto.DAILY -> {
            formatRankingDate(
                epochMillis = startsAtEpochMillis,
                pattern = "dd/MM/yyyy",
            )
        }

        PublicRankingCycleDto.WEEKLY -> {
            val start = formatRankingDate(
                epochMillis = startsAtEpochMillis,
                pattern = "dd/MM",
            )
            val end = formatRankingDate(
                epochMillis = inclusiveEnd,
                pattern = "dd/MM/yyyy",
            )
            "$start a $end"
        }

        PublicRankingCycleDto.MONTHLY -> {
            formatRankingDate(
                epochMillis = startsAtEpochMillis,
                pattern = "MMMM 'de' yyyy",
            ).replaceFirstChar { character ->
                character.uppercaseChar()
            }
        }

        PublicRankingCycleDto.ANNUAL -> {
            formatRankingDate(
                epochMillis = startsAtEpochMillis,
                pattern = "yyyy",
            )
        }
    }
}

private fun formatRankingDate(
    epochMillis: Long,
    pattern: String,
): String {
    return SimpleDateFormat(
        pattern,
        Locale("pt", "BR"),
    ).apply {
        timeZone = RANKING_TIME_ZONE
    }.format(
        Date(epochMillis),
    )
}

private val RANKING_TIME_ZONE: TimeZone =
    TimeZone.getTimeZone("America/Recife")
