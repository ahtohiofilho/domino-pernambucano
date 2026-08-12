package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpResponseDto

internal interface MiniProductionGateway {
    fun isReady(): Boolean

    fun createAnonymousSession(): OnlineAnonymousSessionDto

    fun promoteAccount(
        anonymousAccessToken: String,
    ): OnlineAccountSessionDto

    fun fetchAccountProfile(
        accessToken: String,
    ): OnlineAccountProfileResponseDto

    fun updateAccountProfile(
        accessToken: String,
        profile: SyntheticProfile,
    ): OnlineAccountProfileResponseDto

    fun enterRankedQueue(
        accessToken: String,
        tableCode: String,
    ): PublicRankedQueueHttpResponseDto

    fun fetchMatchSnapshot(
        accessToken: String,
        matchId: String,
    ): OnlineMatchSnapshotDto

    fun submitAction(
        accessToken: String,
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto
}

internal class MiniProductionHttpException(
    val statusCode: Int,
    val route: String,
    responseBody: String,
) : IllegalStateException(
    "HTTP $statusCode em $route: " +
        responseBody.trim().take(240),
)
