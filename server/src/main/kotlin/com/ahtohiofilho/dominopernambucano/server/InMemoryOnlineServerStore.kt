package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankedCyclePeriod
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.competitive.buildRankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycle
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycles
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchMetricAccumulator
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerIdentity
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.accumulateRankedMatchTransition
import com.ahtohiofilho.dominopernambucano.competitive.buildRankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.competitive.didRankedSeatPlayPiece
import com.ahtohiofilho.dominopernambucano.domain.DominoGameState
import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.domain.isGameFinished
import com.ahtohiofilho.dominopernambucano.domain.isRoundFinished
import com.ahtohiofilho.dominopernambucano.domain.passTurn
import com.ahtohiofilho.dominopernambucano.domain.playMoveForCurrentPlayer
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.match.createInitialPlayerClockMillis
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.match.findRandomPlayableMove
import com.ahtohiofilho.dominopernambucano.match.isPlayerClockExpired
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfile
import com.ahtohiofilho.dominopernambucano.online.createOnlineAccountProfile
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchActionReduction
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.projectForParticipant
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.online.applyOnlineRoomPlayerNames
import com.ahtohiofilho.dominopernambucano.online.determineOnlineNextPhase
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceContext
import com.ahtohiofilho.dominopernambucano.online.observability.createOnlineTraceStateFingerprint
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLevel
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceSource
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceStateSummary
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceType
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineAuthoritativeClock
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineGameAction
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineStartNewMatchAction
import com.ahtohiofilho.dominopernambucano.online.reduceOnlineStartNextRoundAction
import com.ahtohiofilho.dominopernambucano.online.toOnlineSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState

private const val DEVELOPMENT_BOT_PLAYER_ID_PREFIX =
    "development-bot-seat-"

private const val FIRST_DEVELOPMENT_BOT_SEAT_INDEX = 2
private const val LAST_DEVELOPMENT_BOT_SEAT_INDEX = 3

/*
 * O store de desenvolvimento preserva uma janela de revisÃµes por partida para
 * que o cliente apresente cada transiÃ§Ã£o, em vez de pular ao snapshot atual.
 */
private const val MATCH_REVISION_HISTORY_CAPACITY = 2_048
private const val MAX_SERVER_IDENTIFIER_CHARACTERS = 160
private const val MAX_SERVER_PLAYER_NAME_CHARACTERS = 160
private const val MAX_SERVER_ROOM_CODE_CHARACTERS = 16

class InMemoryOnlineServerStore(
    private val clockPolicy: DominoMatchClockPolicy =
        DominoMatchClockPolicy.OnlinePerPlayerRound,
    private val autoFillDevelopmentBotsAfterTwoHumanPlayers: Boolean = false,
    private val resourcePolicy: OnlineServerStoreResourcePolicy =
        OnlineServerStoreResourcePolicy.Default,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val traceLogger: OnlineTraceLogger = OnlineTraceLogger(
        nowEpochMillis = nowEpochMillis,
    ),
    private val accountIdFactory: () -> String = {
        "account-${java.util.UUID.randomUUID()}"
    },
    private val publicRankedFormationEntropy:
        PublicRankedFormationEntropy =
        SecurePublicRankedFormationEntropy(),
) : OnlineServerStore {
    private data class MatchRecord(
        val roomId: String,
        val matchId: String,
        var snapshot: OnlineMatchSnapshotDto,
        val revisionHistory: ArrayDeque<OnlineMatchSnapshotDto> = ArrayDeque(),
        val automaticSeatIndexes: MutableSet<Int> = mutableSetOf(),
        val automaticRoundSeatIndexes: MutableSet<Int> = mutableSetOf(),
        val developmentBotSeatIndexes: Set<Int> = emptySet(),
        val matchMode: DominoMatchMode,
        val classification: RankedMatchClassification,
        val rankedPlayerIdentitiesBySeat:
            List<RankedMatchPlayerIdentity>,
        var rankedMetricAccumulator: RankedMatchMetricAccumulator,
    )

    private data class ClockReductionResult(
        val runtimeState: DominoMatchRuntimeState,
        val turnWasResolved: Boolean,
    )

    private data class ActionResultCacheKey(
        val matchId: String,
        val playerId: String,
        val actionId: String,
    )

    private data class ExternalIdentityKey(
        val provider: OnlineExternalIdentityProvider,
        val subject: String,
    )

    private data class PublicRankedQueueEntry(
        val playerId: String,
        val accountId: String,
        var playerName: String,
        val enqueuedAtEpochMillis: Long,
        var lastSeenAtEpochMillis: Long,
    )

    private val lock = Any()

    private val roomsById = mutableMapOf<String, OnlineRoomSnapshotDto>()
    private val roomIdsByCode = mutableMapOf<String, String>()
    private val matchesById = mutableMapOf<String, MatchRecord>()
    private val actionResultsByKey =
        mutableMapOf<ActionResultCacheKey, OnlineActionResultDto>()
    private val rankedResultsById =
        mutableMapOf<String, RankedMatchResult>()
    private val rankedCycleSnapshotsById =
        mutableMapOf<String, RankedCycleSnapshot>()
    private val accountsByPlayerId =
        mutableMapOf<String, OnlineServerAccount>()
    private val externalIdentitiesByKey =
        mutableMapOf<ExternalIdentityKey, OnlineServerExternalIdentity>()

    private val publicRankedQueueByAccountId =
        linkedMapOf<String, PublicRankedQueueEntry>()
    private val publicRankedFormationHistory =
        mutableListOf<PublicRankedFormationHistoryEntry>()

    private var nextRoomSequence = 1
    private var nextMatchSequence = 1
    private var lastPruneAtEpochMillis: Long? = null

    override fun getRankedCycleLadder(
        kind: RankingCycleKind,
        completedAtEpochMillis: Long,
        rankingRuleVersion: Int,
    ): RankedCycleLadder {
        return synchronized(lock) {
            val period = resolveRankingCycle(
                kind = kind,
                completedAtEpochMillis = completedAtEpochMillis,
                rankingRuleVersion = rankingRuleVersion,
            )

            rankedCycleSnapshotsById[period.cycleId]
                ?.toRankedCycleLadder()
                ?: buildLiveRankedCycleLadder(period)
        }
    }

    override fun getClosedRankedCycleSnapshot(
        cycleId: String,
    ): RankedCycleSnapshot? {
        val normalizedCycleId = cycleId.trim()

        if (normalizedCycleId.isBlank()) {
            return null
        }

        return synchronized(lock) {
            rankedCycleSnapshotsById[normalizedCycleId]
        }
    }

    override fun listClosedRankedCycleSnapshots(
        kind: RankingCycleKind,
        offset: Int,
        limit: Int,
    ): RankedCycleSnapshotPage {
        require(offset >= 0)
        require(limit > 0)

        return synchronized(lock) {
            val matchingSnapshots = rankedCycleSnapshotsById.values
                .asSequence()
                .filter { snapshot ->
                    snapshot.period.kind == kind
                }
                .sortedWith(
                    compareByDescending<RankedCycleSnapshot> { snapshot ->
                        snapshot.period.endsAtEpochMillis
                    }.thenByDescending { snapshot ->
                        snapshot.period.cycleId
                    },
                )
                .toList()

            RankedCycleSnapshotPage(
                totalSnapshots = matchingSnapshots.size,
                snapshots = matchingSnapshots
                    .drop(offset)
                    .take(limit),
            )
        }
    }

    private fun buildLiveRankedCycleLadder(
        period: RankedCyclePeriod,
    ): RankedCycleLadder {
        return buildRankedCycleLadder(
            period = period,
            results = rankedResultsById.values.toList(),
        )
    }

    private fun materializeClosedRankedCycleSnapshots(
        referenceEpochMillis: Long,
    ): Int {
        require(referenceEpochMillis >= 0L)

        val closedPeriods = rankedResultsById.values
            .asSequence()
            .flatMap { result ->
                resolveRankingCycles(
                    completedAtEpochMillis =
                        result.completedAtEpochMillis,
                    rankingRuleVersion = result.rankingRuleVersion,
                ).asSequence()
            }
            .filter { period ->
                period.endsAtEpochMillis <= referenceEpochMillis
            }
            .distinctBy { period -> period.cycleId }
            .sortedWith(
                compareBy<RankedCyclePeriod>(
                    { period -> period.endsAtEpochMillis },
                    { period -> period.kind.ordinal },
                    { period -> period.cycleId },
                ),
            )
            .toList()

        var materializedCount = 0

        closedPeriods.forEach { period ->
            if (period.cycleId !in rankedCycleSnapshotsById) {
                rankedCycleSnapshotsById[period.cycleId] =
                    buildLiveRankedCycleLadder(period)
                        .toClosedSnapshot(
                            closedAtEpochMillis =
                                referenceEpochMillis,
                        )
                materializedCount += 1
            }
        }

        return materializedCount
    }

    override fun promoteAccount(
        playerId: String,
        expectedAccountId: String?,
    ): OnlineServerAccount? {
        return synchronized(lock) {
            val normalizedPlayerId = playerId.trim()
            require(
                normalizedPlayerId.isNotBlank() &&
                    normalizedPlayerId.length <= MAX_SERVER_IDENTIFIER_CHARACTERS
            ) {
                "O playerId da promoção de conta é inválido."
            }

            val existingAccount = accountsByPlayerId[normalizedPlayerId]
            val normalizedExpectedAccountId = expectedAccountId
                ?.trim()
                ?.takeIf { value -> value.isNotBlank() }

            if (expectedAccountId != null) {
                return@synchronized existingAccount?.takeIf { account ->
                    account.accountId == normalizedExpectedAccountId
                }
            }

            if (existingAccount != null) {
                return@synchronized existingAccount
            }

            val accountId = accountIdFactory().trim()
            require(
                accountId.isNotBlank() &&
                    accountId.length <= MAX_SERVER_IDENTIFIER_CHARACTERS
            ) {
                "O accountId emitido para a promoção é inválido."
            }
            check(
                accountsByPlayerId.values.none { account ->
                    account.accountId == accountId
                }
            ) {
                "O accountId emitido já está vinculado a outro player."
            }

            OnlineServerAccount(
                accountId = accountId,
                playerId = normalizedPlayerId,
                createdAtEpochMillis = nowEpochMillis(),
            ).also { account ->
                accountsByPlayerId[normalizedPlayerId] = account
            }
        }
    }

    override fun linkExternalIdentity(
        playerId: String,
        expectedAccountId: String?,
        provider: OnlineExternalIdentityProvider,
        subject: String,
    ): OnlineExternalIdentityLinkResult {
        return synchronized(lock) {
            val normalizedPlayerId = requireStoreIdentifier(
                value = playerId,
                fieldName = "playerId",
            )
            val normalizedSubject = requireStoreIdentifier(
                value = subject,
                fieldName = "external subject",
            )
            val normalizedExpectedAccountId = expectedAccountId
                ?.let { value ->
                    requireStoreIdentifier(
                        value = value,
                        fieldName = "expectedAccountId",
                    )
                }
            val key = ExternalIdentityKey(
                provider = provider,
                subject = normalizedSubject,
            )
            val existingIdentity = externalIdentitiesByKey[key]

            if (existingIdentity != null) {
                val existingAccount = accountsByPlayerId.values
                    .firstOrNull { account ->
                        account.accountId == existingIdentity.accountId
                    }
                val isSameCanonicalAccount =
                    existingAccount?.playerId == normalizedPlayerId &&
                        (
                            normalizedExpectedAccountId == null ||
                                existingAccount.accountId ==
                                normalizedExpectedAccountId
                        )

                return@synchronized if (isSameCanonicalAccount) {
                    OnlineExternalIdentityLinkResult.Linked(
                        account = requireNotNull(existingAccount),
                    )
                } else {
                    OnlineExternalIdentityLinkResult.Conflict
                }
            }

            val account = promoteAccount(
                playerId = normalizedPlayerId,
                expectedAccountId = normalizedExpectedAccountId,
            ) ?: return@synchronized OnlineExternalIdentityLinkResult.Conflict

            val hasDifferentIdentityForProvider =
                externalIdentitiesByKey.values.any { identity ->
                    identity.provider == provider &&
                        identity.accountId == account.accountId &&
                        identity.subject != normalizedSubject
                }

            if (hasDifferentIdentityForProvider) {
                return@synchronized OnlineExternalIdentityLinkResult.Conflict
            }

            externalIdentitiesByKey[key] = OnlineServerExternalIdentity(
                provider = provider,
                subject = normalizedSubject,
                accountId = account.accountId,
                linkedAtEpochMillis = nowEpochMillis(),
            )

            OnlineExternalIdentityLinkResult.Linked(
                account = account,
            )
        }
    }

    override fun findAccountByExternalIdentity(
        provider: OnlineExternalIdentityProvider,
        subject: String,
    ): OnlineServerAccount? {
        return synchronized(lock) {
            val normalizedSubject = requireStoreIdentifier(
                value = subject,
                fieldName = "external subject",
            )
            val identity = externalIdentitiesByKey[
                ExternalIdentityKey(
                    provider = provider,
                    subject = normalizedSubject,
                )
            ] ?: return@synchronized null

            accountsByPlayerId.values.firstOrNull { account ->
                account.accountId == identity.accountId
            }
        }
    }

    override fun getAccountProfile(
        accountId: String,
    ): OnlineAccountProfile? {
        return synchronized(lock) {
            val normalizedAccountId = requireStoreIdentifier(
                value = accountId,
                fieldName = "accountId",
            )

            accountsByPlayerId.values
                .firstOrNull { account ->
                    account.accountId == normalizedAccountId
                }
                ?.toOnlineAccountProfileOrNull()
        }
    }

    override fun updateAccountProfile(
        accountId: String,
        publicDisplayName: String,
        tableName: String?,
    ): OnlineAccountProfile? {
        return synchronized(lock) {
            val normalizedAccountId = requireStoreIdentifier(
                value = accountId,
                fieldName = "accountId",
            )
            val account = accountsByPlayerId.values
                .firstOrNull { candidate ->
                    candidate.accountId == normalizedAccountId
                }
                ?: return@synchronized null
            val updatedAtEpochMillis = maxOf(
                nowEpochMillis(),
                account.createdAtEpochMillis,
                account.profileUpdatedAtEpochMillis ?: 0L,
            )
            val profile = createOnlineAccountProfile(
                publicDisplayName = publicDisplayName,
                tableName = tableName,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )

            accountsByPlayerId[account.playerId] = account.copy(
                publicDisplayName = profile.publicDisplayName,
                tableName = profile.tableName,
                profileUpdatedAtEpochMillis =
                    profile.updatedAtEpochMillis,
            )

            profile
        }
    }

    override fun getPublicDisplayNames(
        accountIds: Set<String>,
    ): Map<String, String> {
        return synchronized(lock) {
            if (accountIds.isEmpty()) {
                return@synchronized emptyMap()
            }

            val normalizedAccountIds = accountIds.map { accountId ->
                requireStoreIdentifier(
                    value = accountId,
                    fieldName = "accountId",
                )
            }.toSet()

            accountsByPlayerId.values
                .asSequence()
                .filter { account ->
                    account.accountId in normalizedAccountIds
                }
                .mapNotNull { account ->
                    account.publicDisplayName?.let { displayName ->
                        account.accountId to displayName
                    }
                }
                .toMap()
        }
    }

    internal fun createPublicRankedRoom(
        request: CreateOnlineRoomRequestDto,
        identity: OnlineRequestIdentity,
    ): OnlineRoomOperationResultDto {
        return createServerManagedRoom(
            request = request,
            matchMode = DominoMatchMode.PUBLIC_RANKED,
            rankedIdentity = identity,
        )
    }

    internal fun joinPublicRankedRoom(
        request: JoinOnlineRoomRequestDto,
        identity: OnlineRequestIdentity,
    ): OnlineRoomOperationResultDto {
        return joinServerManagedRoom(
            request = request,
            expectedMatchMode = DominoMatchMode.PUBLIC_RANKED,
            rankedIdentity = identity,
        )
    }

    override fun enqueuePublicRanked(
        request: CreateOnlineRoomRequestDto,
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult {
        return synchronized(lock) {
            val now = nowEpochMillis()
            pruneExpiredRecords(
                nowEpochMillis = now,
                force = true,
            )

            val rankedIdentity = resolveRankedPlayerIdentityOrNull(
                identity = identity,
                expectedPlayerId = request.localPlayerId,
            ) ?: return@synchronized rejectedPublicRankedQueueResult(
                reason =
                    "Conta autenticada obrigatória para entrar na fila ranqueada.",
            )

            if (request.playerName.isBlank()) {
                return@synchronized rejectedPublicRankedQueueResult(
                    reason = "Nome do jogador não informado.",
                )
            }

            if (
                request.playerName.length >
                MAX_SERVER_PLAYER_NAME_CHARACTERS
            ) {
                return@synchronized rejectedPublicRankedQueueResult(
                    reason = "Nome do jogador acima do limite.",
                )
            }

            findActivePublicRankedRoom(
                playerId = rankedIdentity.playerId,
            )?.let { room ->
                return@synchronized matchedPublicRankedQueueResult(
                    room = room,
                    playerId = rankedIdentity.playerId,
                )
            }

            val activeOtherRoom = roomsById.values.firstOrNull { room ->
                room.status in setOf(
                    OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
                    OnlineRoomStatusDto.IN_MATCH,
                ) &&
                    room.players.any { player ->
                        player.playerId == rankedIdentity.playerId
                    }
            }

            if (activeOtherRoom != null) {
                return@synchronized rejectedPublicRankedQueueResult(
                    reason =
                        "O jogador já participa de outra sala ativa.",
                )
            }

            val accountId = requireNotNull(
                rankedIdentity.accountId,
            )
            val conflictingPlayerEntry =
                publicRankedQueueByAccountId.values.firstOrNull { entry ->
                    entry.playerId == rankedIdentity.playerId &&
                        entry.accountId != accountId
                }

            if (conflictingPlayerEntry != null) {
                return@synchronized rejectedPublicRankedQueueResult(
                    reason =
                        "O jogador já possui outra identidade na fila.",
                )
            }

            val existingEntry = publicRankedQueueByAccountId[accountId]
            if (existingEntry != null) {
                existingEntry.playerName = request.playerName
                existingEntry.lastSeenAtEpochMillis = now
            } else {
                if (
                    publicRankedQueueByAccountId.size >=
                    resourcePolicy.maxPublicRankedQueueSize
                ) {
                    return@synchronized rejectedPublicRankedQueueResult(
                        reason =
                            "Capacidade temporária da fila ranqueada atingida.",
                    )
                }

                publicRankedQueueByAccountId[accountId] =
                    PublicRankedQueueEntry(
                        playerId = rankedIdentity.playerId,
                        accountId = accountId,
                        playerName = request.playerName,
                        enqueuedAtEpochMillis = now,
                        lastSeenAtEpochMillis = now,
                    )
            }

            formPublicRankedMatchesFromQueue(
                nowEpochMillis = now,
            )

            findActivePublicRankedRoom(
                playerId = rankedIdentity.playerId,
            )?.let { room ->
                return@synchronized matchedPublicRankedQueueResult(
                    room = room,
                    playerId = rankedIdentity.playerId,
                )
            }

            queuedPublicRankedResult(
                accountId = accountId,
            )
        }
    }

    override fun cancelPublicRankedQueue(
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult {
        return synchronized(lock) {
            val now = nowEpochMillis()
            pruneExpiredRecords(
                nowEpochMillis = now,
                force = true,
            )

            val rankedIdentity = resolveRankedPlayerIdentityOrNull(
                identity = identity,
                expectedPlayerId = identity.playerId,
            ) ?: return@synchronized rejectedPublicRankedQueueResult(
                reason =
                    "Conta autenticada obrigatória para cancelar a fila ranqueada.",
            )

            findActivePublicRankedRoom(
                playerId = rankedIdentity.playerId,
            )?.let { room ->
                return@synchronized PublicRankedQueueResult(
                    accepted = false,
                    status = PublicRankedQueueStatus.MATCHED,
                    roomSnapshot = room,
                    localSeatIndex = room.players.firstOrNull { player ->
                        player.playerId == rankedIdentity.playerId
                    }?.seatIndex,
                    reason =
                        "A mesa já foi formada e não pode ser cancelada pela fila.",
                )
            }

            val accountId = requireNotNull(rankedIdentity.accountId)
            publicRankedQueueByAccountId.remove(accountId)

            PublicRankedQueueResult(
                accepted = true,
                status = PublicRankedQueueStatus.NOT_QUEUED,
            )
        }
    }

    override fun getPublicRankedQueueStatus(
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult {
        return synchronized(lock) {
            val now = nowEpochMillis()
            pruneExpiredRecords(
                nowEpochMillis = now,
                force = true,
            )

            val rankedIdentity = resolveRankedPlayerIdentityOrNull(
                identity = identity,
                expectedPlayerId = identity.playerId,
            ) ?: return@synchronized rejectedPublicRankedQueueResult(
                reason =
                    "Conta autenticada obrigatória para consultar a fila ranqueada.",
            )

            findActivePublicRankedRoom(
                playerId = rankedIdentity.playerId,
            )?.let { room ->
                return@synchronized matchedPublicRankedQueueResult(
                    room = room,
                    playerId = rankedIdentity.playerId,
                )
            }

            val accountId = requireNotNull(rankedIdentity.accountId)
            val queuedEntry = publicRankedQueueByAccountId[accountId]
            if (queuedEntry != null) {
                queuedEntry.lastSeenAtEpochMillis = now
                queuedPublicRankedResult(
                    accountId = accountId,
                )
            } else {
                PublicRankedQueueResult(
                    accepted = true,
                    status = PublicRankedQueueStatus.NOT_QUEUED,
                )
            }
        }
    }

    override fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return createServerManagedRoom(
            request = request,
            matchMode = DominoMatchMode.PRIVATE_UNRANKED,
        )
    }

    /**
     * Internal server-owned mode seam.
     *
     * This method is not exposed by the HTTP room-by-code interface.
     * PUBLIC_RANKED requires a server-resolved ACCOUNT identity whose
     * playerId/accountId pair matches the persistent account registry.
     */
    internal fun createServerManagedRoom(
        request: CreateOnlineRoomRequestDto,
        matchMode: DominoMatchMode,
        rankedIdentity: OnlineRequestIdentity? = null,
    ): OnlineRoomOperationResultDto {
        return synchronized(lock) {
            require(matchMode.isServerHosted) {
                "A modalidade da sala deve ser hospedada pelo servidor."
            }
            require(matchMode.isEnabledInMvp) {
                "A modalidade da sala não está habilitada no MVP."
            }

            val rankedPlayerIdentity =
                resolveRankedPlayerIdentityOrNull(
                    identity = rankedIdentity,
                    expectedPlayerId = request.localPlayerId,
                )

            if (
                matchMode.contributesToRanking &&
                rankedPlayerIdentity == null
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_public_ranked_room",
                    playerId = request.localPlayerId,
                    reason =
                        "Conta autenticada obrigatória para partida ranqueada.",
                )
            }

            if (
                !matchMode.contributesToRanking &&
                rankedIdentity != null
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason =
                        "Identidade competitiva não permitida nesta modalidade.",
                )
            }

            pruneExpiredRecords(
                nowEpochMillis = nowEpochMillis(),
                force = true,
            )

            if (roomsById.size >= resourcePolicy.maxRoomCount) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Capacidade temporária de salas atingida.",
                )
            }

            if (request.localPlayerId.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Identificador do jogador não informado.",
                )
            }

            if (
                request.localPlayerId.length >
                MAX_SERVER_IDENTIFIER_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Identificador do jogador acima do limite.",
                )
            }

            if (request.playerName.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador não informado.",
                )
            }

            if (
                request.playerName.length >
                MAX_SERVER_PLAYER_NAME_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "create_room",
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador acima do limite.",
                )
            }

            val roomSequence = nextRoomSequence++
            val roomId = "server-room-$roomSequence"
            val roomCode = roomSequence.toString().padStart(
                length = 4,
                padChar = '0',
            )

            val now = nowEpochMillis()

            val room = OnlineRoomSnapshotDto(
                roomId = roomId,
                roomCode = roomCode,
                hostPlayerId = request.localPlayerId,
                status = OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
                players = listOf(
                    OnlineRoomPlayerDto(
                        playerId = request.localPlayerId,
                        name = request.playerName,
                        seatIndex = 0,
                        connected = true,
                        participantType =
                            OnlineParticipantTypeDto.HUMAN,
                    ),
                ),
                matchMode = matchMode,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )

            roomsById[roomId] = room
            roomIdsByCode[roomCode] = roomId

            trace(
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.SERVER_STORE,
                type = OnlineTraceType.ROOM_CREATED,
                roomId = roomId,
                playerId = request.localPlayerId,
                localSeatIndex = 0,
                attributes = room.traceAttributes() + mapOf(
                    "operation" to "create_room",
                ),
            )

            OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = room,
                localSeatIndex = 0,
            )
        }
    }

    override fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return joinServerManagedRoom(
            request = request,
            expectedMatchMode = DominoMatchMode.PRIVATE_UNRANKED,
        )
    }

    /**
     * Internal counterpart for a server-selected mode.
     * Public code/invite routes continue to call [joinRoom], which accepts
     * only PRIVATE_UNRANKED rooms.
     */
    internal fun joinServerManagedRoom(
        request: JoinOnlineRoomRequestDto,
        expectedMatchMode: DominoMatchMode,
        rankedIdentity: OnlineRequestIdentity? = null,
    ): OnlineRoomOperationResultDto {
        return synchronized(lock) {
            require(expectedMatchMode.isServerHosted) {
                "A modalidade esperada deve ser hospedada pelo servidor."
            }
            require(expectedMatchMode.isEnabledInMvp) {
                "A modalidade esperada não está habilitada no MVP."
            }

            pruneExpiredRecords(
                nowEpochMillis = nowEpochMillis(),
                force = true,
            )

            val normalizedRoomCode = request.roomCode.trim()

            if (
                normalizedRoomCode.isBlank() ||
                normalizedRoomCode.length >
                MAX_SERVER_ROOM_CODE_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    playerId = request.localPlayerId,
                    reason = "Código de sala inválido.",
                )
            }

            val roomId = roomIdsByCode[normalizedRoomCode]
                ?: return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    playerId = request.localPlayerId,
                    reason = "Código de sala inválido.",
                )

            val currentRoom = roomsById[roomId]
                ?: return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = roomId,
                    playerId = request.localPlayerId,
                    reason = "Sala não encontrada.",
                )

            if (currentRoom.matchMode != expectedMatchMode) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "A sala não pertence a esta modalidade.",
                )
            }

            val rankedPlayerIdentity =
                resolveRankedPlayerIdentityOrNull(
                    identity = rankedIdentity,
                    expectedPlayerId = request.localPlayerId,
                )

            if (
                expectedMatchMode.contributesToRanking &&
                rankedPlayerIdentity == null
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_public_ranked_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason =
                        "Conta autenticada obrigatória para partida ranqueada.",
                )
            }

            if (
                !expectedMatchMode.contributesToRanking &&
                rankedIdentity != null
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason =
                        "Identidade competitiva não permitida nesta modalidade.",
                )
            }

            if (
                currentRoom.status == OnlineRoomStatusDto.CLOSED ||
                currentRoom.status == OnlineRoomStatusDto.FINISHED
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "A sala não está mais disponível.",
                )
            }

            if (request.localPlayerId.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    reason = "Identificador do jogador não informado.",
                )
            }

            if (
                request.localPlayerId.length >
                MAX_SERVER_IDENTIFIER_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "Identificador do jogador acima do limite.",
                )
            }

            if (request.playerName.isBlank()) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador não informado.",
                )
            }

            if (
                request.playerName.length >
                MAX_SERVER_PLAYER_NAME_CHARACTERS
            ) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "Nome do jogador acima do limite.",
                )
            }

            val existingPlayer = currentRoom.players.firstOrNull { player ->
                player.playerId == request.localPlayerId
            }

            if (existingPlayer != null) {
                val updatedRoom = currentRoom.copy(
                    players = currentRoom.players.map { player ->
                        if (player.playerId == request.localPlayerId) {
                            player.copy(
                                name = request.playerName,
                                connected = true,
                            )
                        } else {
                            player
                        }
                    },
                    updatedAtEpochMillis = nowEpochMillis(),
                )

                roomsById[updatedRoom.roomId] = updatedRoom

                val controlReclaimed = reclaimHumanSeatControlIfNeeded(
                    room = updatedRoom,
                    player = existingPlayer,
                )

                trace(
                    level = OnlineTraceLevel.INFO,
                    source = OnlineTraceSource.SERVER_STORE,
                    type = OnlineTraceType.ROOM_JOINED,
                    roomId = updatedRoom.roomId,
                    matchId = updatedRoom.matchId,
                    playerId = request.localPlayerId,
                    localSeatIndex = existingPlayer.seatIndex,
                    attributes = updatedRoom.traceAttributes() + mapOf(
                        "operation" to "join_room",
                        "reconnected" to "true",
                        "controlReclaimed" to controlReclaimed.toString(),
                    ),
                )

                return@synchronized OnlineRoomOperationResultDto(
                    accepted = true,
                    roomSnapshot = updatedRoom,
                    localSeatIndex = existingPlayer.seatIndex,
                )
            }

            if (currentRoom.status == OnlineRoomStatusDto.IN_MATCH) {
                return@synchronized rejectedRoomOperationWithTrace(
                    operation = "join_room",
                    roomId = currentRoom.roomId,
                    matchId = currentRoom.matchId,
                    playerId = request.localPlayerId,
                    reason = "A partida já foi iniciada.",
                )
            }

            val occupiedSeats = currentRoom.players
                .mapNotNull { player -> player.seatIndex }
                .toSet()

            val nextSeatIndex = (0..3).firstOrNull { seatIndex ->
                seatIndex !in occupiedSeats
            } ?: return@synchronized rejectedRoomOperationWithTrace(
                operation = "join_room",
                roomId = currentRoom.roomId,
                matchId = currentRoom.matchId,
                playerId = request.localPlayerId,
                reason = "A sala já está cheia.",
            )

            val playersAfterHumanJoin = currentRoom.players + OnlineRoomPlayerDto(
                playerId = request.localPlayerId,
                name = request.playerName,
                seatIndex = nextSeatIndex,
                connected = true,
                participantType =
                    OnlineParticipantTypeDto.HUMAN,
            )

            val updatedPlayers =
                if (currentRoom.matchMode.contributesToRanking) {
                    playersAfterHumanJoin
                } else {
                    addDevelopmentBotsIfNeeded(
                        players = playersAfterHumanJoin,
                    )
                }

            val shouldStartMatch = updatedPlayers.size == 4
            val nextMatchId = if (shouldStartMatch) {
                "server-match-${nextMatchSequence++}"
            } else {
                null
            }

            val updatedRoom = currentRoom.copy(
                status = if (shouldStartMatch) {
                    OnlineRoomStatusDto.IN_MATCH
                } else {
                    OnlineRoomStatusDto.WAITING_FOR_PLAYERS
                },
                players = updatedPlayers,
                matchId = nextMatchId,
                updatedAtEpochMillis = nowEpochMillis(),
            )

            roomsById[updatedRoom.roomId] = updatedRoom

            if (nextMatchId != null) {
                createMatch(
                    room = updatedRoom,
                    matchId = nextMatchId,
                )
            }

            trace(
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.SERVER_STORE,
                type = OnlineTraceType.ROOM_JOINED,
                roomId = updatedRoom.roomId,
                matchId = updatedRoom.matchId,
                playerId = request.localPlayerId,
                localSeatIndex = nextSeatIndex,
                attributes = updatedRoom.traceAttributes() + mapOf(
                    "operation" to "join_room",
                    "reconnected" to "false",
                    "matchStarted" to shouldStartMatch.toString(),
                ),
            )

            OnlineRoomOperationResultDto(
                accepted = true,
                roomSnapshot = updatedRoom,
                localSeatIndex = nextSeatIndex,
            )
        }
    }

    override fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        return synchronized(lock) {
            trace(
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.SERVER_STORE,
                type = OnlineTraceType.ACTION_SUBMITTED,
                action = action,
                attributes = action.traceAttributes(),
            )

            val invalidActionReason = action.validationReasonOrNull()

            if (invalidActionReason != null) {
                val result = rejectedAction(
                    reason = invalidActionReason,
                ).copy(
                    actionId = action.actionId,
                )

                trace(
                    level = OnlineTraceLevel.WARN,
                    source = OnlineTraceSource.SERVER_STORE,
                    type = OnlineTraceType.ACTION_REJECTED,
                    action = action,
                    attributes = action.traceAttributes() + mapOf(
                        "reason" to invalidActionReason,
                    ),
                )

                return@synchronized result
            }

            actionResultsByKey[action.toActionResultCacheKey()]?.let { cachedResult ->
                trace(
                    level = OnlineTraceLevel.INFO,
                    source = OnlineTraceSource.SERVER_STORE,
                    type = OnlineTraceType.ACTION_DEDUPLICATED,
                    action = action,
                    snapshotRevision = cachedResult.revision,
                    attributes = action.traceAttributes() + mapOf(
                        "cachedAccepted" to cachedResult.accepted.toString(),
                    ),
                )

                return@synchronized cachedResult
            }

            val currentRoom = roomsById[action.roomId]
                ?: return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Sala inválida.",
                )

            val matchRecord = matchesById[action.matchId]
                ?: return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Partida inválida.",
                )

            if (matchRecord.roomId != currentRoom.roomId) {
                return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "A partida não pertence à sala informada.",
                    revision = matchRecord.snapshot.revision,
                )
            }

            val roomPlayer = currentRoom.players.firstOrNull { player ->
                player.playerId == action.playerId
            } ?: return@synchronized cacheRejectedAction(
                action = action,
                reason = "Jogador não encontrado na sala.",
                revision = matchRecord.snapshot.revision,
            )

            val seatIndex = roomPlayer.seatIndex
                ?: return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Jogador sem assento definido.",
                    revision = matchRecord.snapshot.revision,
                )

            if (!roomPlayer.connected) {
                return@synchronized cacheRejectedAction(
                    action = action,
                    reason = "Jogador desconectado.",
                    revision = matchRecord.snapshot.revision,
                )
            }

            if (
                action.type == OnlinePlayerActionTypeDto.PLAY_MOVE ||
                action.type == OnlinePlayerActionTypeDto.PASS_TURN
            ) {
                /*
                 * Fecha a pequena janela entre ticks: uma jogada enviada no
                 * instante do timeout ainda é comparada ao relógio do servidor
                 * antes de o redutor validar a ação humana.
                 */
                advanceAuthoritativeMatch(
                    matchRecord = matchRecord,
                    nowEpochMillis = nowEpochMillis(),
                    trigger = "action_pre_validation",
                    traceSource = OnlineTraceSource.SERVER_STORE,
                )
            }

            val result = when (action.type) {
                /*
                 * Mantido temporariamente no contrato para compatibilidade de
                 * versões. Não conduz mais relógio, bot ou troca de turno.
                 */
                OnlinePlayerActionTypeDto.REQUEST_SNAPSHOT -> {
                    OnlineActionResultDto(
                        accepted = true,
                        revision = matchRecord.snapshot.revision,
                    )
                }

                OnlinePlayerActionTypeDto.LEAVE_ROOM -> {
                    markPlayerDisconnected(
                        roomId = currentRoom.roomId,
                        playerId = action.playerId,
                    )

                    trace(
                        level = OnlineTraceLevel.INFO,
                        source = OnlineTraceSource.SERVER_STORE,
                        type = OnlineTraceType.ROOM_LEFT,
                        action = action,
                        snapshotRevision = matchRecord.snapshot.revision,
                        attributes = action.traceAttributes() + mapOf(
                            "operation" to "leave_room",
                        ),
                    )

                    OnlineActionResultDto(
                        accepted = true,
                        revision = matchRecord.snapshot.revision,
                    )
                }

                OnlinePlayerActionTypeDto.START_NEXT_ROUND -> {
                    submitStartNextRound(
                        action = action,
                        currentRoom = currentRoom,
                        matchRecord = matchRecord,
                    )
                }

                OnlinePlayerActionTypeDto.START_NEW_MATCH -> {
                    submitStartNewMatch(
                        action = action,
                        currentRoom = currentRoom,
                        matchRecord = matchRecord,
                    )
                }

                OnlinePlayerActionTypeDto.PLAY_MOVE,
                OnlinePlayerActionTypeDto.PASS_TURN -> {
                    submitGameAction(
                        action = action,
                        seatIndex = seatIndex,
                        matchRecord = matchRecord,
                    )
                }
            }

            val cachedResult = cacheActionResult(
                action = action,
                result = result,
            )

            traceActionResult(
                action = action,
                result = cachedResult,
                matchRecord = matchRecord,
                localSeatIndex = seatIndex,
            )

            cachedResult
        }
    }

    /**
     * Avança a partida a partir do relógio do próprio servidor.
     *
     * Clientes nunca chamam este método por HTTP. O ticker do processo o invoca
     * em cadência fixa, garantindo que polling e visualização sejam somente
     * observacionais. Cada chamada publica no máximo uma transição por partida,
     * preservando uma cadência consumível pela fila visual dos clientes.
     */
    override fun advanceAuthoritativeTime(): Boolean {
        return synchronized(lock) {
            val now = nowEpochMillis()
            var stateChanged = pruneExpiredRecords(
                nowEpochMillis = now,
            )

            matchesById.values.forEach { matchRecord ->
                val previousRevision = matchRecord.snapshot.revision

                val changed = advanceAuthoritativeMatch(
                    matchRecord = matchRecord,
                    nowEpochMillis = now,
                    trigger = "ticker",
                    traceSource = OnlineTraceSource.SERVER_TICKER,
                )

                if (changed) {
                    stateChanged = true
                    trace(
                        level = OnlineTraceLevel.INFO,
                        source = OnlineTraceSource.SERVER_TICKER,
                        type = OnlineTraceType.AUTHORITATIVE_TICK,
                        roomId = matchRecord.roomId,
                        matchId = matchRecord.matchId,
                        snapshotRevision = matchRecord.snapshot.revision,
                        runtimeState = matchRecord.snapshot.toRuntimeState(
                            localPlayerIndex = 0,
                        ),
                        automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                        attributes = mapOf(
                            "previousRevision" to previousRevision.toString(),
                            "trigger" to "ticker",
                        ),
                    )
                }
            }

            if (
                materializeClosedRankedCycleSnapshots(
                    referenceEpochMillis = now,
                ) > 0
            ) {
                stateChanged = true
            }

            stateChanged
        }
    }

    override fun getRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto? {
        return synchronized(lock) {
            roomsById[roomId]
        }
    }

    override fun isRoomParticipant(
        roomId: String,
        playerId: String,
    ): Boolean {
        return synchronized(lock) {
            roomsById[roomId]
                ?.players
                ?.any { player ->
                    player.playerId == playerId
                } == true
        }
    }

    override fun isMatchParticipant(
        matchId: String,
        playerId: String,
    ): Boolean {
        return synchronized(lock) {
            val matchRecord = matchesById[matchId] ?: return@synchronized false
            val room = roomsById[matchRecord.roomId] ?: return@synchronized false

            room.players.any { player ->
                player.playerId == playerId
            }
        }
    }

    override fun getMatchSnapshotForParticipant(
        matchId: String,
        playerId: String,
    ): OnlineMatchSnapshotDto? {
        return synchronized(lock) {
            val matchRecord = matchesById[matchId]
                ?: return@synchronized null
            val room = roomsById[matchRecord.roomId]
                ?: return@synchronized null
            val seatIndex = room.players
                .firstOrNull { player ->
                    player.playerId == playerId
                }
                ?.seatIndex
                ?: return@synchronized null

            matchRecord.snapshot.projectForParticipant(
                seatIndex = seatIndex,
            )
        }
    }

    override fun getMatchSnapshotsAfterForParticipant(
        matchId: String,
        playerId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>? {
        return synchronized(lock) {
            val matchRecord = matchesById[matchId]
                ?: return@synchronized null
            val room = roomsById[matchRecord.roomId]
                ?: return@synchronized null
            val seatIndex = room.players
                .firstOrNull { player ->
                    player.playerId == playerId
                }
                ?.seatIndex
                ?: return@synchronized null

            matchRecord.revisionHistory
                .filter { snapshot ->
                    snapshot.revision > afterRevision
                }
                .map { snapshot ->
                    snapshot.projectForParticipant(
                        seatIndex = seatIndex,
                    )
                }
        }
    }

    /**
     * Leitura observacional do estado autoritativo.
     *
     * Buscar um snapshot nunca reduz relógio, move bot, resolve toque ou cria
     * revisão. A mutação é exclusividade de [advanceAuthoritativeTime] e das
     * ações explícitas de jogo aceitas pelo servidor.
     */
    override fun getMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto? {
        return synchronized(lock) {
            matchesById[matchId]?.snapshot
        }
    }

    /**
     * Retorna em ordem todas as revisÃµes posteriores Ã  referÃªncia do cliente.
     * A consulta Ã© observacional e nÃ£o avança relÃ³gios ou turnos.
     */
    override fun getMatchSnapshotsAfter(
        matchId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>? {
        return synchronized(lock) {
            matchesById[matchId]
                ?.revisionHistory
                ?.filter { snapshot ->
                    snapshot.revision > afterRevision
                }
                ?.toList()
        }
    }

    internal fun getRankedMatchMetricAccumulator(
        matchId: String,
    ): RankedMatchMetricAccumulator? {
        return synchronized(lock) {
            matchesById[matchId]?.rankedMetricAccumulator
        }
    }

    internal fun getMatchMode(
        matchId: String,
    ): DominoMatchMode? {
        return synchronized(lock) {
            matchesById[matchId]?.matchMode
        }
    }

    internal fun getRankedMatchClassification(
        matchId: String,
    ): RankedMatchClassification? {
        return synchronized(lock) {
            matchesById[matchId]?.classification
        }
    }

    internal fun getRankedPlayerIdentities(
        matchId: String,
    ): List<RankedMatchPlayerIdentity>? {
        return synchronized(lock) {
            matchesById[matchId]
                ?.rankedPlayerIdentitiesBySeat
                ?.toList()
        }
    }

    internal fun getRankedMatchResult(
        matchId: String,
    ): RankedMatchResult? {
        return synchronized(lock) {
            rankedResultsById[
                createRankedMatchResultId(matchId)
            ]
        }
    }

    internal fun snapshotPersistentState(): OnlineServerStoreState {
        return synchronized(lock) {
            OnlineServerStoreState(
                nextRoomSequence = nextRoomSequence,
                nextMatchSequence = nextMatchSequence,
                rooms = roomsById.values.sortedBy { room ->
                    room.roomId
                },
                matches = matchesById.values
                    .sortedBy { matchRecord ->
                        matchRecord.matchId
                    }
                    .map { matchRecord ->
                        OnlineServerStoredMatch(
                            roomId = matchRecord.roomId,
                            matchId = matchRecord.matchId,
                            snapshot = matchRecord.snapshot,
                            revisionHistory =
                                matchRecord.revisionHistory.toList(),
                            automaticSeatIndexes =
                                matchRecord.automaticSeatIndexes.sorted(),
                            automaticRoundSeatIndexes =
                                matchRecord
                                    .automaticRoundSeatIndexes
                                    .sorted(),
                            applicationSeatIndexes =
                                matchRecord.developmentBotSeatIndexes.sorted(),
                            matchMode = matchRecord.matchMode,
                            classification = matchRecord.classification,
                            rankedMetricAccumulator =
                                matchRecord.rankedMetricAccumulator,
                        )
                    },
                actionResults = actionResultsByKey.entries
                    .map { (key, result) ->
                        OnlineServerStoredActionResult(
                            matchId = key.matchId,
                            playerId = key.playerId,
                            actionId = key.actionId,
                            result = result,
                        )
                    },
                rankedResults = rankedResultsById.values
                    .sortedBy { result -> result.resultId },
                rankedCycleSnapshots = rankedCycleSnapshotsById.values
                    .sortedBy { snapshot ->
                        snapshot.period.cycleId
                    },
                accounts = accountsByPlayerId.values
                    .sortedBy { account -> account.accountId },
                externalIdentities = externalIdentitiesByKey.values
                    .sortedWith(
                        compareBy<OnlineServerExternalIdentity>(
                            { identity -> identity.provider.name },
                            { identity -> identity.subject },
                        ),
                    ),
                publicRankedFormationHistory =
                    publicRankedFormationHistory.toList(),
            )
        }
    }

    internal fun restorePersistentState(
        state: OnlineServerStoreState,
    ) {
        synchronized(lock) {
            val normalizedState = normalizePersistentState(state)
            validatePersistentState(normalizedState)

            roomsById.clear()
            roomIdsByCode.clear()
            matchesById.clear()
            actionResultsByKey.clear()
            rankedResultsById.clear()
            rankedCycleSnapshotsById.clear()
            accountsByPlayerId.clear()
            externalIdentitiesByKey.clear()
            publicRankedQueueByAccountId.clear()
            publicRankedFormationHistory.clear()

            normalizedState.rooms.forEach { room ->
                roomsById[room.roomId] = room
                roomIdsByCode[room.roomCode] = room.roomId
            }

            normalizedState.accounts.forEach { account ->
                accountsByPlayerId[account.playerId] = account
            }

            normalizedState.matches.forEach { storedMatch ->
                matchesById[storedMatch.matchId] = MatchRecord(
                    roomId = storedMatch.roomId,
                    matchId = storedMatch.matchId,
                    snapshot = storedMatch.snapshot,
                    revisionHistory =
                        ArrayDeque<OnlineMatchSnapshotDto>().apply {
                            addAll(storedMatch.revisionHistory)
                        },
                    automaticSeatIndexes = storedMatch
                        .automaticSeatIndexes
                        .toMutableSet(),
                    automaticRoundSeatIndexes = storedMatch
                        .automaticRoundSeatIndexes
                        .toMutableSet(),
                    developmentBotSeatIndexes = storedMatch
                        .applicationSeatIndexes
                        .toSet(),
                    matchMode = storedMatch.matchMode,
                    classification = storedMatch.classification,
                    rankedPlayerIdentitiesBySeat =
                        if (storedMatch.matchMode.contributesToRanking) {
                            resolveRankedPlayerIdentitiesBySeat(
                                room = requireNotNull(
                                    normalizedState.rooms.singleOrNull { room ->
                                        room.roomId == storedMatch.roomId
                                    },
                                ),
                            )
                        } else {
                            emptyList()
                        },
                    rankedMetricAccumulator =
                        storedMatch.rankedMetricAccumulator
                            ?: RankedMatchMetricAccumulator.empty(
                                playerCount = storedMatch
                                    .snapshot
                                    .gameState
                                    .players
                                    .size,
                                teamCount = storedMatch
                                    .snapshot
                                    .gameState
                                    .teamScores
                                    .size,
                            ),
                )
            }

            normalizedState.actionResults.forEach { storedAction ->
                actionResultsByKey[
                    ActionResultCacheKey(
                        matchId = storedAction.matchId,
                        playerId = storedAction.playerId,
                        actionId = storedAction.actionId,
                    )
                ] = storedAction.result
            }

            normalizedState.rankedResults.forEach { rankedResult ->
                rankedResultsById[rankedResult.resultId] =
                    rankedResult
            }

            normalizedState.rankedCycleSnapshots.forEach { snapshot ->
                rankedCycleSnapshotsById[snapshot.period.cycleId] =
                    snapshot
            }

            normalizedState.externalIdentities.forEach { identity ->
                externalIdentitiesByKey[
                    ExternalIdentityKey(
                        provider = identity.provider,
                        subject = identity.subject,
                    )
                ] = identity
            }

            publicRankedFormationHistory.addAll(
                normalizedState.publicRankedFormationHistory,
            )

            nextRoomSequence = normalizedState.nextRoomSequence
            nextMatchSequence = normalizedState.nextMatchSequence
            lastPruneAtEpochMillis = null
        }
    }

    private fun normalizePersistentState(
        state: OnlineServerStoreState,
    ): OnlineServerStoreState {
        if (state.schemaVersion >=
            ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION
        ) {
            return state
        }

        if (state.schemaVersion == 8) {
            return state.copy(
                schemaVersion =
                    ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
                rankedCycleSnapshots =
                    state.rankedCycleSnapshots.map { snapshot ->
                        snapshot.copy(
                            totalEligiblePlayers = maxOf(
                                snapshot.totalEligiblePlayers,
                                snapshot.standings.size,
                            ),
                            retainedRankingSize =
                                snapshot.standings.size,
                        )
                    },
            )
        }

        if (state.schemaVersion == 7) {
            return state.copy(
                schemaVersion =
                    ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
                rankedCycleSnapshots = emptyList(),
            )
        }

        if (state.schemaVersion == 6) {
            return state.copy(
                schemaVersion =
                    ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
                rankedCycleSnapshots = emptyList(),
            )
        }

        if (state.schemaVersion == 5) {
            return state.copy(
                schemaVersion =
                    ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
                publicRankedFormationHistory = emptyList(),
                rankedCycleSnapshots = emptyList(),
            )
        }

        return state.copy(
            schemaVersion =
                ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
            rooms = state.rooms.map { room ->
                room.copy(
                    matchMode = DominoMatchMode.PRIVATE_UNRANKED,
                )
            },
            matches = state.matches.map { storedMatch ->
                storedMatch.copy(
                    matchMode = DominoMatchMode.PRIVATE_UNRANKED,
                    classification =
                        RankedMatchClassification.UNRANKED,
                )
            },
            rankedResults = emptyList(),
            rankedCycleSnapshots = emptyList(),
        )
    }

    private fun validatePersistentState(
        state: OnlineServerStoreState,
    ) {
        require(
            state.schemaVersion in
                    MINIMUM_SUPPORTED_ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION..
                    ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION
        ) {
            "Versão de estado autoritativo não suportada: " +
                    state.schemaVersion
        }
        require(state.nextRoomSequence > 0)
        require(state.nextMatchSequence > 0)
        val accountIds = state.accounts.map { account ->
            account.accountId
        }
        val accountPlayerIds = state.accounts.map { account ->
            account.playerId
        }
        require(accountIds.distinct().size == accountIds.size)
        require(accountPlayerIds.distinct().size == accountPlayerIds.size)
        require(
            state.accounts.all { account ->
                account.accountId.isNotBlank() &&
                    account.accountId.length <=
                    MAX_SERVER_IDENTIFIER_CHARACTERS &&
                    account.playerId.isNotBlank() &&
                    account.playerId.length <=
                    MAX_SERVER_IDENTIFIER_CHARACTERS &&
                    account.createdAtEpochMillis >= 0L &&
                    (
                        (
                            account.publicDisplayName == null &&
                            account.tableName == null &&
                            account.profileUpdatedAtEpochMillis == null
                        ) ||
                        (
                            account.publicDisplayName != null &&
                            account.tableName != null &&
                            account.profileUpdatedAtEpochMillis != null &&
                            account.profileUpdatedAtEpochMillis >=
                                account.createdAtEpochMillis &&
                            createOnlineAccountProfile(
                                publicDisplayName =
                                    account.publicDisplayName,
                                tableName = account.tableName,
                                updatedAtEpochMillis =
                                    account.profileUpdatedAtEpochMillis,
                            ) == account.toOnlineAccountProfileOrNull()
                        )
                    )
            }
        ) {
            "O estado persistido contém uma conta inválida."
        }
        val persistedAccountIds = accountIds.toSet()
        val persistedAccountsByPlayerId =
            state.accounts.associateBy { account ->
                account.playerId
            }

        require(
            state.publicRankedFormationHistory.size <=
                resourcePolicy.maxPublicRankedFormationHistoryCount
        ) {
            "O histórico de formação ranqueada excede o limite."
        }
        state.publicRankedFormationHistory.forEach { entry ->
            require(
                entry.roomId.isNotBlank() &&
                    entry.matchId.isNotBlank() &&
                    entry.formedAtEpochMillis >= 0L &&
                    (
                        entry.completedAtEpochMillis == null ||
                            entry.completedAtEpochMillis >=
                                entry.formedAtEpochMillis
                    ) &&
                    entry.selectedAccountIdsInQueueOrder.size == 4 &&
                    entry.accountIdsBySeat.size == 4 &&
                    entry.selectedAccountIdsInQueueOrder.distinct().size ==
                        4 &&
                    entry.accountIdsBySeat.distinct().size == 4 &&
                    entry.selectedAccountIdsInQueueOrder.toSet() ==
                        entry.accountIdsBySeat.toSet() &&
                    entry.accountIdsBySeat.all { accountId ->
                        accountId in persistedAccountIds
                    } &&
                    entry.auditNonce.matches(
                        Regex("[0-9a-f]{64}"),
                    ) &&
                    entry.auditCommitment ==
                        createPublicRankedFormationAuditCommitment(
                            roomId = entry.roomId,
                            matchId = entry.matchId,
                            formedAtEpochMillis =
                                entry.formedAtEpochMillis,
                            selectedAccountIdsInQueueOrder =
                                entry.selectedAccountIdsInQueueOrder,
                            accountIdsBySeat =
                                entry.accountIdsBySeat,
                            auditNonce = entry.auditNonce,
                        )
            ) {
                "O estado persistido contém formação ranqueada inválida."
            }
        }

        val externalIdentityKeys = state.externalIdentities.map { identity ->
            identity.provider to identity.subject
        }
        val externalIdentityAccountsByProvider =
            state.externalIdentities.map { identity ->
                identity.provider to identity.accountId
            }
        require(
            externalIdentityKeys.distinct().size ==
                externalIdentityKeys.size &&
                externalIdentityAccountsByProvider.distinct().size ==
                externalIdentityAccountsByProvider.size &&
                state.externalIdentities.all { identity ->
                    identity.subject.isNotBlank() &&
                        identity.subject.length <=
                        MAX_SERVER_IDENTIFIER_CHARACTERS &&
                        identity.accountId in persistedAccountIds &&
                        identity.linkedAtEpochMillis >= 0L
                }
        ) {
            "O estado persistido contem uma identidade externa invalida."
        }
        require(state.rooms.size <= resourcePolicy.maxRoomCount) {
            "O estado persistido excede a capacidade de salas."
        }
        require(
            state.actionResults.size <=
                    resourcePolicy.maxActionResultCount
        ) {
            "O estado persistido excede a capacidade de idempotência."
        }

        val roomIds = state.rooms.map { room -> room.roomId }
        val roomCodes = state.rooms.map { room -> room.roomCode }
        val matchIds = state.matches.map { match -> match.matchId }

        require(roomIds.none { roomId -> roomId.isBlank() })
        require(roomIds.distinct().size == roomIds.size)
        require(roomCodes.none { roomCode -> roomCode.isBlank() })
        require(roomCodes.distinct().size == roomCodes.size)
        require(
            state.rooms.all { room ->
                room.matchMode.isServerHosted &&
                    room.matchMode.isEnabledInMvp
            }
        )
        require(
            state.rooms.all { room ->
                if (room.matchMode != DominoMatchMode.PUBLIC_RANKED) {
                    true
                } else {
                    room.players.all { player ->
                        player.participantType ==
                            OnlineParticipantTypeDto.HUMAN &&
                            player.seatIndex != null &&
                            persistedAccountsByPlayerId[
                                player.playerId
                            ] != null
                    } &&
                        room.players.map { player ->
                            requireNotNull(
                                persistedAccountsByPlayerId[
                                    player.playerId
                                ],
                            ).accountId
                        }.distinct().size == room.players.size
                }
            }
        ) {
            "Sala PUBLIC_RANKED contém jogador sem conta autenticada."
        }
        require(matchIds.none { matchId -> matchId.isBlank() })
        require(matchIds.distinct().size == matchIds.size)

        val roomsByPersistedId = state.rooms.associateBy { room ->
            room.roomId
        }
        val matchesByPersistedId = state.matches.associateBy { match ->
            match.matchId
        }

        state.matches.forEach { storedMatch ->
            val room = requireNotNull(
                roomsByPersistedId[storedMatch.roomId],
            ) {
                "Partida persistida referencia sala inexistente."
            }

            require(room.matchId == storedMatch.matchId)
            require(storedMatch.snapshot.roomId == storedMatch.roomId)
            require(storedMatch.snapshot.matchId == storedMatch.matchId)
            require(storedMatch.revisionHistory.isNotEmpty())
            require(
                storedMatch.revisionHistory.size <=
                        MATCH_REVISION_HISTORY_CAPACITY
            )
            require(
                storedMatch.revisionHistory.last() ==
                        storedMatch.snapshot
            )
            require(
                storedMatch.revisionHistory
                    .zipWithNext()
                    .all { (previous, next) ->
                        next.revision == previous.revision + 1L
                    }
            )
            require(
                storedMatch.automaticSeatIndexes.all { index ->
                    index in 0..3
                }
            )
            require(
                storedMatch.applicationSeatIndexes.all { index ->
                    index in 0..3
                }
            )
            require(
                storedMatch.automaticRoundSeatIndexes.all { index ->
                    index in 0..3
                }
            )
            require(room.matchMode == storedMatch.matchMode)
            require(
                storedMatch.classification ==
                    storedMatch.matchMode.rankedMatchClassification
            )

            storedMatch.rankedMetricAccumulator?.let { accumulator ->
                require(
                    accumulator.seatMetrics.size ==
                            storedMatch.snapshot.gameState.players.size,
                )
                require(
                    accumulator.collectiveCountPointsByTeam.size ==
                            storedMatch.snapshot.gameState.teamScores.size,
                )
            }

            if (storedMatch.matchMode.contributesToRanking) {
                require(storedMatch.applicationSeatIndexes.isEmpty())
            }
        }

        state.rooms.forEach { room ->
            room.matchId?.let { matchId ->
                require(matchesByPersistedId[matchId]?.roomId == room.roomId)
            }
        }

        val actionKeys = state.actionResults.map { storedAction ->
            Triple(
                storedAction.matchId,
                storedAction.playerId,
                storedAction.actionId,
            )
        }

        require(actionKeys.distinct().size == actionKeys.size)
        require(
            state.actionResults.all { storedAction ->
                storedAction.matchId.isNotBlank() &&
                        storedAction.playerId.isNotBlank() &&
                        storedAction.actionId.isNotBlank() &&
                        storedAction.result.actionId == storedAction.actionId
            }
        )

        val rankedResultIds = state.rankedResults.map { result ->
            result.resultId
        }
        val rankedResultMatchIds = state.rankedResults.map { result ->
            result.matchId
        }

        require(
            rankedResultIds.distinct().size ==
                    rankedResultIds.size,
        )
        require(
            rankedResultMatchIds.distinct().size ==
                    rankedResultMatchIds.size,
        )
        require(
            state.rankedResults.all { result ->
                result.resultId ==
                        createRankedMatchResultId(result.matchId)
            },
        )

        val rankedCycleSnapshotIds =
            state.rankedCycleSnapshots.map { snapshot ->
                snapshot.period.cycleId
            }

        require(
            rankedCycleSnapshotIds.distinct().size ==
                rankedCycleSnapshotIds.size,
        ) {
            "O estado persistido contém ciclos encerrados duplicados."
        }
        require(
            state.rankedCycleSnapshots.all { snapshot ->
                snapshot.period.cycleId.isNotBlank() &&
                    snapshot.closedAtEpochMillis >=
                    snapshot.period.endsAtEpochMillis &&
                    snapshot.retainedRankingSize ==
                    snapshot.standings.size &&
                    snapshot.totalEligiblePlayers >=
                    snapshot.retainedRankingSize
            },
        ) {
            "O estado persistido contém snapshot de ranking inválido."
        }

        val rankedResultsByMatchId = state.rankedResults.associateBy {
            result -> result.matchId
        }

        state.matches.forEach { storedMatch ->
            val result = rankedResultsByMatchId[storedMatch.matchId]
            val matchFinished =
                storedMatch.snapshot.gameState.gameWinnerTeamIndex != null

            when (storedMatch.classification) {
                RankedMatchClassification.UNRANKED -> {
                    require(result == null)
                }

                RankedMatchClassification.RANKED -> {
                    require(
                        storedMatch.matchMode ==
                            DominoMatchMode.PUBLIC_RANKED
                    )
                    require(!matchFinished || result != null)

                    result?.let { rankedResult ->
                        val room = requireNotNull(
                            roomsByPersistedId[
                                storedMatch.roomId
                            ],
                        )
                        val expectedAccountsBySeat =
                            room.players.associate { player ->
                                val seatIndex = requireNotNull(
                                    player.seatIndex,
                                )
                                val account = requireNotNull(
                                    persistedAccountsByPlayerId[
                                        player.playerId
                                    ],
                                )

                                seatIndex to account.accountId
                            }

                        require(
                            rankedResult.players.all { player ->
                                player.accountId ==
                                    expectedAccountsBySeat[
                                        player.seatIndex
                                    ]
                            }
                        ) {
                            "Resultado ranqueado diverge das contas da sala."
                        }
                    }
                }
            }
        }

        val maximumRoomSequence = roomIds.maxOfOrNull { roomId ->
            roomId.removePrefix("server-room-").toIntOrNull() ?: 0
        } ?: 0
        val maximumMatchSequence = matchIds.maxOfOrNull { matchId ->
            matchId.removePrefix("server-match-").toIntOrNull() ?: 0
        } ?: 0

        require(state.nextRoomSequence > maximumRoomSequence)
        require(state.nextMatchSequence > maximumMatchSequence)
    }

    private fun rejectedPublicRankedQueueResult(
        reason: String,
    ): PublicRankedQueueResult {
        return PublicRankedQueueResult(
            accepted = false,
            status = PublicRankedQueueStatus.REJECTED,
            reason = reason,
        )
    }

    private fun queuedPublicRankedResult(
        accountId: String,
    ): PublicRankedQueueResult {
        val position = publicRankedQueueByAccountId.keys
            .indexOf(accountId)
            .takeIf { index -> index >= 0 }
            ?.plus(1)

        return PublicRankedQueueResult(
            accepted = true,
            status = PublicRankedQueueStatus.QUEUED,
            queuePosition = position,
        )
    }

    private fun matchedPublicRankedQueueResult(
        room: OnlineRoomSnapshotDto,
        playerId: String,
    ): PublicRankedQueueResult {
        return PublicRankedQueueResult(
            accepted = true,
            status = PublicRankedQueueStatus.MATCHED,
            roomSnapshot = room,
            localSeatIndex = room.players.firstOrNull { player ->
                player.playerId == playerId
            }?.seatIndex,
        )
    }

    private fun findActivePublicRankedRoom(
        playerId: String,
    ): OnlineRoomSnapshotDto? {
        return roomsById.values.firstOrNull { room ->
            room.matchMode == DominoMatchMode.PUBLIC_RANKED &&
                (
                    room.status ==
                        OnlineRoomStatusDto.WAITING_FOR_PLAYERS ||
                        room.status == OnlineRoomStatusDto.IN_MATCH
                ) &&
                room.players.any { player ->
                    player.playerId == playerId
                }
        }
    }

    private fun formPublicRankedMatchesFromQueue(
        nowEpochMillis: Long,
    ) {
        while (publicRankedQueueByAccountId.size >= 4) {
            if (roomsById.size >= resourcePolicy.maxRoomCount) {
                return
            }

            val invalidAccountIds =
                publicRankedQueueByAccountId.values
                    .filter { entry ->
                        accountsByPlayerId[entry.playerId]?.accountId !=
                            entry.accountId ||
                            roomsById.values.any { room ->
                                room.status in setOf(
                                    OnlineRoomStatusDto.WAITING_FOR_PLAYERS,
                                    OnlineRoomStatusDto.IN_MATCH,
                                ) &&
                                    room.players.any { player ->
                                        player.playerId == entry.playerId
                                    }
                            }
                    }
                    .map { entry -> entry.accountId }

            if (invalidAccountIds.isNotEmpty()) {
                invalidAccountIds.forEach { accountId ->
                    publicRankedQueueByAccountId.remove(accountId)
                }
                continue
            }

            val plan = planPublicRankedMatchFormation(
                queuedCandidates =
                    publicRankedQueueByAccountId.values.map { entry ->
                        PublicRankedFormationCandidate(
                            playerId = entry.playerId,
                            accountId = entry.accountId,
                            playerName = entry.playerName,
                            enqueuedAtEpochMillis =
                                entry.enqueuedAtEpochMillis,
                        )
                    },
                recentHistory = publicRankedFormationHistory,
                nowEpochMillis = nowEpochMillis,
                policy = resourcePolicy,
                entropy = publicRankedFormationEntropy,
            ) ?: return

            check(
                plan.selectedCandidatesInQueueOrder
                    .map { candidate -> candidate.accountId }
                    .distinct()
                    .size == 4
            ) {
                "A formação ranqueada contém conta duplicada."
            }
            check(
                plan.selectedCandidatesInQueueOrder
                    .map { candidate -> candidate.playerId }
                    .distinct()
                    .size == 4
            ) {
                "A formação ranqueada contém jogador duplicado."
            }

            val roomSequence = nextRoomSequence++
            val roomId = "server-room-$roomSequence"
            val roomCode = roomSequence.toString().padStart(
                length = 4,
                padChar = '0',
            )
            val matchId = "server-match-${nextMatchSequence++}"
            val players =
                plan.candidatesBySeat.mapIndexed { seatIndex, candidate ->
                    OnlineRoomPlayerDto(
                        playerId = candidate.playerId,
                        name = candidate.playerName,
                        seatIndex = seatIndex,
                        connected = true,
                        participantType =
                            OnlineParticipantTypeDto.HUMAN,
                    )
                }
            val room = OnlineRoomSnapshotDto(
                roomId = roomId,
                roomCode = roomCode,
                hostPlayerId = players.first().playerId,
                status = OnlineRoomStatusDto.IN_MATCH,
                players = players,
                matchId = matchId,
                matchMode = DominoMatchMode.PUBLIC_RANKED,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
            val selectedAccountIdsInQueueOrder =
                plan.selectedCandidatesInQueueOrder.map { candidate ->
                    candidate.accountId
                }
            val accountIdsBySeat =
                plan.candidatesBySeat.map { candidate ->
                    candidate.accountId
                }
            val auditCommitment =
                createPublicRankedFormationAuditCommitment(
                    roomId = roomId,
                    matchId = matchId,
                    formedAtEpochMillis = nowEpochMillis,
                    selectedAccountIdsInQueueOrder =
                        selectedAccountIdsInQueueOrder,
                    accountIdsBySeat = accountIdsBySeat,
                    auditNonce = plan.auditNonce,
                )

            roomsById[roomId] = room
            roomIdsByCode[roomCode] = roomId
            selectedAccountIdsInQueueOrder.forEach { accountId ->
                publicRankedQueueByAccountId.remove(accountId)
            }
            publicRankedFormationHistory +=
                PublicRankedFormationHistoryEntry(
                    roomId = roomId,
                    matchId = matchId,
                    formedAtEpochMillis = nowEpochMillis,
                    selectedAccountIdsInQueueOrder =
                        selectedAccountIdsInQueueOrder,
                    accountIdsBySeat = accountIdsBySeat,
                    auditNonce = plan.auditNonce,
                    auditCommitment = auditCommitment,
                )
            trimPublicRankedFormationHistory()
            createMatch(
                room = room,
                matchId = matchId,
            )

            trace(
                level = OnlineTraceLevel.INFO,
                source = OnlineTraceSource.SERVER_STORE,
                type = OnlineTraceType.ROOM_CREATED,
                roomId = roomId,
                matchId = matchId,
                playerId = players.first().playerId,
                localSeatIndex = 0,
                attributes = room.traceAttributes() + mapOf(
                    "operation" to "public_ranked_queue_match",
                    "queueSizeAfterMatch" to
                        publicRankedQueueByAccountId.size.toString(),
                    "formationAuditCommitment" to auditCommitment,
                    "repeatedEncounterScore" to
                        plan.repeatedEncounterScore.toString(),
                    "repeatedPartnerScore" to
                        plan.repeatedPartnerScore.toString(),
                    "seatAssignmentAuthority" to "server",
                ),
            )
        }
    }

    private fun trimPublicRankedFormationHistory() {
        while (
            publicRankedFormationHistory.size >
                resourcePolicy.maxPublicRankedFormationHistoryCount
        ) {
            publicRankedFormationHistory.removeAt(0)
        }
    }

    private fun resolveRankedPlayerIdentityOrNull(
        identity: OnlineRequestIdentity?,
        expectedPlayerId: String,
    ): RankedMatchPlayerIdentity? {
        val normalizedExpectedPlayerId = expectedPlayerId.trim()
        val normalizedAccountId = identity
            ?.accountId
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank()
            }

        if (
            identity == null ||
            expectedPlayerId != normalizedExpectedPlayerId ||
            identity.kind != OnlinePrincipalKind.ACCOUNT ||
            identity.playerId != normalizedExpectedPlayerId ||
            normalizedAccountId == null
        ) {
            return null
        }

        val account = accountsByPlayerId[normalizedExpectedPlayerId]
            ?: return null

        if (account.accountId != normalizedAccountId) {
            return null
        }

        return RankedMatchPlayerIdentity(
            playerId = account.playerId,
            accountId = account.accountId,
        )
    }

    private fun resolveRankedPlayerIdentitiesBySeat(
        room: OnlineRoomSnapshotDto,
    ): List<RankedMatchPlayerIdentity> {
        check(room.matchMode == DominoMatchMode.PUBLIC_RANKED) {
            "Somente PUBLIC_RANKED exige identidades de conta."
        }
        check(
            room.players.size == 4 &&
                room.players.all { player ->
                    player.participantType ==
                        OnlineParticipantTypeDto.HUMAN &&
                        player.seatIndex != null
                }
        ) {
            "Partida ranqueada exige quatro assentos humanos."
        }

        val identities = room.players
            .sortedBy { player ->
                requireNotNull(player.seatIndex)
            }
            .map { player ->
                val account = requireNotNull(
                    accountsByPlayerId[player.playerId],
                ) {
                    "Jogador ranqueado sem conta persistente."
                }

                RankedMatchPlayerIdentity(
                    playerId = account.playerId,
                    accountId = account.accountId,
                )
            }

        check(
            identities.mapNotNull { identity ->
                identity.accountId
            }.distinct().size == identities.size
        ) {
            "Uma conta não pode ocupar mais de um assento ranqueado."
        }

        return identities
    }

    private fun requireStoreIdentifier(
        value: String,
        fieldName: String,
    ): String {
        return value.trim().also { normalizedValue ->
            require(
                normalizedValue.isNotBlank() &&
                    normalizedValue.length <=
                    MAX_SERVER_IDENTIFIER_CHARACTERS
            ) {
                "O $fieldName e invalido."
            }
        }
    }

    private fun pruneExpiredRecords(
        nowEpochMillis: Long,
        force: Boolean = false,
    ): Boolean {
        val previousPruneAtEpochMillis = lastPruneAtEpochMillis

        if (
            !force &&
            previousPruneAtEpochMillis != null &&
            nowEpochMillis >= previousPruneAtEpochMillis &&
            nowEpochMillis - previousPruneAtEpochMillis <
            resourcePolicy.pruneIntervalMillis
        ) {
            return false
        }

        lastPruneAtEpochMillis = nowEpochMillis

        val expiredQueueAccountIds = publicRankedQueueByAccountId.values
            .filter { entry ->
                (
                    nowEpochMillis - entry.lastSeenAtEpochMillis
                ).coerceAtLeast(0L) >=
                    resourcePolicy.publicRankedQueueEntryRetentionMillis
            }
            .map { entry -> entry.accountId }

        expiredQueueAccountIds.forEach { accountId ->
            publicRankedQueueByAccountId.remove(accountId)
        }

        val historySizeBeforePrune =
            publicRankedFormationHistory.size
        publicRankedFormationHistory.removeAll { entry ->
            val referenceEpochMillis =
                entry.completedAtEpochMillis
                    ?: entry.formedAtEpochMillis
            (
                nowEpochMillis - referenceEpochMillis
            ).coerceAtLeast(0L) >
                resourcePolicy
                    .publicRankedFormationHistoryRetentionMillis
        }
        val formationHistoryChanged =
            historySizeBeforePrune !=
                publicRankedFormationHistory.size

        val expiredRoomIds = roomsById.values
            .filter { room ->
                val lastUpdatedAtEpochMillis =
                    room.updatedAtEpochMillis
                        ?: room.createdAtEpochMillis
                        ?: return@filter false
                val ageMillis = (
                    nowEpochMillis - lastUpdatedAtEpochMillis
                ).coerceAtLeast(0L)

                when (room.status) {
                    OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
                        ageMillis >=
                                resourcePolicy.waitingRoomRetentionMillis
                    }

                    OnlineRoomStatusDto.FINISHED,
                    OnlineRoomStatusDto.CLOSED -> {
                        ageMillis >=
                                resourcePolicy.finalizedRoomRetentionMillis
                    }

                    OnlineRoomStatusDto.IN_MATCH -> false
                }
            }
            .map { room -> room.roomId }
            .toSet()

        if (expiredRoomIds.isEmpty()) {
            return expiredQueueAccountIds.isNotEmpty() ||
                formationHistoryChanged
        }

        val expiredMatchIds = matchesById.values
            .filter { matchRecord ->
                matchRecord.roomId in expiredRoomIds
            }
            .map { matchRecord ->
                matchRecord.matchId
            }
            .toSet()

        roomIdsByCode.entries.removeAll { (_, roomId) ->
            roomId in expiredRoomIds
        }
        expiredRoomIds.forEach { roomId ->
            roomsById.remove(roomId)
        }
        expiredMatchIds.forEach { matchId ->
            matchesById.remove(matchId)
        }
        actionResultsByKey.keys.removeAll { key ->
            key.matchId in expiredMatchIds
        }

        return true
    }

    private fun addDevelopmentBotsIfNeeded(
        players: List<OnlineRoomPlayerDto>,
    ): List<OnlineRoomPlayerDto> {
        if (!autoFillDevelopmentBotsAfterTwoHumanPlayers) {
            return players
        }

        if (players.size != 2) {
            return players
        }

        val occupiedSeatIndexes = players
            .mapNotNull { player ->
                player.seatIndex
            }
            .toSet()

        val developmentBots =
            (FIRST_DEVELOPMENT_BOT_SEAT_INDEX..LAST_DEVELOPMENT_BOT_SEAT_INDEX)
                .filter { seatIndex ->
                    seatIndex !in occupiedSeatIndexes
                }
                .map { seatIndex ->
                    OnlineRoomPlayerDto(
                        playerId = "$DEVELOPMENT_BOT_PLAYER_ID_PREFIX$seatIndex",
                        name = "Bot ${seatIndex + 1}",
                        seatIndex = seatIndex,
                        connected = true,
                        participantType =
                            OnlineParticipantTypeDto.APPLICATION,
                    )
                }

        return players + developmentBots
    }

    private fun createMatch(
        room: OnlineRoomSnapshotDto,
        matchId: String,
    ) {
        val matchMode = room.matchMode
        val classification = matchMode.rankedMatchClassification

        if (matchMode.contributesToRanking) {
            require(
                room.players.size == 4 &&
                        room.players.all { player ->
                            player.participantType ==
                                    OnlineParticipantTypeDto.HUMAN
                        },
            ) {
                "Partida ranqueada exige quatro jogadores humanos."
            }
        }

        val rankedPlayerIdentitiesBySeat =
            if (matchMode.contributesToRanking) {
                resolveRankedPlayerIdentitiesBySeat(
                    room = room,
                )
            } else {
                emptyList()
            }

        val gameState = applyOnlineRoomPlayerNames(
            gameState = createInitialDominoGameState(),
            room = room,
        )

        val runtimeState = DominoMatchRuntimeState(
            gameState = gameState,
            roundNumber = 1,
            localPlayerIndex = 0,
            phase = determineOnlineNextPhase(
                gameState = gameState,
            ),
            clockPolicy = clockPolicy,
            playerClockMillis = createInitialPlayerClockMillis(
                playerCount = gameState.players.size,
                clockPolicy = clockPolicy,
            ),
        )

        val snapshot = runtimeState.toOnlineSnapshotDto(
            roomId = room.roomId,
            matchId = matchId,
            revision = 1L,
            serverEpochMillis = nowEpochMillis(),
        )

        val matchRecord = MatchRecord(
            roomId = room.roomId,
            matchId = matchId,
            snapshot = snapshot,
            developmentBotSeatIndexes = room.players
                .filter { player ->
                    player.participantType ==
                        OnlineParticipantTypeDto.APPLICATION
                }
                .mapNotNull { player ->
                    player.seatIndex
                }
                .toSet(),
            matchMode = matchMode,
            classification = classification,
            rankedPlayerIdentitiesBySeat =
                rankedPlayerIdentitiesBySeat,
            rankedMetricAccumulator =
                RankedMatchMetricAccumulator.empty(
                    playerCount = gameState.players.size,
                    teamCount = gameState.teamScores.size,
                ),
        )

        recordSnapshotInHistory(
            matchRecord = matchRecord,
            snapshot = snapshot,
        )

        matchesById[matchId] = matchRecord

        trace(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            roomId = room.roomId,
            matchId = matchId,
            snapshotRevision = snapshot.revision,
            runtimeState = runtimeState,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "trigger" to "match_created",
                "previousRevision" to "null",
            ),
        )
    }

    /**
     * Processa no máximo uma transição autoritativa da partida.
     *
     * A redução parcial de relógio não gera snapshot novo; o cliente mantém a
     * contagem visual local entre revisões. Uma nova revisão é publicada apenas
     * quando há troca de estado de jogo: jogada automática, toque, timeout ou
     * ação de bot.
     */
    private fun advanceAuthoritativeMatch(
        matchRecord: MatchRecord,
        nowEpochMillis: Long,
        trigger: String,
        traceSource: OnlineTraceSource,
    ): Boolean {
        val currentSnapshot = matchRecord.snapshot
        val runtimeState = currentSnapshot.toRuntimeState(
            localPlayerIndex = 0,
        )

        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        val mandatoryPassRuntimeState = resolveMandatoryPassIfNeeded(
            runtimeState = runtimeState,
        )

        if (mandatoryPassRuntimeState != null) {
            trace(
                level = OnlineTraceLevel.INFO,
                source = traceSource,
                type = OnlineTraceType.AUTOMATIC_TURN_RESOLVED,
                roomId = matchRecord.roomId,
                matchId = matchRecord.matchId,
                snapshotRevision = currentSnapshot.revision,
                runtimeState = mandatoryPassRuntimeState,
                automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                attributes = mapOf(
                    "playerIndex" to currentPlayerIndex.toString(),
                    "reason" to "mandatory_pass",
                    "trigger" to trigger,
                ),
            )

            publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = mandatoryPassRuntimeState,
                serverEpochMillis = nowEpochMillis,
                trigger = "$trigger:mandatory_pass",
                traceSource = traceSource,
            )

            return true
        }

        val automaticSeatIndexesBefore =
            matchRecord.automaticSeatIndexes.toSet()

        val clockReduction = reduceClockAndRegisterAutomaticPlayer(
            runtimeState = runtimeState,
            automaticSeatIndexes = matchRecord.automaticSeatIndexes,
            automaticRoundSeatIndexes =
                matchRecord.automaticRoundSeatIndexes,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = nowEpochMillis,
            ),
        )

        if (clockReduction.turnWasResolved) {
            val currentPlayerBecameAutomatic =
                currentPlayerIndex !in automaticSeatIndexesBefore &&
                        currentPlayerIndex in matchRecord.automaticSeatIndexes

            if (currentPlayerBecameAutomatic) {
                trace(
                    level = OnlineTraceLevel.WARN,
                    source = traceSource,
                    type = OnlineTraceType.CLOCK_EXPIRED,
                    roomId = matchRecord.roomId,
                    matchId = matchRecord.matchId,
                    snapshotRevision = currentSnapshot.revision,
                    runtimeState = runtimeState,
                    automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                    attributes = mapOf(
                        "playerIndex" to currentPlayerIndex.toString(),
                        "trigger" to trigger,
                    ),
                )
            }

            trace(
                level = OnlineTraceLevel.INFO,
                source = traceSource,
                type = OnlineTraceType.AUTOMATIC_TURN_RESOLVED,
                roomId = matchRecord.roomId,
                matchId = matchRecord.matchId,
                snapshotRevision = currentSnapshot.revision,
                runtimeState = clockReduction.runtimeState,
                automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
                attributes = mapOf(
                    "playerIndex" to currentPlayerIndex.toString(),
                    "reason" to if (currentPlayerBecameAutomatic) {
                        "clock_expired"
                    } else {
                        "automatic_seat"
                    },
                    "trigger" to trigger,
                ),
            )

            publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = clockReduction.runtimeState,
                serverEpochMillis = nowEpochMillis,
                trigger = "$trigger:automatic_turn",
                traceSource = traceSource,
            )

            return true
        }

        val runtimeStateAfterBotTurn =
            advanceDevelopmentBotTurnIfNeeded(
                matchRecord = matchRecord,
                runtimeState = clockReduction.runtimeState,
            )

        if (runtimeStateAfterBotTurn == clockReduction.runtimeState) {
            return false
        }

        trace(
            level = OnlineTraceLevel.INFO,
            source = traceSource,
            type = OnlineTraceType.AUTOMATIC_TURN_RESOLVED,
            roomId = matchRecord.roomId,
            matchId = matchRecord.matchId,
            snapshotRevision = currentSnapshot.revision,
            runtimeState = runtimeStateAfterBotTurn,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "playerIndex" to currentPlayerIndex.toString(),
                "reason" to "development_bot",
                "trigger" to trigger,
            ),
        )

        publishMatchSnapshot(
            matchRecord = matchRecord,
            previousSnapshot = currentSnapshot,
            runtimeState = runtimeStateAfterBotTurn,
            serverEpochMillis = nowEpochMillis,
            trigger = "$trigger:development_bot",
            traceSource = traceSource,
        )

        return true
    }

    private fun resolveMandatoryPassIfNeeded(
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchRuntimeState? {
        val phase = runtimeState.phase as? DominoMatchPhase.PresentingPass
            ?: return null

        val gameState = runtimeState.gameState

        if (
            isRoundFinished(gameState) ||
            isGameFinished(gameState) ||
            phase.playerIndex != gameState.currentPlayerIndex
        ) {
            return null
        }

        val passedGameState = passTurn(
            state = gameState,
        )

        return runtimeState.copy(
            gameState = passedGameState,
            phase = determineOnlineNextPhase(
                gameState = passedGameState,
            ),
        )
    }

    private fun advanceDevelopmentBotTurnIfNeeded(
        matchRecord: MatchRecord,
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchRuntimeState {
        if (matchRecord.developmentBotSeatIndexes.isEmpty()) {
            return runtimeState
        }

        val gameState = runtimeState.gameState

        if (isRoundFinished(gameState) || isGameFinished(gameState)) {
            return runtimeState
        }

        val currentPlayerIndex = gameState.currentPlayerIndex

        if (currentPlayerIndex !in matchRecord.developmentBotSeatIndexes) {
            return runtimeState
        }

        val updatedGameState = findBasicBotMove(
            state = gameState,
        )?.let { move ->
            playMoveForCurrentPlayer(
                state = gameState,
                playableMove = move,
            )
        } ?: passTurn(
            state = gameState,
        )

        registerAutomaticPiecePlayIfNeeded(
            previousState = gameState,
            updatedState = updatedGameState,
            seatIndex = currentPlayerIndex,
            automaticRoundSeatIndexes =
                matchRecord.automaticRoundSeatIndexes,
        )

        return runtimeState.copy(
            gameState = updatedGameState,
            phase = determineOnlineNextPhase(
                gameState = updatedGameState,
            ),
        )
    }

    private fun submitGameAction(
        action: OnlinePlayerActionDto,
        seatIndex: Int,
        matchRecord: MatchRecord,
    ): OnlineActionResultDto {
        val currentSnapshot = matchRecord.snapshot
        val now = nowEpochMillis()
        val runtimeState = currentSnapshot.toRuntimeState(
            localPlayerIndex = seatIndex,
        )
        val clockReduction = reduceClockAndRegisterAutomaticPlayer(
            runtimeState = runtimeState,
            automaticSeatIndexes = matchRecord.automaticSeatIndexes,
            automaticRoundSeatIndexes =
                matchRecord.automaticRoundSeatIndexes,
            elapsedMillis = getElapsedMillisSinceSnapshot(
                snapshot = currentSnapshot,
                nowEpochMillis = now,
            ),
        )

        if (clockReduction.turnWasResolved) {
            val automaticResult = publishMatchSnapshot(
                matchRecord = matchRecord,
                previousSnapshot = currentSnapshot,
                runtimeState = clockReduction.runtimeState,
                serverEpochMillis = now,
                trigger = "game_action:automatic_turn",
            )

            return automaticResult.copy(
                accepted = false,
                reason = "Tempo esgotado.",
            )
        }

        val clockedSnapshot = clockReduction.runtimeState.toOnlineSnapshotDto(
            roomId = currentSnapshot.roomId,
            matchId = currentSnapshot.matchId,
            revision = currentSnapshot.revision,
            serverEpochMillis = now,
            automaticPlayerIndexes =
                matchRecord.automaticSeatIndexes.sorted(),
        )

        return when (
            val reduction = reduceOnlineGameAction(
                action = action,
                currentSnapshot = clockedSnapshot,
                seatIndex = seatIndex,
            )
        ) {
            is OnlineMatchActionReduction.Accepted -> {
                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = clockedSnapshot,
                    runtimeState = reduction.runtimeState,
                    serverEpochMillis = now,
                    action = action,
                    trigger = "game_action",
                )
            }

            is OnlineMatchActionReduction.Rejected -> {
                rejectedAction(
                    reason = reduction.reason,
                    revision = reduction.revision,
                )
            }
        }
    }

    private fun submitStartNextRound(
        action: OnlinePlayerActionDto,
        currentRoom: OnlineRoomSnapshotDto,
        matchRecord: MatchRecord,
    ): OnlineActionResultDto {
        return when (
            val reduction = reduceOnlineStartNextRoundAction(
                action = action,
                currentRoom = currentRoom,
                currentSnapshot = matchRecord.snapshot,
            )
        ) {
            is OnlineMatchActionReduction.Accepted -> {
                /*
                 * O modo automático é deliberadamente limitado à rodada.
                 * Ao começar a próxima rodada, todos recuperam o controle.
                 */
                matchRecord.automaticSeatIndexes.clear()
                matchRecord.automaticRoundSeatIndexes.clear()

                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = matchRecord.snapshot,
                    runtimeState = reduction.runtimeState,
                    action = action,
                    trigger = "start_next_round",
                )
            }

            is OnlineMatchActionReduction.Rejected -> {
                rejectedAction(
                    reason = reduction.reason,
                    revision = reduction.revision,
                )
            }
        }
    }

    private fun submitStartNewMatch(
        action: OnlinePlayerActionDto,
        currentRoom: OnlineRoomSnapshotDto,
        matchRecord: MatchRecord,
    ): OnlineActionResultDto {
        if (matchRecord.matchMode.contributesToRanking) {
            return rejectedAction(
                reason =
                    "Partida ranqueada concluída não pode ser reiniciada.",
                revision = matchRecord.snapshot.revision,
            )
        }

        return when (
            val reduction = reduceOnlineStartNewMatchAction(
                action = action,
                currentRoom = currentRoom,
                currentSnapshot = matchRecord.snapshot,
            )
        ) {
            is OnlineMatchActionReduction.Accepted -> {
                matchRecord.automaticSeatIndexes.clear()
                matchRecord.automaticRoundSeatIndexes.clear()
                matchRecord.rankedMetricAccumulator =
                    RankedMatchMetricAccumulator.empty(
                        playerCount = reduction
                            .runtimeState
                            .gameState
                            .players
                            .size,
                        teamCount = reduction
                            .runtimeState
                            .gameState
                            .teamScores
                            .size,
                    )

                publishMatchSnapshot(
                    matchRecord = matchRecord,
                    previousSnapshot = matchRecord.snapshot,
                    runtimeState = reduction.runtimeState,
                    action = action,
                    trigger = "start_new_match",
                )
            }

            is OnlineMatchActionReduction.Rejected -> {
                rejectedAction(
                    reason = reduction.reason,
                    revision = reduction.revision,
                )
            }
        }
    }

    private fun reduceClockAndRegisterAutomaticPlayer(
        runtimeState: DominoMatchRuntimeState,
        automaticSeatIndexes: MutableSet<Int>,
        automaticRoundSeatIndexes: MutableSet<Int>,
        elapsedMillis: Long,
    ): ClockReductionResult {
        val currentPlayerIndex = runtimeState.gameState.currentPlayerIndex

        if (
            shouldForceAutomaticTurnForMarkedCurrentPlayer(
                runtimeState = runtimeState,
                automaticSeatIndexes = automaticSeatIndexes,
            )
        ) {
            val forcedRuntimeState = forceAutomaticTurnForCurrentPlayer(
                runtimeState = runtimeState,
            )

            registerAutomaticPiecePlayIfNeeded(
                previousState = runtimeState.gameState,
                updatedState = forcedRuntimeState.gameState,
                seatIndex = currentPlayerIndex,
                automaticRoundSeatIndexes = automaticRoundSeatIndexes,
            )

            return ClockReductionResult(
                runtimeState = forcedRuntimeState,
                turnWasResolved = true,
            )
        }

        val currentPlayerWasAlreadyExpired =
            shouldForceAutomaticTurnForExpiredCurrentPlayer(
                runtimeState = runtimeState,
            )

        val clockedRuntimeState = reduceOnlineAuthoritativeClock(
            runtimeState = runtimeState,
            elapsedMillis = elapsedMillis,
        )

        val turnWasResolved = wasTurnResolvedByClockOrTimeout(
            previousRuntimeState = runtimeState,
            updatedRuntimeState = clockedRuntimeState,
        )

        if (currentPlayerWasAlreadyExpired || turnWasResolved) {
            automaticSeatIndexes.add(currentPlayerIndex)
        }

        if (turnWasResolved) {
            registerAutomaticPiecePlayIfNeeded(
                previousState = runtimeState.gameState,
                updatedState = clockedRuntimeState.gameState,
                seatIndex = currentPlayerIndex,
                automaticRoundSeatIndexes = automaticRoundSeatIndexes,
            )

            return ClockReductionResult(
                runtimeState = clockedRuntimeState,
                turnWasResolved = true,
            )
        }

        val currentPlayerIsNowExpired =
            shouldForceAutomaticTurnForExpiredCurrentPlayer(
                runtimeState = clockedRuntimeState,
            )

        if (!currentPlayerIsNowExpired) {
            return ClockReductionResult(
                runtimeState = clockedRuntimeState,
                turnWasResolved = false,
            )
        }

        automaticSeatIndexes.add(currentPlayerIndex)

        val forcedRuntimeState = forceAutomaticTurnForCurrentPlayer(
            runtimeState = clockedRuntimeState,
        )

        registerAutomaticPiecePlayIfNeeded(
            previousState = clockedRuntimeState.gameState,
            updatedState = forcedRuntimeState.gameState,
            seatIndex = currentPlayerIndex,
            automaticRoundSeatIndexes = automaticRoundSeatIndexes,
        )

        return ClockReductionResult(
            runtimeState = forcedRuntimeState,
            turnWasResolved = true,
        )
    }

    private fun registerAutomaticPiecePlayIfNeeded(
        previousState: DominoGameState,
        updatedState: DominoGameState,
        seatIndex: Int,
        automaticRoundSeatIndexes: MutableSet<Int>,
    ) {
        if (
            didRankedSeatPlayPiece(
                previousState = previousState,
                updatedState = updatedState,
                seatIndex = seatIndex,
            )
        ) {
            automaticRoundSeatIndexes.add(seatIndex)
        }
    }

    private fun shouldForceAutomaticTurnForMarkedCurrentPlayer(
        runtimeState: DominoMatchRuntimeState,
        automaticSeatIndexes: Set<Int>,
    ): Boolean {
        val gameState = runtimeState.gameState

        if (isRoundFinished(gameState) || isGameFinished(gameState)) {
            return false
        }

        if (gameState.currentPlayerIndex !in automaticSeatIndexes) {
            return false
        }

        return when (val phase = runtimeState.phase) {
            DominoMatchPhase.WaitingForLocalMove -> true

            is DominoMatchPhase.PresentingPass -> {
                phase.playerIndex == gameState.currentPlayerIndex
            }

            else -> false
        }
    }

    private fun shouldForceAutomaticTurnForExpiredCurrentPlayer(
        runtimeState: DominoMatchRuntimeState,
    ): Boolean {
        if (!runtimeState.clockPolicy.enabled) {
            return false
        }

        if (runtimeState.phase != DominoMatchPhase.WaitingForLocalMove) {
            return false
        }

        val gameState = runtimeState.gameState

        if (isRoundFinished(gameState) || isGameFinished(gameState)) {
            return false
        }

        return isPlayerClockExpired(
            clocks = runtimeState.playerClockMillis,
            playerIndex = gameState.currentPlayerIndex,
        )
    }

    private fun forceAutomaticTurnForCurrentPlayer(
        runtimeState: DominoMatchRuntimeState,
    ): DominoMatchRuntimeState {
        val updatedGameState = when (val phase = runtimeState.phase) {
            DominoMatchPhase.WaitingForLocalMove -> {
                val move = findRandomPlayableMove(
                    state = runtimeState.gameState,
                )

                if (move != null) {
                    playMoveForCurrentPlayer(
                        state = runtimeState.gameState,
                        playableMove = move,
                    )
                } else {
                    passTurn(
                        state = runtimeState.gameState,
                    )
                }
            }

            is DominoMatchPhase.PresentingPass -> {
                if (phase.playerIndex == runtimeState.gameState.currentPlayerIndex) {
                    passTurn(
                        state = runtimeState.gameState,
                    )
                } else {
                    runtimeState.gameState
                }
            }

            else -> runtimeState.gameState
        }

        return runtimeState.copy(
            gameState = updatedGameState,
            phase = determineOnlineNextPhase(
                gameState = updatedGameState,
            ),
        )
    }

    private fun wasTurnResolvedByClockOrTimeout(
        previousRuntimeState: DominoMatchRuntimeState,
        updatedRuntimeState: DominoMatchRuntimeState,
    ): Boolean {
        return updatedRuntimeState.gameState != previousRuntimeState.gameState ||
                updatedRuntimeState.phase != previousRuntimeState.phase
    }

    private fun publishMatchSnapshot(
        matchRecord: MatchRecord,
        previousSnapshot: OnlineMatchSnapshotDto,
        runtimeState: DominoMatchRuntimeState,
        serverEpochMillis: Long = nowEpochMillis(),
        action: OnlinePlayerActionDto? = null,
        trigger: String,
        traceSource: OnlineTraceSource = OnlineTraceSource.SERVER_STORE,
    ): OnlineActionResultDto {
        matchRecord.rankedMetricAccumulator =
            accumulateRankedMatchTransition(
                accumulator = matchRecord.rankedMetricAccumulator,
                previousState = previousSnapshot.toRuntimeState(
                    localPlayerIndex = 0,
                ).gameState,
                updatedState = runtimeState.gameState,
                automaticSeatIndexes =
                    matchRecord.automaticRoundSeatIndexes,
            )

        if (
            matchRecord.matchMode.contributesToRanking &&
            matchRecord.classification ==
                matchRecord.matchMode.rankedMatchClassification &&
            isGameFinished(runtimeState.gameState)
        ) {
            materializeRankedMatchResult(
                matchRecord = matchRecord,
                finalState = runtimeState.gameState,
                completedAtEpochMillis = serverEpochMillis,
            )
        }

        val updatedSnapshot = runtimeState.toOnlineSnapshotDto(
            roomId = previousSnapshot.roomId,
            matchId = previousSnapshot.matchId,
            revision = previousSnapshot.revision + 1L,
            serverEpochMillis = serverEpochMillis,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes.sorted(),
        )

        matchRecord.snapshot = updatedSnapshot

        recordSnapshotInHistory(
            matchRecord = matchRecord,
            snapshot = updatedSnapshot,
        )

        val currentRoom = roomsById[matchRecord.roomId]

        if (currentRoom != null) {
            roomsById[currentRoom.roomId] = currentRoom.copy(
                status = if (
                    runtimeState.gameState.gameWinnerTeamIndex != null
                ) {
                    OnlineRoomStatusDto.FINISHED
                } else {
                    OnlineRoomStatusDto.IN_MATCH
                },
                updatedAtEpochMillis = serverEpochMillis,
            )
        }

        trace(
            level = OnlineTraceLevel.INFO,
            source = traceSource,
            type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            action = action,
            roomId = updatedSnapshot.roomId,
            matchId = updatedSnapshot.matchId,
            snapshotRevision = updatedSnapshot.revision,
            runtimeState = runtimeState,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "trigger" to trigger,
                "previousRevision" to previousSnapshot.revision.toString(),
            ),
        )

        return OnlineActionResultDto(
            accepted = true,
            revision = updatedSnapshot.revision,
        )
    }

    private fun materializeRankedMatchResult(
        matchRecord: MatchRecord,
        finalState: DominoGameState,
        completedAtEpochMillis: Long,
    ) {
        check(matchRecord.matchMode == DominoMatchMode.PUBLIC_RANKED) {
            "Somente PUBLIC_RANKED pode materializar resultado ranqueado."
        }
        check(
            matchRecord.classification ==
                RankedMatchClassification.RANKED
        ) {
            "Modalidade competitiva com classificação inconsistente."
        }

        val currentRoom = requireNotNull(
            roomsById[matchRecord.roomId],
        ) {
            "Partida ranqueada referencia sala inexistente."
        }
        check(currentRoom.matchMode == matchRecord.matchMode) {
            "A modalidade da sala diverge da partida ranqueada."
        }

        val resultId = createRankedMatchResultId(
            matchId = matchRecord.matchId,
        )
        val existingResult = rankedResultsById[resultId]
        val stableCompletedAtEpochMillis =
            existingResult?.completedAtEpochMillis
                ?: completedAtEpochMillis

        val playerIdentitiesBySeat =
            matchRecord.rankedPlayerIdentitiesBySeat

        check(
            playerIdentitiesBySeat.size ==
                finalState.players.size &&
                playerIdentitiesBySeat.all { identity ->
                    !identity.accountId.isNullOrBlank()
                }
        ) {
            "Partida ranqueada sem quatro contas autenticadas."
        }

        check(
            currentRoom.players
                .sortedBy { player -> player.seatIndex }
                .map { player -> player.playerId } ==
                playerIdentitiesBySeat.map { identity ->
                    identity.playerId
                }
        ) {
            "Identidades ranqueadas divergem dos assentos da sala."
        }

        val candidate = buildRankedMatchResult(
            matchId = matchRecord.matchId,
            completedAtEpochMillis =
                stableCompletedAtEpochMillis,
            finalState = finalState,
            playerIdentitiesBySeat = playerIdentitiesBySeat,
            accumulator = matchRecord.rankedMetricAccumulator,
        )

        check(
            existingResult == null ||
                    existingResult == candidate,
        ) {
            "Resultado ranqueado conflitante para a mesma partida."
        }

        if (existingResult == null) {
            val immutableCycleId = rankedCycleSnapshotsById.values
                .asSequence()
                .map { snapshot -> snapshot.period }
                .firstOrNull { period ->
                    period.rankingRuleVersion ==
                        candidate.rankingRuleVersion &&
                        period.contains(
                            candidate.completedAtEpochMillis,
                        )
                }
                ?.cycleId

            check(immutableCycleId == null) {
                "Resultado ranqueado tardio para ciclo encerrado: " +
                    immutableCycleId
            }
        }

        rankedResultsById[resultId] = candidate

        val formationIndex =
            publicRankedFormationHistory.indexOfFirst { entry ->
                entry.matchId == matchRecord.matchId
            }
        if (formationIndex >= 0) {
            val formation =
                publicRankedFormationHistory[formationIndex]
            publicRankedFormationHistory[formationIndex] =
                formation.copy(
                    completedAtEpochMillis =
                        stableCompletedAtEpochMillis,
                )
        }
    }

    private fun recordSnapshotInHistory(
        matchRecord: MatchRecord,
        snapshot: OnlineMatchSnapshotDto,
    ) {
        matchRecord.revisionHistory.addLast(snapshot)

        while (
            matchRecord.revisionHistory.size >
            MATCH_REVISION_HISTORY_CAPACITY
        ) {
            matchRecord.revisionHistory.removeFirst()
        }
    }

    private fun markPlayerDisconnected(
        roomId: String,
        playerId: String,
    ) {
        val currentRoom = roomsById[roomId] ?: return

        roomsById[roomId] = currentRoom.copy(
            players = currentRoom.players.map { player ->
                if (player.playerId == playerId) {
                    player.copy(
                        connected = false,
                    )
                } else {
                    player
                }
            },
            updatedAtEpochMillis = nowEpochMillis(),
        )
    }

    /**
     * Devolve ao dono humano o controle temporariamente assumido pelo
     * servidor. A reconexão já ocorre sob o lock autoritativo, portanto a
     * troca acontece entre ações e nunca no meio de uma redução de jogada.
     *
     * O snapshot preserva o mesmo instante-base do relógio. Assim, publicar a
     * troca de controlador não concede tempo adicional ao jogador da vez.
     */
    private fun reclaimHumanSeatControlIfNeeded(
        room: OnlineRoomSnapshotDto,
        player: OnlineRoomPlayerDto,
    ): Boolean {
        if (
            room.status != OnlineRoomStatusDto.IN_MATCH ||
            player.participantType != OnlineParticipantTypeDto.HUMAN
        ) {
            return false
        }

        val matchId = room.matchId ?: return false
        val seatIndex = player.seatIndex ?: return false
        val matchRecord = matchesById[matchId] ?: return false

        if (!matchRecord.automaticSeatIndexes.remove(seatIndex)) {
            return false
        }

        val previousSnapshot = matchRecord.snapshot
        val updatedSnapshot = previousSnapshot.copy(
            revision = previousSnapshot.revision + 1L,
            automaticPlayerIndexes =
                matchRecord.automaticSeatIndexes.sorted(),
        )

        matchRecord.snapshot = updatedSnapshot
        recordSnapshotInHistory(
            matchRecord = matchRecord,
            snapshot = updatedSnapshot,
        )

        trace(
            level = OnlineTraceLevel.INFO,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.SNAPSHOT_PUBLISHED,
            roomId = room.roomId,
            matchId = matchId,
            playerId = player.playerId,
            localSeatIndex = seatIndex,
            snapshotRevision = updatedSnapshot.revision,
            runtimeState = updatedSnapshot.toRuntimeState(
                localPlayerIndex = seatIndex,
            ),
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = mapOf(
                "trigger" to "human_reconnected:control_reclaimed",
                "previousRevision" to
                    previousSnapshot.revision.toString(),
            ),
        )

        return true
    }

    private fun getElapsedMillisSinceSnapshot(
        snapshot: OnlineMatchSnapshotDto,
        nowEpochMillis: Long,
    ): Long {
        val previousEpochMillis = snapshot.serverEpochMillis
            ?: return 0L

        return (nowEpochMillis - previousEpochMillis)
            .coerceAtLeast(0L)
    }

    private fun OnlinePlayerActionDto.toActionResultCacheKey(): ActionResultCacheKey {
        return ActionResultCacheKey(
            matchId = matchId,
            playerId = playerId,
            actionId = actionId,
        )
    }

    private fun OnlinePlayerActionDto.validationReasonOrNull(): String? {
        val identifiers = listOf(
            "roomId" to roomId,
            "matchId" to matchId,
            "playerId" to playerId,
            "actionId" to actionId,
        )

        val invalidIdentifier = identifiers.firstOrNull { (_, value) ->
            value.isBlank() ||
                    value.length > MAX_SERVER_IDENTIFIER_CHARACTERS
        }

        if (invalidIdentifier != null) {
            return "${invalidIdentifier.first} ausente ou acima do limite."
        }

        if (revision < 0L) {
            return "Revisão negativa não é permitida."
        }

        return null
    }

    private fun cacheActionResult(
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
    ): OnlineActionResultDto {
        val cachedResult = result.copy(
            actionId = action.actionId,
        )

        actionResultsByKey[action.toActionResultCacheKey()] = cachedResult

        while (
            actionResultsByKey.size >
            resourcePolicy.maxActionResultCount
        ) {
            val iterator = actionResultsByKey.entries.iterator()

            check(iterator.hasNext()) {
                "O cache de idempotência perdeu seu estado."
            }

            iterator.next()
            iterator.remove()
        }

        return cachedResult
    }

    private fun cacheRejectedAction(
        action: OnlinePlayerActionDto,
        reason: String,
        revision: Long? = null,
    ): OnlineActionResultDto {
        val result = cacheActionResult(
            action = action,
            result = rejectedAction(
                reason = reason,
                revision = revision,
            ),
        )

        trace(
            level = OnlineTraceLevel.WARN,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.ACTION_REJECTED,
            action = action,
            snapshotRevision = result.revision,
            attributes = action.traceAttributes() + mapOf(
                "reason" to reason.take(180),
            ),
        )

        return result
    }

    private fun traceActionResult(
        action: OnlinePlayerActionDto,
        result: OnlineActionResultDto,
        matchRecord: MatchRecord,
        localSeatIndex: Int,
    ) {
        val runtimeState = matchRecord.snapshot.toRuntimeState(
            localPlayerIndex = localSeatIndex,
        )

        trace(
            level = if (result.accepted) {
                OnlineTraceLevel.INFO
            } else {
                OnlineTraceLevel.WARN
            },
            source = OnlineTraceSource.SERVER_STORE,
            type = if (result.accepted) {
                OnlineTraceType.ACTION_ACCEPTED
            } else {
                OnlineTraceType.ACTION_REJECTED
            },
            action = action,
            snapshotRevision = result.revision,
            runtimeState = runtimeState,
            automaticPlayerIndexes = matchRecord.automaticSeatIndexes,
            attributes = action.traceAttributes() + mapOf(
                "reason" to result.reason.orEmpty().take(180),
            ),
        )
    }

    private fun rejectedRoomOperationWithTrace(
        operation: String,
        reason: String,
        roomId: String? = null,
        matchId: String? = null,
        playerId: String? = null,
    ): OnlineRoomOperationResultDto {
        trace(
            level = OnlineTraceLevel.WARN,
            source = OnlineTraceSource.SERVER_STORE,
            type = OnlineTraceType.ACTION_REJECTED,
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            attributes = mapOf(
                "operation" to operation,
                "reason" to reason.take(180),
            ),
        )

        return rejectedRoomOperation(
            reason = reason,
        )
    }

    private fun trace(
        level: OnlineTraceLevel,
        source: OnlineTraceSource,
        type: OnlineTraceType,
        action: OnlinePlayerActionDto? = null,
        roomId: String? = null,
        matchId: String? = null,
        playerId: String? = null,
        localSeatIndex: Int? = null,
        snapshotRevision: Long? = null,
        runtimeState: DominoMatchRuntimeState? = null,
        automaticPlayerIndexes: Set<Int> = emptySet(),
        attributes: Map<String, String> = emptyMap(),
    ) {
        traceLogger.log(
            level = level,
            source = source,
            type = type,
            context = OnlineTraceContext(
                roomId = roomId ?: action?.roomId,
                matchId = matchId ?: action?.matchId,
                playerId = playerId ?: action?.playerId,
                localSeatIndex = localSeatIndex,
                actionId = action?.actionId,
                actionRevision = action?.revision,
                snapshotRevision = snapshotRevision,
            ),
            state = runtimeState?.toTraceStateSummary(
                automaticPlayerIndexes = automaticPlayerIndexes,
            ),
            attributes = attributes,
        )
    }

    private fun DominoMatchRuntimeState.toTraceStateSummary(
        automaticPlayerIndexes: Set<Int>,
    ): OnlineTraceStateSummary {
        return OnlineTraceStateSummary(
            roundNumber = roundNumber,
            phase = phase.traceName(),
            currentPlayerIndex = gameState.currentPlayerIndex,
            boardPieceCount = gameState.board.size,
            teamScores = gameState.teamScores,
            playerClockMillis = playerClockMillis,
            automaticPlayerIndexes = automaticPlayerIndexes.sorted(),
            stateFingerprint = createOnlineTraceStateFingerprint(
                runtimeState = this,
                automaticPlayerIndexes = automaticPlayerIndexes,
            ),
        )
    }

    private fun DominoMatchPhase.traceName(): String {
        return when (this) {
            DominoMatchPhase.RoundIntro -> "ROUND_INTRO"

            DominoMatchPhase.WaitingForLocalMove ->
                "WAITING_FOR_LOCAL_MOVE"

            is DominoMatchPhase.PresentingMove ->
                "PRESENTING_MOVE"

            is DominoMatchPhase.PresentingPass ->
                "PRESENTING_PASS"

            DominoMatchPhase.RoundSummary -> "ROUND_SUMMARY"

            DominoMatchPhase.MatchFinished -> "MATCH_FINISHED"
        }
    }

    private fun OnlineRoomSnapshotDto.traceAttributes(): Map<String, String> {
        return mapOf(
            "roomStatus" to status.name,
            "matchMode" to matchMode.name,
            "playerCount" to players.size.toString(),
            "roomCode" to roomCode,
        )
    }

    private fun OnlinePlayerActionDto.traceAttributes(): Map<String, String> {
        val attributes = mutableMapOf(
            "actionType" to type.name,
        )

        move?.let { onlineMove ->
            attributes["piece"] =
                "${onlineMove.piece.left}-${onlineMove.piece.right}"
            attributes["boardSide"] = onlineMove.side.name
            attributes["flipped"] = onlineMove.flipped.toString()
        }

        return attributes
    }

    private fun rejectedRoomOperation(
        reason: String,
    ): OnlineRoomOperationResultDto {
        return OnlineRoomOperationResultDto(
            accepted = false,
            reason = reason,
        )
    }

    private fun rejectedAction(
        reason: String,
        revision: Long? = null,
    ): OnlineActionResultDto {
        return OnlineActionResultDto(
            accepted = false,
            revision = revision,
            reason = reason,
        )
    }


}
