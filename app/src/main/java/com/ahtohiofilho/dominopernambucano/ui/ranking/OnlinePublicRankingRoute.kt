package com.ahtohiofilho.dominopernambucano.ui.ranking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.advertising.AdvertisingPlacement
import com.ahtohiofilho.dominopernambucano.advertising.DominoBannerAd
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingClient
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingCyclesClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingFailureKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCyclesResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandShapes
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPatternBackground
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenTitle
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

internal const val RankingScreenTag = "ranking_screen"
internal const val RankingTitleTag = "ranking_title"
internal const val RankingCycleSelectorTag = "ranking_cycle_selector"
internal const val RankingScopeSelectorTag = "ranking_scope_selector"
internal const val RankingPublicationPendingTag = "ranking_publication_pending"

@Composable
fun OnlinePublicRankingRoute(
    rankingClient: OnlinePublicRankingClient,
    onBackClick: () -> Unit,
    bannerAdsReady: Boolean = false,
) {
    var selectedCycle by remember {
        mutableStateOf(PublicRankingCycleDto.DAILY)
    }
    var selectedScope by remember {
        mutableStateOf(PublicRankingScope.CURRENT)
    }

    var closedCycles by remember {
        mutableStateOf(emptyList<PublicRankingCycleSummaryDto>())
    }
    var latestCyclesResponse by remember {
        mutableStateOf<PublicRankingCyclesResponseDto?>(null)
    }
    var selectedClosedCycleId by remember {
        mutableStateOf<String?>(null)
    }
    var closedCyclesFailure by remember {
        mutableStateOf<OnlinePublicRankingFailureKind?>(null)
    }
    var closedCyclesOffset by remember {
        mutableIntStateOf(0)
    }
    var closedCyclesNonce by remember {
        mutableIntStateOf(0)
    }
    var closedCyclesLoading by remember {
        mutableStateOf(false)
    }

    var entries by remember {
        mutableStateOf(emptyList<PublicRankingEntryDto>())
    }
    var latestResponse by remember {
        mutableStateOf<PublicRankingResponseDto?>(null)
    }
    var failure by remember {
        mutableStateOf<OnlinePublicRankingFailureKind?>(null)
    }
    var requestedOffset by remember {
        mutableIntStateOf(0)
    }
    var requestNonce by remember {
        mutableIntStateOf(0)
    }
    var loading by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(
        selectedCycle,
        selectedScope,
        closedCyclesOffset,
        closedCyclesNonce,
    ) {
        if (selectedScope != PublicRankingScope.CLOSED) {
            closedCyclesLoading = false
            return@LaunchedEffect
        }

        closedCyclesLoading = true
        closedCyclesFailure = null

        val closedCyclesResult = try {
            rankingClient.fetchClosedCycles(
                cycle = selectedCycle,
                offset = closedCyclesOffset,
                limit = PUBLIC_CLOSED_CYCLE_PAGE_SIZE,
            )
        } catch (_: UnsupportedOperationException) {
            OnlinePublicRankingCyclesClientResult.Failure(
                kind = OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
                retryable = false,
            )
        }

        when (val result = closedCyclesResult) {
            is OnlinePublicRankingCyclesClientResult.Success -> {
                val response = result.response
                val updatedCycles = mergeClosedRankingCycles(
                    current = if (closedCyclesOffset == 0) {
                        emptyList()
                    } else {
                        closedCycles
                    },
                    incoming = response.cycles,
                )

                closedCycles = updatedCycles
                latestCyclesResponse = response

                if (
                    selectedClosedCycleId !in
                    updatedCycles.map { summary ->
                        summary.cycleId
                    }
                ) {
                    selectedClosedCycleId =
                        updatedCycles.firstOrNull()?.cycleId
                    entries = emptyList()
                    latestResponse = null
                    failure = null
                    requestedOffset = 0
                    requestNonce += 1
                }
            }

            is OnlinePublicRankingCyclesClientResult.Failure -> {
                closedCyclesFailure = result.kind
            }
        }

        closedCyclesLoading = false
    }

    LaunchedEffect(
        selectedCycle,
        selectedScope,
        selectedClosedCycleId,
        requestedOffset,
        requestNonce,
    ) {
        val historicalCycleId = selectedClosedCycleId
            .takeIf {
                selectedScope == PublicRankingScope.CLOSED
            }

        if (
            selectedScope == PublicRankingScope.CLOSED &&
            historicalCycleId == null
        ) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        failure = null

        val result = try {
            if (historicalCycleId == null) {
                rankingClient.fetch(
                    cycle = selectedCycle,
                    offset = requestedOffset,
                    limit = PUBLIC_RANKING_PAGE_SIZE,
                    rankingRevision = latestResponse
                        ?.rankingRevision
                        ?.takeIf { requestedOffset > 0 },
                )
            } else {
                rankingClient.fetchHistorical(
                    cycle = selectedCycle,
                    cycleId = historicalCycleId,
                    offset = requestedOffset,
                    limit = PUBLIC_RANKING_PAGE_SIZE,
                )
            }
        } catch (_: UnsupportedOperationException) {
            OnlinePublicRankingClientResult.Failure(
                kind = OnlinePublicRankingFailureKind.PROTOCOL_ERROR,
                retryable = false,
            )
        }

        when (result) {
            is OnlinePublicRankingClientResult.Success -> {
                val response = result.response

                entries = if (requestedOffset == 0) {
                    response.entries
                } else {
                    (entries + response.entries)
                        .distinctBy { entry ->
                            entry.competitorId
                        }
                }
                latestResponse = response
            }

            is OnlinePublicRankingClientResult.Failure -> {
                if (
                    shouldRestartRankingPagination(
                        failure = result.kind,
                        requestedOffset = requestedOffset,
                        selectedScope = selectedScope,
                    )
                ) {
                    entries = emptyList()
                    latestResponse = null
                    failure = null
                    requestedOffset = 0
                    requestNonce += 1
                } else {
                    failure = result.kind
                }
            }
        }

        loading = false
    }

    OnlinePublicRankingScreen(
        selectedCycle = selectedCycle,
        selectedScope = selectedScope,
        closedCycles = closedCycles,
        latestCyclesResponse = latestCyclesResponse,
        selectedClosedCycleId = selectedClosedCycleId,
        closedCyclesFailure = closedCyclesFailure,
        closedCyclesLoading = closedCyclesLoading,
        entries = entries,
        latestResponse = latestResponse,
        failure = failure,
        loading = loading,
        bannerAdsReady = bannerAdsReady,
        onCycleSelected = { cycle ->
            if (cycle != selectedCycle) {
                selectedCycle = cycle
                closedCycles = emptyList()
                latestCyclesResponse = null
                selectedClosedCycleId = null
                closedCyclesFailure = null
                closedCyclesOffset = 0
                closedCyclesNonce += 1
                entries = emptyList()
                latestResponse = null
                failure = null
                requestedOffset = 0
                requestNonce += 1
            }
        },
        onScopeSelected = { scope ->
            if (scope != selectedScope) {
                selectedScope = scope
                closedCycles = emptyList()
                latestCyclesResponse = null
                selectedClosedCycleId = null
                closedCyclesFailure = null
                closedCyclesOffset = 0
                closedCyclesNonce += 1
                entries = emptyList()
                latestResponse = null
                failure = null
                requestedOffset = 0
                requestNonce += 1
            }
        },
        onClosedCycleSelected = { cycleId ->
            if (cycleId != selectedClosedCycleId) {
                selectedClosedCycleId = cycleId
                entries = emptyList()
                latestResponse = null
                failure = null
                requestedOffset = 0
                requestNonce += 1
            }
        },
        onLoadMoreClosedCycles = {
            closedCyclesOffset = closedCycles.size
            closedCyclesNonce += 1
        },
        onRetryClosedCycles = {
            closedCyclesNonce += 1
        },
        onLoadMore = {
            requestedOffset = latestResponse
                ?.let { response ->
                    response.offset + response.entries.size
                }
                ?: entries.size
            requestNonce += 1
        },
        onRetry = {
            requestNonce += 1
        },
        onBackClick = onBackClick,
    )
}

internal fun shouldRestartRankingPagination(
    failure: OnlinePublicRankingFailureKind,
    requestedOffset: Int,
    selectedScope: PublicRankingScope,
): Boolean {
    return selectedScope == PublicRankingScope.CURRENT &&
        requestedOffset > 0 &&
        failure ==
        OnlinePublicRankingFailureKind.PAGINATION_RESTART_REQUIRED
}

@Composable
internal fun OnlinePublicRankingScreen(
    selectedCycle: PublicRankingCycleDto,
    selectedScope: PublicRankingScope,
    closedCycles: List<PublicRankingCycleSummaryDto>,
    latestCyclesResponse: PublicRankingCyclesResponseDto?,
    selectedClosedCycleId: String?,
    closedCyclesFailure: OnlinePublicRankingFailureKind?,
    closedCyclesLoading: Boolean,
    entries: List<PublicRankingEntryDto>,
    latestResponse: PublicRankingResponseDto?,
    failure: OnlinePublicRankingFailureKind?,
    loading: Boolean,
    onCycleSelected: (PublicRankingCycleDto) -> Unit,
    onScopeSelected: (PublicRankingScope) -> Unit,
    onClosedCycleSelected: (String) -> Unit,
    onLoadMoreClosedCycles: () -> Unit,
    onRetryClosedCycles: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    bannerAdsReady: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    val hasRankingSelection =
        selectedScope == PublicRankingScope.CURRENT ||
            selectedClosedCycleId != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DominoSemanticColors.brandBackground)
            .testTag(RankingScreenTag),
    ) {
        DominoPatternBackground(
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RankingHeader(
                onBackClick = onBackClick,
            )

            RankingCycleSelector(
                selectedCycle = selectedCycle,
                onCycleSelected = onCycleSelected,
            )

            RankingScopeSelector(
                selectedScope = selectedScope,
                onScopeSelected = onScopeSelected,
            )

            if (selectedScope == PublicRankingScope.CLOSED) {
                ClosedRankingCycleSelector(
                    cycles = closedCycles,
                    latestResponse = latestCyclesResponse,
                    selectedCycleId = selectedClosedCycleId,
                    failure = closedCyclesFailure,
                    loading = closedCyclesLoading,
                    onCycleSelected = onClosedCycleSelected,
                    onLoadMore = onLoadMoreClosedCycles,
                    onRetry = onRetryClosedCycles,
                )
            }

            latestResponse?.let { response ->
                RankingOverviewCard(
                    response = response,
                    locale = locale,
                )

                if (response.isOfficialRankingPending()) {
                    RankingPublicationPendingContent(
                        response = response,
                    )
                }

                val viewer = response.viewer
                val viewerAlreadyVisible = viewer?.let { currentViewer ->
                    entries.any { entry ->
                        entry.competitorId == currentViewer.competitorId
                    }
                } == true

                if (viewer != null && !viewerAlreadyVisible) {
                    RankingEntryCard(
                        entry = viewer,
                        locale = locale,
                        heading = stringResource(
                            R.string.ranking_your_position,
                        ),
                        highlighted = true,
                    )
                }
            }

            when {
                !hasRankingSelection -> {
                    Spacer(
                        modifier = Modifier.weight(1f),
                    )
                }

                failure != null && entries.isEmpty() -> {
                    RankingFailureContent(
                        modifier = Modifier.weight(1f),
                        failure = failure,
                        onRetry = onRetry,
                    )
                }

                loading && entries.isEmpty() -> {
                    RankingLoadingContent(
                        modifier = Modifier.weight(1f),
                    )
                }


                !loading && entries.isEmpty() -> {
                    RankingEmptyContent(
                        modifier = Modifier.weight(1f),
                        selectedScope = selectedScope,
                    )
                }

                else -> {
                    RankingEntries(
                        modifier = Modifier.weight(1f),
                        entries = entries,
                        locale = locale,
                        latestResponse = latestResponse,
                        viewerCompetitorId =
                            latestResponse?.viewer?.competitorId,
                        failure = failure,
                        loading = loading,
                        onLoadMore = onLoadMore,
                        onRetry = onRetry,
                    )
                }
            }

            DominoBannerAd(
                placement = if (
                    selectedScope == PublicRankingScope.CLOSED
                ) {
                    AdvertisingPlacement.RANKING_HISTORY
                } else {
                    AdvertisingPlacement.RANKING_CURRENT
                },
                adsReady = bannerAdsReady,
            )
        }
    }
}

@Composable
private fun RankingHeader(
    onBackClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onBackClick,
        ) {
            Text(
                text = stringResource(R.string.common_back),
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        DominoScreenTitle(
            text = stringResource(R.string.ranking_title),
            modifier = Modifier
                .testTag(RankingTitleTag),
        )
    }
}

@Composable
private fun RankingCycleSelector(
    selectedCycle: PublicRankingCycleDto,
    onCycleSelected: (PublicRankingCycleDto) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RankingCycleSelectorTag),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PublicRankingCycleDto.values().forEach { cycle ->
            RankingFilterChip(
                modifier = Modifier.weight(1f),
                selected = cycle == selectedCycle,
                label = stringResource(cycle.publicLabelRes()),
                onClick = {
                    onCycleSelected(cycle)
                },
            )
        }
    }
}

@Composable
private fun RankingScopeSelector(
    selectedScope: PublicRankingScope,
    onScopeSelected: (PublicRankingScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RankingScopeSelectorTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PublicRankingScope.values().forEach { scope ->
            RankingFilterChip(
                modifier = Modifier.weight(1f),
                selected = scope == selectedScope,
                label = stringResource(scope.publicLabelRes()),
                onClick = {
                    onScopeSelected(scope)
                },
            )
        }
    }
}

@Composable
private fun RankingFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        },
        shape = DominoBrandShapes.control,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = DominoSemanticColors.brandSurface,
            labelColor = DominoSemanticColors.brandText,
            selectedContainerColor = DominoSemanticColors.brandEnergy,
            selectedLabelColor = DominoSemanticColors.brandText,
        ),
    )
}

@Composable
private fun ClosedRankingCycleSelector(
    cycles: List<PublicRankingCycleSummaryDto>,
    latestResponse: PublicRankingCyclesResponseDto?,
    selectedCycleId: String?,
    failure: OnlinePublicRankingFailureKind?,
    loading: Boolean,
    onCycleSelected: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DominoBrandShapes.card,
        colors = CardDefaults.cardColors(
            containerColor =
                DominoSemanticColors.brandSurface.copy(alpha = 0.88f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = DominoSemanticColors.brandBorder,
        ),
    ) {
        when {
            loading && cycles.isEmpty() -> {
                RankingInlineLoading(
                    text = stringResource(
                        R.string.ranking_closed_loading,
                    ),
                )
            }

            failure != null && cycles.isEmpty() -> {
                RankingFailureContent(
                    modifier = Modifier.padding(14.dp),
                    failure = failure,
                    onRetry = onRetry,
                )
            }

            !loading && cycles.isEmpty() -> {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(
                        R.string.ranking_closed_empty,
                    ),
                    color = DominoSemanticColors.brandText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.ranking_closed_count,
                            latestResponse?.totalClosedCycles
                                ?: cycles.size,
                        ),
                        color =
                            DominoSemanticColors.brandSupportingText,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState(),
                            ),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        cycles.forEach { summary ->
                            FilterChip(
                                selected =
                                    summary.cycleId ==
                                        selectedCycleId,
                                onClick = {
                                    onCycleSelected(
                                        summary.cycleId,
                                    )
                                },
                                label = {
                                    Text(
                                        text =
                                            summary.publicPeriodLabel(
                                                locale = locale,
                                            ),
                                        maxLines = 1,
                                    )
                                },
                                shape = DominoBrandShapes.control,
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        containerColor =
                                            DominoSemanticColors
                                                .brandSurfaceElevated,
                                        labelColor =
                                            DominoSemanticColors.brandText,
                                        selectedContainerColor =
                                            DominoSemanticColors.brandEnergy,
                                        selectedLabelColor =
                                            DominoSemanticColors.brandText,
                                    ),
                            )
                        }

                        if (latestResponse?.hasMore == true) {
                            TextButton(
                                onClick = onLoadMore,
                                enabled = !loading,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.ranking_more_periods,
                                    ),
                                    color =
                                        DominoSemanticColors.brandText,
                                )
                            }
                        }

                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color =
                                    DominoSemanticColors.brandText,
                                strokeWidth = 2.dp,
                            )
                        }
                    }

                    failure?.let { currentFailure ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    currentFailure.publicMessageRes(),
                                ),
                                modifier = Modifier.weight(1f),
                                color =
                                    DominoSemanticColors.brandText,
                                style =
                                    MaterialTheme.typography.bodySmall,
                            )

                            TextButton(
                                onClick = onRetry,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.ranking_retry,
                                    ),
                                    color =
                                        DominoSemanticColors.brandText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingOverviewCard(
    response: PublicRankingResponseDto,
    locale: java.util.Locale,
) {
    val period = response.publicPeriodLabel(
        locale = locale,
    )
    val contextText = stringResource(
        if (response.isClosed) {
            R.string.ranking_context_closed
        } else {
            R.string.ranking_context_current
        },
        period,
    )
    val summaryText = stringResource(
        R.string.ranking_summary,
        response.totalEligiblePlayers,
        response.resultCount,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DominoBrandShapes.card,
        colors = CardDefaults.cardColors(
            containerColor =
                DominoSemanticColors.brandSurfaceElevated.copy(
                    alpha = 0.96f,
                ),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = DominoSemanticColors.brandEnergy.copy(
                alpha = 0.72f,
            ),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = contextText,
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = summaryText,
                color = DominoSemanticColors.brandSupportingText,
                style = MaterialTheme.typography.bodyMedium,
            )

        }
    }
}

@Composable
private fun RankingPublicationPendingContent(
    response: PublicRankingResponseDto,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        if (response.isClosed) {
            R.string.ranking_publication_pending_closed_title
        } else {
            R.string.ranking_publication_pending_current_title
        },
    )
    val progress = stringResource(
        if (response.isClosed) {
            R.string.ranking_publication_pending_closed_progress
        } else {
            R.string.ranking_publication_pending_current_progress
        },
        response.totalEligiblePlayers,
        response.publicationThreshold,
        response.eligiblePlayersRemaining,
    )
    val notice = stringResource(
        if (response.isClosed) {
            R.string.ranking_publication_pending_closed_notice
        } else {
            R.string.ranking_publication_pending_current_notice
        },
    )

    RankingStateCard(
        modifier = modifier.testTag(
            RankingPublicationPendingTag,
        ),
    ) {
        Text(
            text = title,
            color = DominoSemanticColors.brandText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text = progress,
            color = DominoSemanticColors.brandText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Text(
            text = notice,
            color = DominoSemanticColors.brandSupportingText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RankingEntries(
    modifier: Modifier,
    entries: List<PublicRankingEntryDto>,
    locale: java.util.Locale,
    latestResponse: PublicRankingResponseDto?,
    viewerCompetitorId: String?,
    failure: OnlinePublicRankingFailureKind?,
    loading: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = entries,
            key = { entry ->
                entry.competitorId
            },
        ) { entry ->
            val highlighted =
                entry.competitorId == viewerCompetitorId

            RankingEntryCard(
                entry = entry,
                locale = locale,
                heading = if (highlighted) {
                    stringResource(
                        R.string.ranking_your_position,
                    )
                } else {
                    null
                },
                highlighted = highlighted,
            )
        }

        if (latestResponse?.hasMore == true) {
            item {
                Button(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = DominoBrandShapes.control,
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            DominoSemanticColors.brandPrimaryAction,
                        contentColor =
                            DominoSemanticColors
                                .brandPrimaryActionContent,
                    ),
                ) {
                    Text(
                        text = if (loading) {
                            stringResource(
                                R.string.ranking_loading,
                            )
                        } else {
                            stringResource(
                                R.string.ranking_load_more,
                            )
                        },
                    )
                }
            }
        }

        failure?.let { currentFailure ->
            item {
                RankingFailureContent(
                    failure = currentFailure,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun RankingEntryCard(
    entry: PublicRankingEntryDto,
    locale: java.util.Locale,
    heading: String? = null,
    highlighted: Boolean = false,
) {
    val normalizedName = entry.publicDisplayName(
        fallback = stringResource(
            R.string.ranking_player_fallback,
            entry.rank,
        ),
    )
    val containerColor = if (highlighted) {
        DominoSemanticColors.brandPositive.copy(alpha = 0.34f)
    } else {
        DominoSemanticColors.brandSurface.copy(alpha = 0.96f)
    }
    val borderColor = if (highlighted) {
        DominoSemanticColors.brandPositive
    } else {
        DominoSemanticColors.brandBorder
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DominoBrandShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RankingPositionBadge(
                rank = entry.rank,
                label = entry.publicRankLabel(locale),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                heading?.let { text ->
                    Text(
                        text = text,
                        color = DominoSemanticColors.brandText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = normalizedName,
                    color = DominoSemanticColors.brandText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = stringResource(
                        R.string.ranking_score_summary,
                        entry.publicScoreText(),
                        entry.victories,
                        entry.games,
                    ),
                    color =
                        DominoSemanticColors.brandSupportingText,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = stringResource(
                        R.string.ranking_metrics_summary,
                        entry.teamBalance,
                        entry.individualPoints,
                        entry.touchesGiven,
                        entry.automaticRounds,
                    ),
                    color =
                        DominoSemanticColors.brandSupportingText.copy(
                            alpha = 0.88f,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RankingPositionBadge(
    rank: Int,
    label: String,
) {
    val backgroundColor = when (rank) {
        1 -> DominoColorTokens.AccentYellow
        2 -> DominoColorTokens.PureWhite.copy(alpha = 0.24f)
        3 -> DominoColorTokens.AccentRed.copy(alpha = 0.92f)
        else -> DominoSemanticColors.brandSurfaceElevated
    }
    val contentColor = if (rank == 1) {
        DominoColorTokens.InkBlue
    } else {
        DominoSemanticColors.brandText
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun RankingFailureContent(
    failure: OnlinePublicRankingFailureKind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RankingStateCard(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                failure.publicMessageRes(),
            ),
            color = DominoSemanticColors.brandText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onRetry,
            shape = DominoBrandShapes.control,
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    DominoSemanticColors.brandPrimaryAction,
                contentColor =
                    DominoSemanticColors
                        .brandPrimaryActionContent,
            ),
        ) {
            Text(
                text = stringResource(
                    R.string.ranking_retry,
                ),
            )
        }
    }
}

@Composable
private fun RankingLoadingContent(
    modifier: Modifier = Modifier,
) {
    RankingStateCard(
        modifier = modifier,
    ) {
        CircularProgressIndicator(
            color = DominoSemanticColors.brandText,
        )

        Text(
            text = stringResource(
                R.string.ranking_loading,
            ),
            color = DominoSemanticColors.brandText,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun RankingInlineLoading(
    text: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = DominoSemanticColors.brandText,
            strokeWidth = 2.dp,
        )

        Text(
            text = text,
            color = DominoSemanticColors.brandText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RankingEmptyContent(
    selectedScope: PublicRankingScope,
    modifier: Modifier = Modifier,
) {
    RankingStateCard(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                if (selectedScope == PublicRankingScope.CLOSED) {
                    R.string.ranking_empty_closed
                } else {
                    R.string.ranking_empty_current
                },
            ),
            color = DominoSemanticColors.brandText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RankingStateCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DominoBrandShapes.card,
        colors = CardDefaults.cardColors(
            containerColor =
                DominoSemanticColors.brandSurface.copy(alpha = 0.92f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = DominoSemanticColors.brandBorder,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

internal fun PublicRankingCycleDto.publicLabelRes(): Int {
    return when (this) {
        PublicRankingCycleDto.DAILY ->
            R.string.ranking_cycle_daily
        PublicRankingCycleDto.WEEKLY ->
            R.string.ranking_cycle_weekly
        PublicRankingCycleDto.MONTHLY ->
            R.string.ranking_cycle_monthly
        PublicRankingCycleDto.ANNUAL ->
            R.string.ranking_cycle_annual
    }
}

internal fun PublicRankingScope.publicLabelRes(): Int {
    return when (this) {
        PublicRankingScope.CURRENT ->
            R.string.ranking_scope_current
        PublicRankingScope.CLOSED ->
            R.string.ranking_scope_closed
    }
}

internal fun OnlinePublicRankingFailureKind.publicMessageRes(): Int {
    return when (this) {
        OnlinePublicRankingFailureKind.AUTHENTICATION_REQUIRED ->
            R.string.ranking_failure_authentication_required
        OnlinePublicRankingFailureKind.RATE_LIMITED ->
            R.string.ranking_failure_rate_limited
        OnlinePublicRankingFailureKind.PAGINATION_RESTART_REQUIRED ->
            R.string.ranking_failure_protocol_error
        OnlinePublicRankingFailureKind.UNAVAILABLE ->
            R.string.ranking_failure_unavailable
        OnlinePublicRankingFailureKind.PROTOCOL_ERROR ->
            R.string.ranking_failure_protocol_error
        OnlinePublicRankingFailureKind.UNKNOWN ->
            R.string.ranking_failure_unknown
    }
}

private const val PUBLIC_RANKING_PAGE_SIZE = 50
private const val PUBLIC_CLOSED_CYCLE_PAGE_SIZE = 20
