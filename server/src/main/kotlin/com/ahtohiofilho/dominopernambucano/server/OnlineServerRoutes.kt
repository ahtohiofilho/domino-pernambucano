package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileUpdateRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleIdentityRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueEnterRequestDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.onlineServerRoutes(
    store: OnlineServerStore,
    traceArchive: OnlineTraceArchive,
    sessionTokenService: OnlineSessionTokenService,
    identityResolver: OnlineRequestIdentityResolver,
    googleIdentityTokenVerifier: OnlineGoogleIdentityTokenVerifier,
    nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    rateLimit(ANONYMOUS_SESSION_RATE_LIMIT_NAME) {
        post("/${OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION}") {
            call.respond(
                HttpStatusCode.Created,
                sessionTokenService.issueAnonymousSession(),
            )
        }

        post("/${OnlineRemoteRoutes.RECOVER_GOOGLE_ACCOUNT}") {
            val request = call.receive<OnlineGoogleIdentityRequestDto>()
            val verification = googleIdentityTokenVerifier.verify(
                idToken = request.idToken,
            )
            val subject = when (verification) {
                is OnlineGoogleIdentityVerificationResult.Verified -> {
                    verification.subject
                }

                OnlineGoogleIdentityVerificationResult.Invalid -> {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                OnlineGoogleIdentityVerificationResult.Unavailable -> {
                    call.respond(HttpStatusCode.ServiceUnavailable)
                    return@post
                }
            }
            val account = store.findAccountByExternalIdentity(
                provider = OnlineExternalIdentityProvider.GOOGLE,
                subject = subject,
            )

            if (account == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.respond(
                sessionTokenService.issueAccountSession(
                    playerId = account.playerId,
                    accountId = account.accountId,
                ),
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

        post("/${OnlineRemoteRoutes.LINK_GOOGLE_IDENTITY}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val request = call.receive<OnlineGoogleIdentityRequestDto>()
            val verification = googleIdentityTokenVerifier.verify(
                idToken = request.idToken,
            )
            val subject = when (verification) {
                is OnlineGoogleIdentityVerificationResult.Verified -> {
                    verification.subject
                }

                OnlineGoogleIdentityVerificationResult.Invalid -> {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                OnlineGoogleIdentityVerificationResult.Unavailable -> {
                    call.respond(HttpStatusCode.ServiceUnavailable)
                    return@post
                }
            }

            when (
                val result = store.linkExternalIdentity(
                    playerId = identity.playerId,
                    expectedAccountId = identity.accountId,
                    provider = OnlineExternalIdentityProvider.GOOGLE,
                    subject = subject,
                )
            ) {
                is OnlineExternalIdentityLinkResult.Linked -> {
                    call.respond(
                        sessionTokenService.issueAccountSession(
                            playerId = result.account.playerId,
                            accountId = result.account.accountId,
                        ),
                    )
                }

                OnlineExternalIdentityLinkResult.Conflict -> {
                    call.respond(HttpStatusCode.Conflict)
                }
            }
        }

        put("/${OnlineRemoteRoutes.ACCOUNT_PROFILE}") {
            val identity = call.requirePublicRankedAccountIdentity(
                identityResolver = identityResolver,
            ) ?: return@put
            val request =
                call.receive<OnlineAccountProfileUpdateRequestDto>()
            val accountId = identity.accountId

            if (accountId.isNullOrBlank()) {
                call.respond(HttpStatusCode.Forbidden)
                return@put
            }

            val profile = try {
                store.updateAccountProfile(
                    accountId = accountId,
                    publicDisplayName = request.publicDisplayName,
                    tableName = request.tableName,
                )
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }

            if (profile == null) {
                call.respond(HttpStatusCode.Forbidden)
                return@put
            }

            call.respond(
                OnlineAccountProfileResponseDto(
                    publicDisplayName =
                        profile.publicDisplayName,
                    tableName = profile.tableName,
                    updatedAtEpochMillis =
                        profile.updatedAtEpochMillis,
                ),
            )
        }

        post("/${OnlineRemoteRoutes.RANKED_QUEUE}") {
            val identity = call.requirePublicRankedAccountIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val request =
                call.receive<PublicRankedQueueEnterRequestDto>()
            val playerName = request.playerName.trim()

            if (playerName.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondPublicRankedQueueResult(
                result = store.enqueuePublicRanked(
                    request = CreateOnlineRoomRequestDto(
                        localPlayerId = identity.playerId,
                        playerName = playerName,
                    ),
                    identity = identity,
                ),
            )
        }

        delete("/${OnlineRemoteRoutes.RANKED_QUEUE}") {
            val identity = call.requirePublicRankedAccountIdentity(
                identityResolver = identityResolver,
            ) ?: return@delete

            call.respondPublicRankedQueueResult(
                result = store.cancelPublicRankedQueue(
                    identity = identity,
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
        get("/${OnlineRemoteRoutes.RANKING}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@get
            val cycle = parsePublicRankingCycle(
                value = call.request.queryParameters["cycle"],
            )

            if (cycle == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val rawOffset = call.request.queryParameters["offset"]
            val parsedOffset = rawOffset?.toIntOrNull()
            val rawLimit = call.request.queryParameters["limit"]
            val parsedLimit = rawLimit?.toIntOrNull()

            if (
                (rawOffset != null && parsedOffset == null) ||
                (rawLimit != null && parsedLimit == null)
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val offset = parsedOffset ?: 0
            val limit =
                parsedLimit ?: DEFAULT_PUBLIC_RANKING_PAGE_SIZE

            if (
                offset < 0 ||
                limit !in 1..MAXIMUM_PUBLIC_RANKING_PAGE_SIZE
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val ladder = store.getRankedCycleLadder(
                kind = cycle.toRankingCycleKind(),
                completedAtEpochMillis = nowEpochMillis(),
            )
            val pageAccountIds = ladder.standings
                .drop(offset)
                .take(limit)
                .mapTo(mutableSetOf()) { standing ->
                    standing.accountId
                }

            identity.accountId
                ?.trim()
                ?.takeIf { accountId -> accountId.isNotBlank() }
                ?.let(pageAccountIds::add)

            call.respondPublicRanking(
                cycle = cycle,
                ladder = ladder,
                viewerAccountId = identity.accountId,
                publicDisplayNames =
                    store.getPublicDisplayNames(
                        accountIds = pageAccountIds,
                    ),
                offset = offset,
                limit = limit,
            )
        }

        get("/${OnlineRemoteRoutes.ACCOUNT_PROFILE}") {
            val identity = call.requirePublicRankedAccountIdentity(
                identityResolver = identityResolver,
            ) ?: return@get
            val accountId = identity.accountId

            if (accountId.isNullOrBlank()) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val profile = store.getAccountProfile(
                accountId = accountId,
            )

            if (profile == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(
                OnlineAccountProfileResponseDto(
                    publicDisplayName =
                        profile.publicDisplayName,
                    tableName = profile.tableName,
                    updatedAtEpochMillis =
                        profile.updatedAtEpochMillis,
                ),
            )
        }

        get("/${OnlineRemoteRoutes.RANKED_QUEUE}") {
            val identity = call.requirePublicRankedAccountIdentity(
                identityResolver = identityResolver,
            ) ?: return@get

            call.respondPublicRankedQueueResult(
                result = store.getPublicRankedQueueStatus(
                    identity = identity,
                ),
            )
        }

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


private suspend fun ApplicationCall.requirePublicRankedAccountIdentity(
    identityResolver: OnlineRequestIdentityResolver,
): OnlineRequestIdentity? {
    val identity = requireOnlineIdentity(
        identityResolver = identityResolver,
    ) ?: return null

    if (
        identity.kind != OnlinePrincipalKind.ACCOUNT ||
        identity.accountId.isNullOrBlank()
    ) {
        respond(HttpStatusCode.Forbidden)
        return null
    }

    return identity
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
