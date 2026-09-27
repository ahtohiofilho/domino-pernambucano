package com.ahtohiofilho.dominopernambucano.ui.ranking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlineRankingAchievementClient
import com.ahtohiofilho.dominopernambucano.online.OnlineRankingAchievementClientResult
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAchievementDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAchievementGalleryResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingAwardTierDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandShapes
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val AchievementGalleryScreenTag =
    "achievement_gallery_screen"
internal const val AchievementGalleryListTag =
    "achievement_gallery_list"

private const val ACHIEVEMENT_GALLERY_PAGE_SIZE = 50

@Composable
fun OnlineRankingAchievementGalleryScreen(
    client: OnlineRankingAchievementClient,
    onBackClick: () -> Unit,
) {
    var achievements by remember {
        mutableStateOf(emptyList<PublicRankingAchievementDto>())
    }
    var latestResponse by remember {
        mutableStateOf<PublicRankingAchievementGalleryResponseDto?>(null)
    }
    var failure by remember {
        mutableStateOf<OnlinePublicRankingFailureKind?>(null)
    }
    var requestedOffset by remember {
        mutableIntStateOf(0)
    }
    var retryNonce by remember {
        mutableIntStateOf(0)
    }
    var loading by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(requestedOffset, retryNonce) {
        loading = true
        failure = null

        when (
            val result = client.fetch(
                offset = requestedOffset,
                limit = ACHIEVEMENT_GALLERY_PAGE_SIZE,
            )
        ) {
            is OnlineRankingAchievementClientResult.Success -> {
                latestResponse = result.response
                achievements =
                    if (requestedOffset == 0) {
                        result.response.achievements
                    } else {
                        mergeAchievements(
                            current = achievements,
                            incoming = result.response.achievements,
                        )
                    }
            }

            is OnlineRankingAchievementClientResult.Failure -> {
                failure = result.kind
            }
        }

        loading = false
    }

    DominoScreenScaffold(
        title = stringResource(
            R.string.achievement_gallery_title,
        ),
        layout = DominoScreenLayout.Top,
        modifier = Modifier.testTag(AchievementGalleryScreenTag),
        onBackClick = onBackClick,
        backContentDescription = stringResource(R.string.common_back),
        scrollable = false,
    ) {
        Text(
            text = stringResource(
                R.string.achievement_gallery_official_note,
            ),
            color =
                DominoSemanticColors.primaryTextOnDark.copy(
                    alpha = 0.82f,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )

        latestResponse?.let { response ->
            if (response.available) {
                Text(
                    text = stringResource(
                        R.string.achievement_gallery_summary,
                        response.totalAchievements,
                        response.totalAwardedPlayers,
                    ),
                    color = DominoSemanticColors.primaryTextOnDark,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (loading && achievements.isEmpty()) {
            CircularProgressIndicator()
        } else if (failure != null && achievements.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.sm,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = achievementGalleryFailureText(
                        failure = requireNotNull(failure),
                    ),
                    color = DominoSemanticColors.primaryTextOnDark,
                )
                Button(
                    onClick = {
                        retryNonce += 1
                    },
                ) {
                    Text(
                        text = stringResource(
                            R.string.achievement_gallery_retry,
                        ),
                    )
                }
            }
        } else if (
            !loading &&
            achievements.isEmpty()
        ) {
            Text(
                text = stringResource(
                    R.string.achievement_gallery_empty,
                ),
                color = DominoSemanticColors.primaryTextOnDark,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(AchievementGalleryListTag),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.sm,
                ),
            ) {
                items(
                    items = achievements,
                    key = { achievement ->
                        "${achievement.cycleId}:${achievement.competitorId}"
                    },
                ) { achievement ->
                    AchievementGalleryCard(
                        achievement = achievement,
                    )
                }

                if (failure != null) {
                    item(key = "failure") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = achievementGalleryFailureText(
                                    failure = requireNotNull(failure),
                                ),
                                color =
                                    DominoSemanticColors
                                        .primaryTextOnDark,
                            )
                            Button(
                                onClick = {
                                    retryNonce += 1
                                },
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string
                                            .achievement_gallery_retry,
                                    ),
                                )
                            }
                        }
                    }
                } else if (latestResponse?.hasMore == true) {
                    item(key = "load-more") {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            enabled = !loading,
                            onClick = {
                                val response =
                                    requireNotNull(latestResponse)
                                requestedOffset =
                                    response.offset +
                                    response.achievements.size
                            },
                        ) {
                            Text(
                                text = stringResource(
                                    R.string
                                        .achievement_gallery_load_more,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementGalleryCard(
    achievement: PublicRankingAchievementDto,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DominoBrandShapes.card,
        colors = CardDefaults.cardColors(
            containerColor =
                DominoSemanticColors.brandSurface.copy(
                    alpha = 0.96f,
                ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.dominoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.xs,
            ),
        ) {
            Text(
                text =
                    achievement.displayName
                        ?: stringResource(
                            R.string.achievement_gallery_player_fallback,
                        ),
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(
                    R.string.achievement_gallery_tier_and_rank,
                    achievementTierLabel(achievement.awardTier),
                    achievement.rank,
                ),
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = stringResource(
                    R.string.achievement_gallery_period,
                    achievementCycleLabel(achievement.cycle),
                    formatAchievementDate(
                        achievement.endsAtEpochMillis,
                    ),
                ),
                color = DominoSemanticColors.brandSupportingText,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(
                    R.string.achievement_gallery_player_counts,
                    achievement.diamondCount,
                    achievement.goldCount,
                    achievement.silverCount,
                    achievement.bronzeCount,
                ),
                color = DominoSemanticColors.brandSupportingText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun achievementTierLabel(
    tier: PublicRankingAwardTierDto,
): String {
    return when (tier) {
        PublicRankingAwardTierDto.DIAMOND ->
            stringResource(R.string.achievement_gallery_tier_diamond)

        PublicRankingAwardTierDto.GOLD ->
            stringResource(R.string.achievement_gallery_tier_gold)

        PublicRankingAwardTierDto.SILVER ->
            stringResource(R.string.achievement_gallery_tier_silver)

        PublicRankingAwardTierDto.BRONZE ->
            stringResource(R.string.achievement_gallery_tier_bronze)
    }
}

@Composable
private fun achievementCycleLabel(
    cycle: PublicRankingCycleDto,
): String {
    return when (cycle) {
        PublicRankingCycleDto.DAILY ->
            stringResource(R.string.achievement_gallery_cycle_daily)

        PublicRankingCycleDto.WEEKLY ->
            stringResource(R.string.achievement_gallery_cycle_weekly)

        PublicRankingCycleDto.MONTHLY ->
            stringResource(R.string.achievement_gallery_cycle_monthly)

        PublicRankingCycleDto.ANNUAL ->
            stringResource(R.string.achievement_gallery_cycle_annual)
    }
}

@Composable
private fun achievementGalleryFailureText(
    failure: OnlinePublicRankingFailureKind,
): String {
    return when (failure) {
        OnlinePublicRankingFailureKind.AUTHENTICATION_REQUIRED ->
            stringResource(
                R.string.achievement_gallery_session_required,
            )

        OnlinePublicRankingFailureKind.RATE_LIMITED,
        OnlinePublicRankingFailureKind.UNAVAILABLE ->
            stringResource(
                R.string.achievement_gallery_temporarily_unavailable,
            )

        OnlinePublicRankingFailureKind.PAGINATION_RESTART_REQUIRED,
        OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
        OnlinePublicRankingFailureKind.UNKNOWN ->
            stringResource(
                R.string.achievement_gallery_load_failed,
            )
    }
}

private fun formatAchievementDate(
    epochMillis: Long,
): String {
    return SimpleDateFormat(
        "dd/MM/yyyy",
        Locale.getDefault(),
    ).apply {
        timeZone = TimeZone.getTimeZone("America/Recife")
    }.format(Date(epochMillis))
}

internal fun mergeAchievements(
    current: List<PublicRankingAchievementDto>,
    incoming: List<PublicRankingAchievementDto>,
): List<PublicRankingAchievementDto> {
    val seen = linkedSetOf<Pair<String, String>>()

    return (current + incoming).filter { achievement ->
        seen.add(
            achievement.competitorId to achievement.cycleId,
        )
    }
}