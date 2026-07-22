package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchClassification
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchMetricAccumulator
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import kotlinx.serialization.Serializable

const val ONLINE_SERVER_STORE_STATE_SCHEMA_VERSION = 4
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
    val accounts: List<OnlineServerAccount> = emptyList(),
    val externalIdentities: List<OnlineServerExternalIdentity> = emptyList(),
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
)

@Serializable
enum class OnlineExternalIdentityProvider {
    GOOGLE,
}

@Serializable
data class OnlineServerExternalIdentity(
    val provider: OnlineExternalIdentityProvider,
    val subject: String,
    val accountId: String,
    val linkedAtEpochMillis: Long,
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
    val classification: RankedMatchClassification =
        RankedMatchClassification.UNRANKED,
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
    val waitingRoomRetentionMillis: Long = 6L * 60L * 60L * 1_000L,
    val finalizedRoomRetentionMillis: Long = 24L * 60L * 60L * 1_000L,
    val pruneIntervalMillis: Long = 60L * 1_000L,
) {
    init {
        require(maxRoomCount > 0)
        require(maxActionResultCount > 0)
        require(waitingRoomRetentionMillis > 0L)
        require(finalizedRoomRetentionMillis > 0L)
        require(pruneIntervalMillis > 0L)
    }

    companion object {
        val Default = OnlineServerStoreResourcePolicy()
    }
}
