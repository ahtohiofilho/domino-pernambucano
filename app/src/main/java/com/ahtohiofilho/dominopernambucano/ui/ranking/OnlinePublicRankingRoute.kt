package com.ahtohiofilho.dominopernambucano.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingClient
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingCyclesClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingFailureKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleSummaryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCyclesResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingEntryDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankingResponseDto
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun OnlinePublicRankingRoute(
    rankingClient: OnlinePublicRankingClient,
    onBackClick: () -> Unit,
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
                failure = result.kind
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
            requestedOffset = entries.size
            requestNonce += 1
        },
        onRetry = {
            requestNonce += 1
        },
        onBackClick = onBackClick,
    )
}

@Composable
private fun OnlinePublicRankingScreen(
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
) {
    val hasRankingSelection =
        selectedScope == PublicRankingScope.CURRENT ||
            selectedClosedCycleId != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DominoSemanticColors.appBackground,
                        DominoSemanticColors.primaryAction,
                    ),
                ),
            )
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RankingHeader(
                onBackClick = onBackClick,
            )

            RankingCycleSelector(
                selectedCycle = selectedCycle,
                onCycleSelected = onCycleSelected,
            )

            Spacer(
                modifier = Modifier.height(8.dp),
            )

            RankingScopeSelector(
                selectedScope = selectedScope,
                onScopeSelected = onScopeSelected,
            )

            if (selectedScope == PublicRankingScope.CLOSED) {
                Spacer(
                    modifier = Modifier.height(8.dp),
                )

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

            Spacer(
                modifier = Modifier.height(12.dp),
            )

            latestResponse?.let { response ->
                Text(
                    text = response.publicContextLabel(),
                    color = DominoSemanticColors.primaryTextOnDark,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(
                    modifier = Modifier.height(4.dp),
                )

                Text(
                    text = response.publicSummaryLabel(),
                    color = DominoSemanticColors.primaryTextOnDark
                        .copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                response.publicRetentionNotice()?.let { notice ->
                    Spacer(
                        modifier = Modifier.height(4.dp),
                    )

                    Text(
                        text = notice,
                        color = DominoSemanticColors.primaryTextOnDark
                            .copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                response.viewer?.let { viewer ->
                    Spacer(
                        modifier = Modifier.height(8.dp),
                    )

                    RankingEntryCard(
                        entry = viewer,
                        heading = "Sua posição",
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp),
            )

            when {
                !hasRankingSelection -> {
                    Spacer(
                        modifier = Modifier.weight(1f),
                    )
                }

                failure != null && entries.isEmpty() -> {
                    RankingFailureContent(
                        failure = failure,
                        onRetry = onRetry,
                    )
                }

                loading && entries.isEmpty() -> {
                    CircularProgressIndicator()
                }

                !loading && entries.isEmpty() -> {
                    Text(
                        text = if (
                            selectedScope ==
                            PublicRankingScope.CLOSED
                        ) {
                            "Nenhum jogador foi preservado neste período."
                        } else {
                            "Nenhum jogador elegível neste ciclo."
                        },
                        color = DominoSemanticColors.primaryTextOnDark,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    RankingEntries(
                        modifier = Modifier.weight(1f),
                        entries = entries,
                        latestResponse = latestResponse,
                        failure = failure,
                        loading = loading,
                        onLoadMore = onLoadMore,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingHeader(
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBackClick,
        ) {
            Text(
                text = "Voltar",
                color = DominoSemanticColors.primaryTextOnDark,
            )
        }

        Text(
            text = "Ranking",
            modifier = Modifier.weight(1f),
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(
            modifier = Modifier.weight(0.22f),
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
            .horizontalScroll(
                rememberScrollState(),
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PublicRankingCycleDto.values().forEach { cycle ->
            FilterChip(
                selected = cycle == selectedCycle,
                onClick = {
                    onCycleSelected(cycle)
                },
                label = {
                    Text(
                        text = cycle.publicLabel(),
                        color = DominoSemanticColors.primaryTextOnDark,
                    )
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
            .horizontalScroll(
                rememberScrollState(),
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PublicRankingScope.values().forEach { scope ->
            FilterChip(
                selected = scope == selectedScope,
                onClick = {
                    onScopeSelected(scope)
                },
                label = {
                    Text(
                        text = scope.publicLabel(),
                        color = DominoSemanticColors.primaryTextOnDark,
                    )
                },
            )
        }
    }
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
    when {
        loading && cycles.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                )

                Text(
                    text = "Carregando períodos encerrados...",
                    color = DominoSemanticColors.primaryTextOnDark,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        failure != null && cycles.isEmpty() -> {
            RankingFailureContent(
                failure = failure,
                onRetry = onRetry,
            )
        }

        !loading && cycles.isEmpty() -> {
            Text(
                text = "Nenhum período encerrado está disponível.",
                color = DominoSemanticColors.primaryTextOnDark,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        else -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text =
                        "${latestResponse?.totalClosedCycles ?: cycles.size} " +
                            "períodos encerrados",
                    color = DominoSemanticColors.primaryTextOnDark
                        .copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState(),
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        summary.publicPeriodLabel(),
                                    color =
                                        DominoSemanticColors.primaryTextOnDark,
                                )
                            },
                        )
                    }

                    if (latestResponse?.hasMore == true) {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !loading,
                        ) {
                            Text(
                                text = "Mais períodos",
                            )
                        }
                    }

                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                failure?.let { currentFailure ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentFailure.publicMessage(),
                            modifier = Modifier.weight(1f),
                            color = DominoSemanticColors.primaryTextOnDark,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        TextButton(
                            onClick = onRetry,
                        ) {
                            Text(
                                text = "Tentar novamente",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingEntries(
    modifier: Modifier,
    entries: List<PublicRankingEntryDto>,
    latestResponse: PublicRankingResponseDto?,
    failure: OnlinePublicRankingFailureKind?,
    loading: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = entries,
            key = { entry ->
                entry.competitorId
            },
        ) { entry ->
            RankingEntryCard(
                entry = entry,
            )
        }

        if (latestResponse?.hasMore == true) {
            item {
                Button(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (loading) {
                            "Carregando..."
                        } else {
                            "Carregar mais"
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
    heading: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            heading?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = entry.publicTitle(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text =
                    "Pontuação ${entry.publicScoreText()} · " +
                        "${entry.victories} vitórias em ${entry.games} jogos",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text =
                    "Saldo ${entry.teamBalance} · " +
                        "Pontos ${entry.individualPoints} · " +
                        "Toques ${entry.touchesGiven} · " +
                        "Automáticas ${entry.automaticRounds}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RankingFailureContent(
    failure: OnlinePublicRankingFailureKind,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = failure.publicMessage(),
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onRetry,
        ) {
            Text(
                text = "Tentar novamente",
            )
        }
    }
}

internal fun PublicRankingCycleDto.publicLabel(): String {
    return when (this) {
        PublicRankingCycleDto.DAILY -> "Diário"
        PublicRankingCycleDto.WEEKLY -> "Semanal"
        PublicRankingCycleDto.MONTHLY -> "Mensal"
        PublicRankingCycleDto.ANNUAL -> "Anual"
    }
}

internal fun PublicRankingEntryDto.publicTitle(): String {
    val normalizedName = displayName
        ?.trim()
        ?.takeIf { value ->
            value.isNotBlank()
        }

    return "${rank}º · ${normalizedName ?: "Jogador $rank"}"
}

internal fun PublicRankingEntryDto.publicScoreText(): String {
    return "$scoreNumerator/$scoreDenominator"
}

internal fun OnlinePublicRankingFailureKind.publicMessage(): String {
    return when (this) {
        OnlinePublicRankingFailureKind.AUTHENTICATION_REQUIRED -> {
            "Não há uma sessão online válida para consultar o ranking."
        }

        OnlinePublicRankingFailureKind.RATE_LIMITED -> {
            "Muitas consultas foram feitas. Aguarde e tente novamente."
        }

        OnlinePublicRankingFailureKind.UNAVAILABLE -> {
            "O ranking está indisponível agora."
        }

        OnlinePublicRankingFailureKind.PROTOCOL_ERROR -> {
            "A resposta do ranking não pôde ser validada."
        }

        OnlinePublicRankingFailureKind.UNKNOWN -> {
            "Não foi possível carregar o ranking."
        }
    }
}

private const val PUBLIC_RANKING_PAGE_SIZE = 50
private const val PUBLIC_CLOSED_CYCLE_PAGE_SIZE = 20
