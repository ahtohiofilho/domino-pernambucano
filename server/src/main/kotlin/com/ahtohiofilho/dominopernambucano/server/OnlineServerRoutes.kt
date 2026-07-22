package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.onlineServerRoutes(
    store: OnlineServerStore,
    traceArchive: OnlineTraceArchive,
    sessionTokenService: OnlineSessionTokenService,
    identityResolver: OnlineRequestIdentityResolver,
) {
    rateLimit(ANONYMOUS_SESSION_RATE_LIMIT_NAME) {
        post("/${OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION}") {
            call.respond(
                HttpStatusCode.Created,
                sessionTokenService.issueAnonymousSession(),
            )
        }
    }

    rateLimit(AUTHENTICATED_MUTATION_RATE_LIMIT_NAME) {
        post("/${OnlineRemoteRoutes.PROMOTE_ACCOUNT}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val account = store.promoteAccount(
                playerId = identity.playerId,
                expectedAccountId = identity.accountId,
            )

            if (account == null) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@post
            }

            call.respond(
                sessionTokenService.issueAccountSession(
                    playerId = account.playerId,
                    accountId = account.accountId,
                ),
            )
        }

        post("/${OnlineRemoteRoutes.CREATE_ROOM}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val request = call.receive<CreateOnlineRoomRequestDto>()

            if (identity.playerId != request.localPlayerId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@post
            }

            call.respond(
                store.createRoom(
                    request = request,
                ),
            )
        }

        post("/${OnlineRemoteRoutes.JOIN_ROOM}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val request = call.receive<JoinOnlineRoomRequestDto>()

            if (identity.playerId != request.localPlayerId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@post
            }

            call.respond(
                store.joinRoom(
                    request = request,
                ),
            )
        }

        post("/${OnlineRemoteRoutes.SUBMIT_ACTION}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val action = call.receive<OnlinePlayerActionDto>()

            if (identity.playerId != action.playerId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@post
            }

            call.respond(
                store.submitAction(
                    action = action,
                ),
            )
        }
    }

    rateLimit(CLIENT_TRACE_RATE_LIMIT_NAME) {
        post("/${OnlineRemoteRoutes.SUBMIT_TRACE_BATCH}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val batch = call.receive<OnlineTraceBatchDto>()
            val isAuthorizedBatch = batch.entries.all { entry ->
                val context = entry.event.context
                val declaredPlayerId = context.playerId
                    ?.trim()
                    ?.takeIf { value ->
                        value.isNotBlank()
                    }
                val roomId = context.roomId
                    ?.trim()
                    ?.takeIf { value ->
                        value.isNotBlank()
                    }
                val matchId = context.matchId
                    ?.trim()
                    ?.takeIf { value ->
                        value.isNotBlank()
                    }

                (declaredPlayerId == null ||
                        declaredPlayerId == identity.playerId) &&
                        (roomId == null || store.isRoomParticipant(
                            roomId = roomId,
                            playerId = identity.playerId,
                        )) &&
                        (matchId == null || store.isMatchParticipant(
                            matchId = matchId,
                            playerId = identity.playerId,
                        ))
            }

            if (!isAuthorizedBatch) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@post
            }

            call.respond(
                traceArchive.recordClientBatch(
                    batch = batch,
                ),
            )
        }
    }

    rateLimit(AUTHENTICATED_READ_RATE_LIMIT_NAME) {
        get("/rooms/{roomId}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@get
            val roomId = call.parameters["roomId"]

            if (roomId == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            val roomSnapshot = store.getRoomSnapshot(
                roomId = roomId,
            )

            if (roomSnapshot == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            if (
                !store.isRoomParticipant(
                    roomId = roomId,
                    playerId = identity.playerId,
                )
            ) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@get
            }

            call.respond(roomSnapshot)
        }

        get("/matches/{matchId}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@get
            val matchId = call.parameters["matchId"]

            if (matchId == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            val authoritativeSnapshot = store.getMatchSnapshot(
                matchId = matchId,
            )

            if (authoritativeSnapshot == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            if (
                !store.isMatchParticipant(
                    matchId = matchId,
                    playerId = identity.playerId,
                )
            ) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@get
            }

            val participantSnapshot = store.getMatchSnapshotForParticipant(
                matchId = matchId,
                playerId = identity.playerId,
            )

            if (participantSnapshot == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            call.respond(participantSnapshot)
        }

        get("/matches/{matchId}/updates") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@get
            val matchId = call.parameters["matchId"]
            val rawAfterRevision = call.request.queryParameters["afterRevision"]
            val afterRevision = rawAfterRevision?.toLongOrNull()

            if (
                afterRevision == null ||
                afterRevision < 0L
            ) {
                call.respond(
                    HttpStatusCode.BadRequest,
                )
                return@get
            }

            if (matchId == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            val authoritativeSnapshot = store.getMatchSnapshot(
                matchId = matchId,
            )

            if (authoritativeSnapshot == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            if (
                !store.isMatchParticipant(
                    matchId = matchId,
                    playerId = identity.playerId,
                )
            ) {
                call.respond(
                    HttpStatusCode.Forbidden,
                )
                return@get
            }

            val snapshots = store.getMatchSnapshotsAfterForParticipant(
                matchId = matchId,
                playerId = identity.playerId,
                afterRevision = afterRevision,
            )

            if (snapshots == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                )
                return@get
            }

            call.respond(snapshots)
        }
    }
}

private suspend fun ApplicationCall.requireOnlineIdentity(
    identityResolver: OnlineRequestIdentityResolver,
): OnlineRequestIdentity? {
    val identity = identityResolver.resolve(
        call = this,
    )

    if (identity != null) {
        return identity
    }

    respond(
        HttpStatusCode.Unauthorized,
    )

    return null
}
