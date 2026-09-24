package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoGameStateDto
import com.ahtohiofilho.dominopernambucano.online.OnlineDominoPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchPhaseTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpStatus
import java.time.Duration
import java.util.Locale
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

internal data class SyntheticDecisionTurnKey(
    val roundNumber: Int,
    val localSeatIndex: Int,
    val gameState: OnlineDominoGameStateDto,
)

internal data class SyntheticDecisionCadenceState(
    val turnKey: SyntheticDecisionTurnKey,
    val delayMillis: Long,
    val readyAtEpochMillis: Long,
)

internal data class SyntheticDecisionCadenceResolution(
    val state: SyntheticDecisionCadenceState?,
    val readyToAct: Boolean,
    val nextStepAtEpochMillis: Long?,
)

private const val MIN_SYNTHETIC_DECISION_DELAY_MILLIS = 1_000L
private const val MAX_SYNTHETIC_DECISION_DELAY_MILLIS = 4_000L

/*
 * Each synthetic identity has a stable personal center between 1.8 and 3.2 s.
 * Per logical turn, a triangular jitter of roughly +/- 0.8 s is applied.
 * Clamping keeps the final cadence within 1-4 s.
 *
 * The deterministic hash is deliberate: polling/recomposition cannot redraw a
 * new delay. A given identity + logical turn always resolves to the same value.
 */
internal fun resolveSyntheticPersonalityBaseDelayMillis(
    identityKey: String,
): Long {
    val normalizedIdentity = identityKey
        .trim()
        .uppercase(Locale.ROOT)

    val personalitySpreadMillis = 1_400
    val personalityOffsetMillis = Math.floorMod(
        normalizedIdentity.hashCode(),
        personalitySpreadMillis + 1,
    )

    return 1_800L + personalityOffsetMillis.toLong()
}

internal fun resolveSyntheticDecisionDelayMillis(
    identityKey: String,
    turnKey: SyntheticDecisionTurnKey,
): Long {
    val normalizedIdentity = identityKey
        .trim()
        .uppercase(Locale.ROOT)

    val seedPrefix =
        "$normalizedIdentity|" +
            "${turnKey.roundNumber}|" +
            "${turnKey.localSeatIndex}|" +
            turnKey.gameState.hashCode()

    /*
     * Average two uniform samples to concentrate most turns near the player's
     * personal center while still allowing occasional fast/slow decisions.
     */
    val sampleA = Math.floorMod(
        "$seedPrefix|A".hashCode(),
        1_601,
    )
    val sampleB = Math.floorMod(
        "$seedPrefix|B".hashCode(),
        1_601,
    )
    val triangularJitterMillis =
        ((sampleA + sampleB) / 2) - 800

    return (
        resolveSyntheticPersonalityBaseDelayMillis(
            identityKey = normalizedIdentity,
        ) + triangularJitterMillis.toLong()
    ).coerceIn(
        MIN_SYNTHETIC_DECISION_DELAY_MILLIS,
        MAX_SYNTHETIC_DECISION_DELAY_MILLIS,
    )
}

/*
 * Synthetic identities are externally controlled participants. The server
 * remains authoritative, while the external process owns both the decision
 * and its human-like cadence.
 *
 * This is scheduling, not sleeping: one synthetic waiting for its decision
 * window must never block the other population actors.
 *
 * The turn key intentionally excludes revision and clocks. Poll/ticker
 * revisions may change while the logical turn is still the same; only a
 * changed game state starts a fresh decision window and therefore a fresh
 * per-turn variation.
 */
internal fun resolveSyntheticDecisionCadence(
    currentState: SyntheticDecisionCadenceState?,
    snapshot: OnlineMatchSnapshotDto,
    localSeatIndex: Int,
    identityKey: String,
    nowEpochMillis: Long,
): SyntheticDecisionCadenceResolution {
    val isLocalSyntheticTurn =
        snapshot.phase.type ==
            OnlineMatchPhaseTypeDto.WAITING_FOR_LOCAL_MOVE &&
            snapshot.gameState.currentPlayerIndex == localSeatIndex

    if (!isLocalSyntheticTurn) {
        return SyntheticDecisionCadenceResolution(
            state = null,
            readyToAct = true,
            nextStepAtEpochMillis = null,
        )
    }

    val turnKey = SyntheticDecisionTurnKey(
        roundNumber = snapshot.roundNumber,
        localSeatIndex = localSeatIndex,
        gameState = snapshot.gameState,
    )

    val scheduledState =
        currentState
            ?.takeIf { state -> state.turnKey == turnKey }
            ?: resolveSyntheticDecisionDelayMillis(
                identityKey = identityKey,
                turnKey = turnKey,
            ).let { delayMillis ->
                SyntheticDecisionCadenceState(
                    turnKey = turnKey,
                    delayMillis = delayMillis,
                    readyAtEpochMillis =
                        nowEpochMillis + delayMillis,
                )
            }

    val readyToAct =
        nowEpochMillis >= scheduledState.readyAtEpochMillis

    return SyntheticDecisionCadenceResolution(
        state = scheduledState,
        readyToAct = readyToAct,
        nextStepAtEpochMillis =
            if (readyToAct) {
                null
            } else {
                scheduledState.readyAtEpochMillis
            },
    )
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
            val cadenceNextStepAtEpochMillis =
                if (player.matchId == null) {
                    enterQueue(player)
                    null
                } else {
                    advanceMatch(
                        player = player,
                        now = now,
                    )
                }

            liveness.recordSuccessfulInteraction(now)

            val nextDelayMillis = if (player.matchId == null) {
                config.standbyPollIntervalMillis
            } else {
                config.pollIntervalMillis
            }

            player.nextStepAtEpochMillis =
                cadenceNextStepAtEpochMillis
                    ?: (now + nextDelayMillis)
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
        now: Long,
    ): Long? {
        val matchId = requireNotNull(player.matchId)
        val snapshot = gateway.fetchMatchSnapshot(
            accessToken = player.credential.accessToken,
            matchId = matchId,
        )

        if (snapshot.phase.type == OnlineMatchPhaseTypeDto.MATCH_FINISHED) {
            player.completedMatches++
            player.clearMatch()
            return null
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

        val cadence = resolveSyntheticDecisionCadence(
            currentState = player.pendingDecisionCadence,
            snapshot = snapshot,
            localSeatIndex = localSeatIndex,
            identityKey = player.profile.tableCode,
            nowEpochMillis = now,
        )
        player.pendingDecisionCadence = cadence.state

        if (!cadence.readyToAct) {
            return requireNotNull(cadence.nextStepAtEpochMillis)
        }

        val action = policy.chooseAction(
            snapshot = snapshot,
            localSeatIndex = localSeatIndex,
            playerId = player.credential.playerId,
        ) ?: return null

        val result = gateway.submitAction(
            accessToken = player.credential.accessToken,
            action = action,
        )

        if (result.accepted) {
            acceptedActions++
            player.pendingDecisionCadence = null
        } else {
            rejectedActions++
        }

        return null
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
                completedMatchesByProfileIndex =
                    players.associate { player ->
                        player.profile.index to player.completedMatches
                    },
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
        var pendingDecisionCadence:
            SyntheticDecisionCadenceState? = null,
    ) {
        fun clearMatch() {
            matchId = null
            localSeatIndex = null
            standbyEnabled = false
            pendingDecisionCadence = null
        }
    }

    private companion object {
        val REPORT_INTERVAL: Duration = Duration.ofSeconds(30L)
    }
}
