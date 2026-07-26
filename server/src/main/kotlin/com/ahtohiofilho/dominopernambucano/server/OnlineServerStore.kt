package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfile
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto

enum class OnlineServerStoreReadiness {
    READY,
    UNAVAILABLE,
}

sealed interface OnlineExternalIdentityLinkResult {
    data class Linked(
        val account: OnlineServerAccount,
    ) : OnlineExternalIdentityLinkResult

    data object Conflict : OnlineExternalIdentityLinkResult
}

interface OnlineServerStore : AutoCloseable {
    /**
     * Promove um player autenticado para uma única conta persistente.
     *
     * Quando expectedAccountId é nulo, cria ou reutiliza a conta do player.
     * Quando não é nulo, somente confirma a conta já vinculada, permitindo
     * retries autenticados com uma credencial ACCOUNT sem criar novo vínculo.
     */
    fun promoteAccount(
        playerId: String,
        expectedAccountId: String? = null,
    ): OnlineServerAccount?

    /**
     * Vincula uma identidade externa sem fundir contas existentes.
     * A mesma operacao e idempotente apenas para a conta canonica original.
     */
    fun linkExternalIdentity(
        playerId: String,
        expectedAccountId: String? = null,
        provider: OnlineExternalIdentityProvider,
        subject: String,
    ): OnlineExternalIdentityLinkResult

    fun findAccountByExternalIdentity(
        provider: OnlineExternalIdentityProvider,
        subject: String,
    ): OnlineServerAccount?

    fun getAccountProfile(
        accountId: String,
    ): OnlineAccountProfile?

    fun updateAccountProfile(
        accountId: String,
        publicDisplayName: String,
        tableName: String? = null,
    ): OnlineAccountProfile?

    /**
     * One in-memory batch read for public ranking projection.
     *
     * Implementations must not perform one persistence read per account.
     */
    fun getPublicDisplayNames(
        accountIds: Set<String>,
    ): Map<String, String>

    /**
     * Derived competitive ladder for one canonical period.
     *
     * Immutable ranked results remain the only source of truth. Implementations
     * must not persist a second aggregate when answering this query.
     */
    fun getRankedCycleLadder(
        kind: RankingCycleKind,
        completedAtEpochMillis: Long,
        rankingRuleVersion: Int = CURRENT_RANKING_RULE_VERSION,
    ): RankedCycleLadder

    /**
     * Server-owned competitive admission boundary.
     *
     * Enters the FIFO queue for the single public ranked pool. The caller
     * cannot select a room, code, seat, partner, or opponent.
     */
    fun enqueuePublicRanked(
        request: CreateOnlineRoomRequestDto,
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult

    fun cancelPublicRankedQueue(
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult

    fun getPublicRankedQueueStatus(
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult

    fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto

    fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto

    /**
     * Avança o relógio autoritativo e informa se o estado persistível mudou.
     */
    fun advanceAuthoritativeTime(): Boolean

    fun getRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto?

    fun isRoomParticipant(
        roomId: String,
        playerId: String,
    ): Boolean

    fun isMatchParticipant(
        matchId: String,
        playerId: String,
    ): Boolean

    fun getMatchSnapshotForParticipant(
        matchId: String,
        playerId: String,
    ): OnlineMatchSnapshotDto?

    fun getMatchSnapshotsAfterForParticipant(
        matchId: String,
        playerId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>?

    fun getMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto?

    fun getMatchSnapshotsAfter(
        matchId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>?

    fun readiness(): OnlineServerStoreReadiness =
        OnlineServerStoreReadiness.READY

    override fun close() = Unit
}
