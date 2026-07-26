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
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankingFailureKind
import com.ahtohiofilho.dominopernambucano.online.PublicRankingCycleDto
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
        requestedOffset,
        requestNonce,
    ) {
        loading = true
        failure = null

        when (
            val result = rankingClient.fetch(
                cycle = selectedCycle,
                offset = requestedOffset,
                limit = PUBLIC_RANKING_PAGE_SIZE,
            )
        ) {
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
        entries = entries,
        latestResponse = latestResponse,
        failure = failure,
        loading = loading,
        onCycleSelected = { cycle ->
            if (cycle != selectedCycle) {
                selectedCycle = cycle
                entries = emptyList()
                latestResponse = null
                failure = null
                requestedOffset = 0
                requestNonce += 1
            }
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
    entries: List<PublicRankingEntryDto>,
    latestResponse: PublicRankingResponseDto?,
    failure: OnlinePublicRankingFailureKind?,
    loading: Boolean,
    onCycleSelected: (PublicRankingCycleDto) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
) {
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
                            )
                        },
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp),
            )

            latestResponse?.let { response ->
                Text(
                    text =
                        "${response.totalEligiblePlayers} jogadores elegíveis · " +
                            "${response.resultCount} partidas",
                    color = DominoSemanticColors.primaryTextOnDark
                        .copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

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
                        text = "Nenhum jogador elegível neste ciclo.",
                        color = DominoSemanticColors.primaryTextOnDark,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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
