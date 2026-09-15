package com.ahtohiofilho.dominopernambucano.ui.ranking

import com.ahtohiofilho.dominopernambucano.competitive.RANKING_RULE_VERSION_V2
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingPublicationStatusDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class PublicRankingScope {
    CURRENT,
    CLOSED,
}

internal fun PublicRankingCycleSummaryDto.publicPeriodLabel(
    locale: Locale,
): String {
    return publicRankingPeriodLabel(
        cycle = cycle,
        startsAtEpochMillis = startsAtEpochMillis,
        endsAtEpochMillis = endsAtEpochMillis,
        locale = locale,
    )
}

internal fun PublicRankingResponseDto.publicPeriodLabel(
    locale: Locale,
): String {
    return publicRankingPeriodLabel(
        cycle = cycle,
        startsAtEpochMillis = startsAtEpochMillis,
        endsAtEpochMillis = endsAtEpochMillis,
        locale = locale,
    )
}

internal fun PublicRankingEntryDto.publicRankLabel(
    locale: Locale,
): String {
    return when {
        locale.language.equals("en", ignoreCase = true) ->
            "#$rank"
        locale.language.equals("pt", ignoreCase = true) ->
            "${rank}\u00AA"
        else ->
            "${rank}\u00BA"
    }
}
internal fun PublicRankingEntryDto.publicScoreText(): String {
    return "$scoreNumerator/$scoreDenominator"
}
internal fun PublicRankingEntryDto.publicDecimalScoreText(): String {
    if (scoreDenominator == 0L) {
        return "0.000"
    }

    return String.format(
        Locale.ROOT,
        "%.3f",
        scoreNumerator.toDouble() / scoreDenominator.toDouble(),
    )
}

internal fun PublicRankingResponseDto.usesV2RankingPresentation(): Boolean {
    return rankingRuleVersion == RANKING_RULE_VERSION_V2
}

internal fun PublicRankingEntryDto.publicVictoryBalance(): Long {
    return Math.subtractExact(
        Math.multiplyExact(victories, 2L),
        games,
    )
}

internal fun PublicRankingEntryDto.publicDefeats(): Long {
    return Math.subtractExact(games, victories)
}

internal enum class PublicRankingV2TiebreakCriterion {
    ASSISTS,
    TOUCHES,
    AUTOMATIC_PLAYS,
}

internal fun publicV2TiebreakCriterion(
    previous: PublicRankingEntryDto?,
    current: PublicRankingEntryDto,
): PublicRankingV2TiebreakCriterion? {
    previous ?: return null

    if (
        previous.publicVictoryBalance() !=
            current.publicVictoryBalance() ||
        previous.teamBalance != current.teamBalance ||
        previous.individualPoints != current.individualPoints
    ) {
        return null
    }

    return when {
        previous.assists != current.assists ->
            PublicRankingV2TiebreakCriterion.ASSISTS

        previous.touchesGiven != current.touchesGiven ->
            PublicRankingV2TiebreakCriterion.TOUCHES

        previous.automaticPlays != current.automaticPlays ->
            PublicRankingV2TiebreakCriterion.AUTOMATIC_PLAYS

        else -> null
    }
}

internal fun PublicRankingEntryDto.publicDisplayName(
    fallback: String,
): String {
    return displayName
        ?.trim()
        ?.takeIf { value ->
            value.isNotBlank()
        }
        ?: fallback
}

internal fun PublicRankingEntryDto.publicAvatarMonogram(
    fallback: String,
): String {
    val compact = publicDisplayName(fallback)
        .filter { character ->
            character.isLetterOrDigit()
        }

    return compact
        .take(3)
        .uppercase(Locale.ROOT)
        .ifBlank { "?" }
}
internal fun PublicRankingResponseDto.isOfficialRankingPending(): Boolean {
    return publicationStatus ==
        PublicRankingPublicationStatusDto.BELOW_THRESHOLD
}

internal fun PublicRankingResponseDto.isOfficialRankingPublished(): Boolean {
    return publicationStatus ==
        PublicRankingPublicationStatusDto.PUBLISHED
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
    locale: Locale,
): String {
    val inclusiveEnd = (
        endsAtEpochMillis - 1L
    ).coerceAtLeast(startsAtEpochMillis)

    return when (cycle) {
        PublicRankingCycleDto.DAILY -> {
            formatRankingDate(
                epochMillis = startsAtEpochMillis,
                locale = locale,
                style = DateFormat.MEDIUM,
            )
        }

        PublicRankingCycleDto.WEEKLY -> {
            val start = formatRankingDate(
                epochMillis = startsAtEpochMillis,
                locale = locale,
                style = DateFormat.SHORT,
            )
            val end = formatRankingDate(
                epochMillis = inclusiveEnd,
                locale = locale,
                style = DateFormat.MEDIUM,
            )
            "$start – $end"
        }

        PublicRankingCycleDto.MONTHLY -> {
            SimpleDateFormat(
                "MMMM yyyy",
                locale,
            ).apply {
                timeZone = RANKING_TIME_ZONE
            }.format(
                Date(startsAtEpochMillis),
            ).replaceFirstChar { character ->
                character.uppercaseChar()
            }
        }

        PublicRankingCycleDto.ANNUAL -> {
            SimpleDateFormat(
                "yyyy",
                locale,
            ).apply {
                timeZone = RANKING_TIME_ZONE
            }.format(
                Date(startsAtEpochMillis),
            )
        }
    }
}

private fun formatRankingDate(
    epochMillis: Long,
    locale: Locale,
    style: Int,
): String {
    return DateFormat.getDateInstance(
        style,
        locale,
    ).apply {
        timeZone = RANKING_TIME_ZONE
    }.format(
        Date(epochMillis),
    )
}

private val RANKING_TIME_ZONE: TimeZone =
    TimeZone.getTimeZone("America/Recife")
