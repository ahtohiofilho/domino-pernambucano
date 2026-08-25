package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomLeaveRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomSeatChangeRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileUpdateRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailCodeRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailCodeRequestResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailIdentityRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailIdentityRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleIdentityRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueEnterRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineSyntheticAccountRecoveryRequestDto
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

internal fun Route.onlineServerRoutes(
    store: OnlineServerStore,
    traceArchive: OnlineTraceArchive,
    sessionTokenService: OnlineSessionTokenService,
    identityResolver: OnlineRequestIdentityResolver,
    googleIdentityTokenVerifier: OnlineGoogleIdentityTokenVerifier,
    emailVerificationService: OnlineEmailVerificationService? = null,
    rankingPublicationPolicy: RankingPublicationPolicy =
        DEFAULT_RANKING_PUBLICATION_POLICY,
    syntheticProvisioningPolicy: SyntheticProvisioningPolicy =
        SyntheticProvisioningPolicy.Disabled,
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

        emailVerificationService?.let { service ->
            post("/${OnlineEmailIdentityRoutes.REQUEST_CODE}") {
                val request = call.receive<OnlineEmailCodeRequestDto>()

                try {
                    service.requestCode(
                        rawEmail = request.email,
                    )
                } catch (_: OnlineEmailVerificationDeliveryException) {
                    call.respond(HttpStatusCode.ServiceUnavailable)
                    return@post
                }

                call.respond(
                    HttpStatusCode.Accepted,
                    OnlineEmailCodeRequestResponseDto(),
                )
            }

            post("/${OnlineEmailIdentityRoutes.RECOVER_ACCOUNT}") {
                val request = call.receive<OnlineEmailIdentityRequestDto>()
                val subject = when (
                    val verification = service.verifyCode(
                        rawEmail = request.email,
                        rawCode = request.code,
                    )
                ) {
                    is OnlineEmailVerificationResult.Verified -> {
                        verification.subject
                    }

                    OnlineEmailVerificationResult.Rejected -> {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }
                }
                val account = store.findAccountByExternalIdentity(
                    provider = OnlineExternalIdentityProvider.EMAIL,
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

        post("/${OnlineRemoteRoutes.RECOVER_SYNTHETIC_ACCOUNT}") {
            val suppliedSecret = call.request.headers[
                OnlineRemoteHeaders.SYNTHETIC_PROVISIONING_SECRET
            ]
            if (
                suppliedSecret == null ||
                !syntheticProvisioningPolicy.authorizes(suppliedSecret)
            ) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request =
                call.receive<OnlineSyntheticAccountRecoveryRequestDto>()
            val account = try {
                store.findSyntheticAccount(accountId = request.accountId)
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
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
            val suppliedSyntheticSecret = call.request.headers[
                OnlineRemoteHeaders.SYNTHETIC_PROVISIONING_SECRET
            ]
            val account = when {
                suppliedSyntheticSecret == null -> store.promoteAccount(
                    playerId = identity.playerId,
                    expectedAccountId = identity.accountId,
                )

                syntheticProvisioningPolicy.authorizes(
                    suppliedSyntheticSecret,
                ) -> store.promoteSyntheticAccount(
                    playerId = identity.playerId,
                    expectedAccountId = identity.accountId,
                )

                else -> {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
            }

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

        emailVerificationService?.let { service ->
            post("/${OnlineEmailIdentityRoutes.LINK_IDENTITY}") {
                val identity = call.requireOnlineIdentity(
                    identityResolver = identityResolver,
                ) ?: return@post
                val request = call.receive<OnlineEmailIdentityRequestDto>()
                val subject = when (
                    val verification = service.verifyCode(
                        rawEmail = request.email,
                        rawCode = request.code,
                    )
                ) {
                    is OnlineEmailVerificationResult.Verified -> {
                        verification.subject
                    }

                    OnlineEmailVerificationResult.Rejected -> {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }
                }

                when (
                    val result = store.linkExternalIdentity(
                        playerId = identity.playerId,
                        expectedAccountId = identity.accountId,
                        provider = OnlineExternalIdentityProvider.EMAIL,
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

        delete("/${OnlineRemoteRoutes.DELETE_ACCOUNT}") {
            val identity = call.requirePublicRankedAccountIdentity(
                identityResolver = identityResolver,
            ) ?: return@delete
            val accountId = identity.accountId
                ?: run {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }

            val deletionResult = try {
                store.deleteHumanAccount(
                    accountId = accountId,
                    playerId = identity.playerId,
                )
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            when (deletionResult) {
                OnlineAccountDeletionResult.DELETED -> {
                    traceArchive.purgePlayerData(
                        playerId = identity.playerId,
                    )
                    call.respond(HttpStatusCode.NoContent)
                }

                OnlineAccountDeletionResult.NOT_FOUND -> {
                    call.respond(HttpStatusCode.NotFound)
                }

                OnlineAccountDeletionResult.FORBIDDEN -> {
                    call.respond(HttpStatusCode.Forbidden)
                }

                OnlineAccountDeletionResult.ACTIVE_PARTICIPATION -> {
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

        post("/${OnlineRemoteRoutes.PRIVATE_ROOM_SEAT}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val request =
                call.receive<PrivateRoomSeatChangeRequestDto>()

            if (identity.playerId != request.localPlayerId) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            call.respond(
                store.movePrivateRoomSeat(
                    request = request,
                ),
            )
        }

        post("/${OnlineRemoteRoutes.PRIVATE_ROOM_LEAVE}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val request =
                call.receive<PrivateRoomLeaveRequestDto>()

            if (identity.playerId != request.localPlayerId) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            call.respond(
                store.leavePrivateRoom(
                    request = request,
                ),
            )
        }

        post("/${OnlineRemoteRoutes.PRIVATE_ROOM_START}") {
            val identity = call.requireOnlineIdentity(
                identityResolver = identityResolver,
            ) ?: return@post
            val request =
                call.receive<PrivateRoomStartRequestDto>()

            if (identity.playerId != request.localPlayerId) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            call.respond(
                store.startPrivateRoom(
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

            val rawCycleId = call.request.queryParameters["cycleId"]
            val cycleId = rawCycleId?.trim()

            if (
                rawCycleId != null &&
                cycleId.isNullOrBlank()
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val rawOffset = call.request.queryParameters["offset"]
            val parsedOffset = rawOffset?.toIntOrNull()
            val rawLimit = call.request.queryParameters["limit"]
            val parsedLimit = rawLimit?.toIntOrNull()
            val rawRankingRevision =
                call.request.queryParameters["revision"]
            val rankingRevision = rawRankingRevision?.trim()

            if (
                (rawOffset != null && parsedOffset == null) ||
                (rawLimit != null && parsedLimit == null) ||
                (
                    rawRankingRevision != null &&
                        rankingRevision.isNullOrBlank()
                ) ||
                (
                    rankingRevision != null &&
                        !rankingRevision.matches(
                            Regex("[0-9a-f]{64}"),
                        )
                ) ||
                (cycleId != null && rankingRevision != null)
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

            val historicalSnapshot = cycleId?.let { requestedCycleId ->
                store.getClosedRankedCycleSnapshot(
                    cycleId = requestedCycleId,
                )
            }

            if (cycleId != null && historicalSnapshot == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            if (
                historicalSnapshot != null &&
                historicalSnapshot.period.kind !=
                cycle.toRankingCycleKind()
            ) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val ladder = historicalSnapshot
                ?.toRankedCycleLadder()
                ?: store.getRankedCycleLadder(
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
                totalEligiblePlayers =
                    historicalSnapshot
                        ?.totalEligiblePlayers
                        ?: ladder.standings.size,
                retainedRankingSize =
                    historicalSnapshot
                        ?.retainedRankingSize
                        ?: ladder.standings.size,
                closedAtEpochMillis =
                    historicalSnapshot
                        ?.closedAtEpochMillis,
                retentionPolicyVersion =
                    historicalSnapshot
                        ?.retentionPolicyVersion
                        ?: LEGACY_RANKING_RETENTION_POLICY_VERSION,
                isLegacyTruncated =
                    historicalSnapshot
                        ?.isLegacyTruncated
                        ?: false,
                expectedRankingRevision = rankingRevision,
                offset = offset,
                limit = limit,
                publicationPolicy = rankingPublicationPolicy,
            )
        }

        get("/${OnlineRemoteRoutes.RANKING_CYCLES}") {
            call.requireOnlineIdentity(
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

            call.respondPublicRankingCycles(
                cycle = cycle,
                page = store.listClosedRankedCycleSnapshots(
                    kind = cycle.toRankingCycleKind(),
                    offset = offset,
                    limit = limit,
                ),
                offset = offset,
                limit = limit,
                publicationPolicy = rankingPublicationPolicy,
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
