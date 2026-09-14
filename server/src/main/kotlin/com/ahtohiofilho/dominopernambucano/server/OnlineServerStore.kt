package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.CURRENT_RANKING_RULE_VERSION
import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomLeaveRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomSeatChangeRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
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

enum class OnlineAccountDeletionResult {
    DELETED,
    NOT_FOUND,
    FORBIDDEN,
    ACTIVE_PARTICIPATION,
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
     * Promotes or confirms an externally controlled synthetic account.
     *
     * This boundary is called only after server-side provisioning
     * authorization. Synthetic remains distinct from APPLICATION: the latter
     * is reserved for server-owned development bots.
     */
    fun promoteSyntheticAccount(
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

    /**
     * Vincula a identidade EMAIL e a credencial de senha como uma única
     * operação autoritativa. Implementações persistentes devem gravar ambas
     * juntas ou nenhuma delas.
     *
     * O corpo padrão preserva compatibilidade de stores auxiliares. Os stores
     * autoritativos de produção sobrescrevem este método com rollback/commit
     * atômico.
     */
    fun registerEmailPasswordIdentity(
        playerId: String,
        expectedAccountId: String? = null,
        subject: String,
        credential: OnlineServerPasswordCredential,
    ): OnlineExternalIdentityLinkResult {
        return when (
            val result = linkExternalIdentity(
                playerId = playerId,
                expectedAccountId = expectedAccountId,
                provider = OnlineExternalIdentityProvider.EMAIL,
                subject = subject,
            )
        ) {
            is OnlineExternalIdentityLinkResult.Linked -> {
                val updatedAccount = setAccountPasswordCredential(
                    accountId = result.account.accountId,
                    credential = credential,
                )
                if (updatedAccount == null) {
                    OnlineExternalIdentityLinkResult.Conflict
                } else {
                    OnlineExternalIdentityLinkResult.Linked(
                        account = updatedAccount,
                    )
                }
            }

            OnlineExternalIdentityLinkResult.Conflict ->
                OnlineExternalIdentityLinkResult.Conflict
        }
    }

    fun findAccountByExternalIdentity(
        provider: OnlineExternalIdentityProvider,
        subject: String,
    ): OnlineServerAccount?

    fun getAccountPasswordCredential(
        accountId: String,
    ): OnlineServerPasswordCredential?

    fun setAccountPasswordCredential(
        accountId: String,
        credential: OnlineServerPasswordCredential,
    ): OnlineServerAccount?

    /**
     * Resolves an existing synthetic account for authorized session renewal.
     */
    fun findSyntheticAccount(
        accountId: String,
    ): OnlineServerAccount?

    fun isAccountIdentityActive(
        accountId: String,
        playerId: String,
    ): Boolean

    fun deleteHumanAccount(
        accountId: String,
        playerId: String,
    ): OnlineAccountDeletionResult

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

    fun getClosedRankedCycleSnapshot(
        cycleId: String,
    ): RankedCycleSnapshot?

    fun listClosedRankedCycleSnapshots(
        kind: RankingCycleKind,
        offset: Int,
        limit: Int,
    ): RankedCycleSnapshotPage

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

    /**
     * Moves only the authenticated/requesting participant in a waiting
     * PRIVATE_UNRANKED room. If the destination is occupied, the two seats
     * are swapped atomically by the authoritative store.
     */
    fun movePrivateRoomSeat(
        request: PrivateRoomSeatChangeRequestDto,
    ): OnlineRoomOperationResultDto

    /**
     * Removes the requesting participant from a waiting PRIVATE_UNRANKED
     * lobby. A host departure closes the lobby instead of transferring host
     * authority.
     */
    fun leavePrivateRoom(
        request: PrivateRoomLeaveRequestDto,
    ): OnlineRoomOperationResultDto

    /**
     * Starts a waiting PRIVATE_UNRANKED room only when the requesting
     * participant is the room host and all four connected seats exist.
     */
    fun startPrivateRoom(
        request: PrivateRoomStartRequestDto,
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
