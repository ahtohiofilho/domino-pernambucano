package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.onlineServerRoutes(
    store: InMemoryOnlineServerStore,
    traceArchive: OnlineTraceArchive,
) {
    post("/${OnlineRemoteRoutes.CREATE_ROOM}") {
        val request = call.receive<CreateOnlineRoomRequestDto>()

        call.respond(
            store.createRoom(
                request = request,
            ),
        )
    }

    post("/${OnlineRemoteRoutes.JOIN_ROOM}") {
        val request = call.receive<JoinOnlineRoomRequestDto>()

        call.respond(
            store.joinRoom(
                request = request,
            ),
        )
    }

    post("/${OnlineRemoteRoutes.SUBMIT_ACTION}") {
        val action = call.receive<OnlinePlayerActionDto>()

        call.respond(
            store.submitAction(
                action = action,
            ),
        )
    }

    post("/${OnlineRemoteRoutes.SUBMIT_TRACE_BATCH}") {
        val batch = call.receive<OnlineTraceBatchDto>()

        call.respond(
            traceArchive.recordClientBatch(
                batch = batch,
            ),
        )
    }

    get("/rooms/{roomId}") {
        val roomId = call.parameters["roomId"]

        val roomSnapshot = roomId?.let { value ->
            store.getRoomSnapshot(
                roomId = value,
            )
        }

        if (roomSnapshot == null) {
            call.respond(
                HttpStatusCode.NotFound,
            )

            return@get
        }

        call.respond(roomSnapshot)
    }

    get("/matches/{matchId}") {
        val matchId = call.parameters["matchId"]

        val matchSnapshot = matchId?.let { value ->
            store.getMatchSnapshot(
                matchId = value,
            )
        }

        if (matchSnapshot == null) {
            call.respond(
                HttpStatusCode.NotFound,
            )

            return@get
        }

        call.respond(matchSnapshot)
    }

    get("/matches/{matchId}/updates") {
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

        val snapshots = matchId?.let { value ->
            store.getMatchSnapshotsAfter(
                matchId = value,
                afterRevision = afterRevision,
            )
        }

        if (snapshots == null) {
            call.respond(
                HttpStatusCode.NotFound,
            )
            return@get
        }

        call.respond(snapshots)
    }
}