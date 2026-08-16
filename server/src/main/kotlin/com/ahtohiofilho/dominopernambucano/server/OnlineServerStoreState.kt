package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchMetricAccumulator
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfile
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import kotlinx.serialization.Serializable

const val ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION = 11
const val MINIMUM_SUPPORTED_ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION = 1

@Serializable
data class OnlineServerStoreState(
    val schemaVersion: Int = ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION,
    val nextRoomSequence: Int = 1,
    val nextMatchSequence: Int = 1,
    val rooms: List<OnlineRoomSnapshotDto> = emptyList(),
    val matches: List<OnlineServerStoredMatch> = emptyList(),
    val actionResults: List<OnlineServerStoredActionResult> = emptyList(),
    val rankedResults: List<RankedMatchResult> = emptyList(),
    val rankedCycleSnapshots: List<RankedCycleSnapshot> = emptyList(),
    val accounts: List<OnlineServerAccount> = emptyList(),
    val externalIdentities: List<OnlineServerExternalIdentity> = emptyList(),
    val publicRankedFormationHistory:
        List<PublicRankedFormationHistoryEntry> = emptyList(),
) {
    companion object {
        val Empty = OnlineServerStoreState()
    }
}

@Serializable
data class OnlineServerAccount(
    val accountId: String,
    val playerId: String,
    val createdAtEpochMillis: Long,
    val publicDisplayName: String? = null,
    val tableName: String? = null,
    val profileUpdatedAtEpochMillis: Long? = null,
    val participantType: OnlineParticipantTypeDto =
        OnlineParticipantTypeDto.HUMAN,
)

internal fun OnlineServerAccount.toOnlineAccountProfileOrNull():
    OnlineAccountProfile? {
    val resolvedPublicDisplayName =
        publicDisplayName ?: return null
    val resolvedTableName = tableName ?: return null
    val resolvedUpdatedAtEpochMillis =
        profileUpdatedAtEpochMillis ?: return null

    return OnlineAccountProfile(
        publicDisplayName = resolvedPublicDisplayName,
        tableName = resolvedTableName,
        updatedAtEpochMillis = resolvedUpdatedAtEpochMillis,
    )
}

@Serializable
enum class OnlineExternalIdentityProvider {
    GOOGLE,
    EMAIL,
}

@Serializable
data class OnlineServerExternalIdentity(
    val provider: OnlineExternalIdentityProvider,
    val subject: String,
    val accountId: String,
    val linkedAtEpochMillis: Long,
)

@Serializable
data class PublicRankedFormationHistoryEntry(
    val roomId: String,
    val matchId: String,
    val formedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val selectedAccountIdsInQueueOrder: List<String>,
    val accountIdsBySeat: List<String>,
    val auditNonce: String,
    val auditCommitment: String,
)

@Serializable
data class OnlineServerStoredMatch(
    val roomId: String,
    val matchId: String,
    val snapshot: OnlineMatchSnapshotDto,
    val revisionHistory: List<OnlineMatchSnapshotDto>,
    val automaticSeatIndexes: List<Int> = emptyList(),
    val automaticRoundSeatIndexes: List<Int> = emptyList(),
    val applicationSeatIndexes: List<Int> = emptyList(),
    val matchMode: DominoMatchMode =
        DominoMatchMode.PRIVATE_UNRANKED,
    val classification: RankedMatchClassification =
        matchMode.rankedMatchClassification,
    val rankedMetricAccumulator: RankedMatchMetricAccumulator? = null,
)

@Serializable
data class OnlineServerStoredActionResult(
    val matchId: String,
    val playerId: String,
    val actionId: String,
    val result: OnlineActionResultDto,
)

data class OnlineServerStoreResourcePolicy(
    val maxRoomCount: Int = 1_024,
    val maxActionResultCount: Int = 32_768,
    val maxPublicRankedQueueSize: Int = 16_384,
    val publicRankedQueueEntryRetentionMillis: Long = 2L * 60L * 1_000L,
    val publicRankedFormationLookaheadSize: Int = 8,
    val publicRankedExactCohortCooldownMillis: Long =
        30L * 60L * 1_000L,
    val publicRankedFormationHistoryRetentionMillis: Long =
        24L * 60L * 60L * 1_000L,
    val maxPublicRankedFormationHistoryCount: Int = 4_096,
    val waitingRoomRetentionMillis: Long = 6L * 60L * 60L * 1_000L,
    val finalizedRoomRetentionMillis: Long = 1L * 60L * 60L * 1_000L,
    val pruneIntervalMillis: Long = 60L * 1_000L,
) {
    init {
        require(maxRoomCount > 0)
        require(maxActionResultCount > 0)
        require(maxPublicRankedQueueSize >= 4)
        require(publicRankedQueueEntryRetentionMillis > 0L)
        require(publicRankedFormationLookaheadSize >= 4)
        require(publicRankedExactCohortCooldownMillis >= 0L)
        require(publicRankedFormationHistoryRetentionMillis > 0L)
        require(maxPublicRankedFormationHistoryCount > 0)
        require(waitingRoomRetentionMillis > 0L)
        require(finalizedRoomRetentionMillis > 0L)
        require(pruneIntervalMillis > 0L)
    }

    companion object {
        val Default = OnlineServerStoreResourcePolicy()
    }
}
