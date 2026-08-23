package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.respondPublicRankedQueueResult(
    result: PublicRankedQueueResult,
) {
    when (result.status) {
        PublicRankedQueueStatus.QUEUED -> {
            if (!result.accepted) {
                respond(HttpStatusCode.Conflict)
                return
            }

            respond(
                HttpStatusCode.OK,
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.WAITING,
                    queuePosition = result.queuePosition,
                ),
            )
        }

        PublicRankedQueueStatus.MATCHED -> {
            val matchId = result.roomSnapshot?.matchId

            if (matchId.isNullOrBlank()) {
                respond(HttpStatusCode.ServiceUnavailable)
                return
            }

            respond(
                if (result.accepted) {
                    HttpStatusCode.OK
                } else {
                    HttpStatusCode.Conflict
                },
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.MATCHED,
                    matchId = matchId,
                    localSeatIndex = null,
                ),
            )
        }

        PublicRankedQueueStatus.NOT_QUEUED -> {
            if (!result.accepted) {
                respond(HttpStatusCode.Conflict)
                return
            }

            respond(
                HttpStatusCode.OK,
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.NOT_QUEUED,
                ),
            )
        }

        PublicRankedQueueStatus.REJECTED -> {
            respond(HttpStatusCode.Conflict)
        }
    }
}
