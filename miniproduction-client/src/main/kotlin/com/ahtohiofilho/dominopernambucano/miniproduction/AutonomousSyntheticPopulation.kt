package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineDominoPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchPhaseTypeDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpStatus
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/*
 * The ranked HTTP boundary intentionally omits localSeatIndex. Synthetic
 * participants recover their seat from the participant-projected snapshot
 * using the same unique table code already used by SyntheticPlayerPolicy.
 */
internal fun resolveSyntheticLocalSeatIndex(
    players: List<OnlineDominoPlayerDto>,
    tableCode: String,
): Int? {
    val normalizedTableCode = tableCode.trim()
    if (normalizedTableCode.isBlank()) {
        return null
    }

    return players
        .mapIndexedNotNull { seatIndex, player ->
            seatIndex.takeIf {
                player.name.trim() == normalizedTableCode
            }
        }
        .singleOrNull()
}

internal class AutonomousSyntheticPopulation(
    private val config: MiniProductionClientConfig,
    private val gateway: MiniProductionGateway,
    private val profiles: List<SyntheticProfile>,
    credentials: List<SyntheticAccountCredential>,
    private val running: AtomicBoolean,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep,
    private val heartbeat: (Long) -> Unit = {},
) {
    init {
        require(profiles.size == credentials.size) {
            "Cada perfil sintético deve possuir uma credencial."
        }
    }

    private val policy = SyntheticPlayerPolicy(
        syntheticTableCodes = profiles.mapTo(mutableSetOf()) { profile ->
            profile.tableCode
        },
    )
    private val players = profiles.zip(credentials).map { (profile, credential) ->
        require(profile.index == credential.profileIndex)
        SyntheticPlayerRuntime(
            profile = profile,
            credential = credential,
        )
    }
    private var lastReportAtEpochMillis = 0L
    private var acceptedActions = 0L
    private var rejectedActions = 0L
    private var transientFailures = 0L
    private val liveness = SyntheticRuntimeLiveness(
        maxSilenceMillis = config.maxSilenceMillis,
        heartbeat = heartbeat,
    )

    fun awaitServerReadiness() {
        val deadline = System.nanoTime() + config.readinessTimeout.toNanos()

        while (running.get() && System.nanoTime() < deadline) {
            if (gateway.isReady()) {
                return
            }
            sleeper(500L)
        }

        check(running.get()) {
            "A população foi interrompida antes da prontidão do servidor."
        }
        throw IllegalStateException(
            "O servidor não ficou pronto em ${config.readinessTimeout.seconds}s.",
        )
    }

    fun run() {
        val startedAt = nowEpochMillis()
        liveness.start(startedAt)
        println(
            "POPULATION_STARTED accounts=${players.size} " +
                "standbyPool=${config.standbyPoolSize} " +
                "standbyPollMillis=${config.standbyPollIntervalMillis} " +
                "target=${config.runtimeTarget} " +
                "baseUrl=${config.normalizedBaseUrl}",
        )

        while (running.get()) {
            val now = nowEpochMillis()
            rebalanceStandby(now)
            players.forEach { player ->
                val eligibleForStep =
                    player.matchId != null || player.standbyEnabled
                if (
                    running.get() &&
                    eligibleForStep &&
                    now >= player.nextStepAtEpochMillis
                ) {
                    step(player, now)
                }
            }
            liveness.assertHealthy(now)
            reportIfNeeded(now)
            sleeper(50L)
        }

        println("POPULATION_STOPPED")
    }

    private fun step(
        player: SyntheticPlayerRuntime,
        now: Long,
    ) {
        try {
            if (player.matchId == null) {
                enterQueue(player)
            } else {
                advanceMatch(player)
            }
            liveness.recordSuccessfulInteraction(now)
            val nextDelayMillis = if (player.matchId == null) {
                config.standbyPollIntervalMillis
            } else {
                config.pollIntervalMillis
            }
            player.nextStepAtEpochMillis = now + nextDelayMillis
        } catch (failure: MiniProductionHttpException) {
            when {
                failure.statusCode == 404 && player.matchId != null -> {
                    player.clearMatch()
                    player.nextStepAtEpochMillis = now + 1_000L
                }

                failure.statusCode == 429 || failure.statusCode >= 500 -> {
                    transientFailures++
                    player.nextStepAtEpochMillis = now + 2_000L
                }

                else -> throw failure
            }
        } catch (failure: java.io.IOException) {
            transientFailures++
            player.nextStepAtEpochMillis = now + 2_000L
        }
    }

    private fun enterQueue(
        player: SyntheticPlayerRuntime,
    ) {
        val queue = gateway.enterRankedQueue(
            accessToken = player.credential.accessToken,
            tableCode = player.profile.tableCode,
        )

        when (queue.status) {
            PublicRankedQueueHttpStatus.MATCHED -> {
                player.matchId = requireNotNull(queue.matchId) {
                    "Resposta MATCHED sem matchId."
                }
                /*
                 * Production ranked HTTP deliberately does not expose the
                 * selected seat. Keep a seat only when a compatible gateway
                 * already supplied one; otherwise resolve it from the first
                 * participant-projected match snapshot.
                 */
                player.localSeatIndex = queue.localSeatIndex
                player.standbyEnabled = false
            }

            PublicRankedQueueHttpStatus.WAITING,
            PublicRankedQueueHttpStatus.NOT_QUEUED -> Unit
        }
    }

    private fun advanceMatch(
        player: SyntheticPlayerRuntime,
    ) {
        val matchId = requireNotNull(player.matchId)
        val snapshot = gateway.fetchMatchSnapshot(
            accessToken = player.credential.accessToken,
            matchId = matchId,
        )

        if (snapshot.phase.type == OnlineMatchPhaseTypeDto.MATCH_FINISHED) {
            player.completedMatches++
            player.clearMatch()
            return
        }

        val localSeatIndex = player.localSeatIndex
            ?: resolveSyntheticLocalSeatIndex(
                players = snapshot.gameState.players,
                tableCode = player.profile.tableCode,
            )
            ?: throw IllegalArgumentException(
                "Snapshot da partida não contém assento único para " +
                    player.profile.tableCode,
            )
        player.localSeatIndex = localSeatIndex

        val action = policy.chooseAction(
            snapshot = snapshot,
            localSeatIndex = localSeatIndex,
            playerId = player.credential.playerId,
        ) ?: return

        val result = gateway.submitAction(
            accessToken = player.credential.accessToken,
            action = action,
        )

        if (result.accepted) {
            acceptedActions++
        } else {
            rejectedActions++
        }
    }

    private fun rebalanceStandby(
        now: Long,
    ) {
        val matchedProfileIndexes = players
            .filter { player -> player.matchId != null }
            .mapTo(mutableSetOf()) { player -> player.profile.index }
        val currentStandbyProfileIndexes = players
            .filter { player ->
                player.matchId == null && player.standbyEnabled
            }
            .mapTo(mutableSetOf()) { player -> player.profile.index }
        val selectedStandbyProfileIndexes =
            selectSyntheticStandbyProfileIndexes(
                profileIndexesInPriorityOrder =
                    players.map { player -> player.profile.index },
                matchedProfileIndexes = matchedProfileIndexes,
                currentStandbyProfileIndexes =
                    currentStandbyProfileIndexes,
                targetSize = config.standbyPoolSize,
            )

        val newlyActivated = players.filter { player ->
            player.matchId == null &&
                !player.standbyEnabled &&
                player.profile.index in selectedStandbyProfileIndexes
        }
        val activationSpacingMillis = (
            config.standbyPollIntervalMillis /
                config.standbyPoolSize.toLong()
        ).coerceAtLeast(250L)

        newlyActivated.forEachIndexed { index, player ->
            player.standbyEnabled = true
            player.nextStepAtEpochMillis = maxOf(
                player.nextStepAtEpochMillis,
                now + index.toLong() * activationSpacingMillis,
            )
        }

        players.forEach { player ->
            if (
                player.matchId == null &&
                player.profile.index !in selectedStandbyProfileIndexes
            ) {
                player.standbyEnabled = false
            }
        }
    }

    private fun reportIfNeeded(
        now: Long,
    ) {
        if (now - lastReportAtEpochMillis < REPORT_INTERVAL.toMillis()) {
            return
        }
        lastReportAtEpochMillis = now
        liveness.recordHealthyHeartbeat(now)

        val activeMatches = players.mapNotNull { player -> player.matchId }
            .distinct()
            .size
        val matchedAccounts = players.count { player -> player.matchId != null }
        val standbyAccounts = players.count { player ->
            player.matchId == null && player.standbyEnabled
        }
        val dormantAccounts =
            players.size - matchedAccounts - standbyAccounts
        val completedMatches = players.sumOf { player -> player.completedMatches }

        println(
            "POPULATION_STATUS accounts=${players.size} " +
                "matchedAccounts=$matchedAccounts " +
                "activeMatches=$activeMatches " +
                "standbyAccounts=$standbyAccounts " +
                "dormantAccounts=$dormantAccounts " +
                "completedObservations=$completedMatches " +
                "acceptedActions=$acceptedActions " +
                "rejectedActions=$rejectedActions " +
                "transientFailures=$transientFailures",
        )
    }

    private data class SyntheticPlayerRuntime(
        val profile: SyntheticProfile,
        val credential: SyntheticAccountCredential,
        var matchId: String? = null,
        var localSeatIndex: Int? = null,
        var nextStepAtEpochMillis: Long = 0L,
        var completedMatches: Long = 0L,
        var standbyEnabled: Boolean = false,
    ) {
        fun clearMatch() {
            matchId = null
            localSeatIndex = null
            standbyEnabled = false
        }
    }

    private companion object {
        val REPORT_INTERVAL: Duration = Duration.ofSeconds(30L)
    }
}
