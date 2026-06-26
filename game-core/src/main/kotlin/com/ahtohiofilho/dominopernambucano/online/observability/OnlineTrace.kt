package com.ahtohiofilho.dominopernambucano.online.observability

import kotlinx.serialization.Serializable

private const val ONLINE_TRACE_SCHEMA_VERSION = 1

@Serializable
enum class OnlineTraceLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

@Serializable
enum class OnlineTraceSource {
    CLIENT_UI,
    CLIENT_COORDINATOR,
    CLIENT_REPOSITORY,
    SERVER_STORE,
    SERVER_TICKER,
    SERVER_HTTP,
}

@Serializable
enum class OnlineTraceType {
    ROOM_CREATED,
    ROOM_JOINED,
    ROOM_LEFT,

    UI_MOVE_INTENT_RECEIVED,
    UI_MOVE_INTENT_REJECTED,

    ACTION_PREPARED,
    ACTION_SUBMITTED,
    ACTION_ACCEPTED,
    ACTION_REJECTED,
    ACTION_DEDUPLICATED,
    ACTION_SUPPRESSED,

    SNAPSHOT_REQUESTED,
    SNAPSHOT_RECEIVED,
    SNAPSHOT_IGNORED,
    SNAPSHOT_ENQUEUED,
    SNAPSHOT_PUBLISHED,

    POLLING_STARTED,
    POLLING_STOPPED,
    POLLING_FAILED,

    PRESENTATION_STARTED,
    PRESENTATION_FINISHED,
    PRESENTATION_FAST_FORWARDED,
    STABLE_STATE_PROMOTED,

    ANIMATION_TARGET_PENDING,
    ANIMATION_TARGET_READY,
    ANIMATION_STARTED,
    ANIMATION_FINISHED,
    ANIMATION_CANCELLED,
    ANIMATION_FALLBACK_USED,

    AUTHORITATIVE_TICK,
    CLOCK_EXPIRED,
    AUTOMATIC_TURN_RESOLVED,

    TRANSPORT_FAILURE,
    INVARIANT_VIOLATION,
}

@Serializable
data class OnlineTraceContext(
    val clientSessionId: String? = null,

    val roomId: String? = null,
    val matchId: String? = null,

    val playerId: String? = null,
    val localSeatIndex: Int? = null,

    val actionId: String? = null,
    val actionRevision: Long? = null,
    val snapshotRevision: Long? = null,
)

@Serializable
data class OnlineTraceStateSummary(
    val roundNumber: Int,
    val phase: String,
    val currentPlayerIndex: Int,

    val boardPieceCount: Int,
    val teamScores: List<Int>,

    val playerClockMillis: List<Long>,
    val automaticPlayerIndexes: List<Int>,

    /*
     * Hash determinístico do estado lógico. Ele permite comparar cliente e
     * servidor sem registrar o snapshot bruto ou nomes de jogadores.
     */
    val stateFingerprint: String? = null,
)

@Serializable
data class OnlineTraceEvent(
    val schemaVersion: Int = ONLINE_TRACE_SCHEMA_VERSION,
    val occurredAtEpochMillis: Long,

    val level: OnlineTraceLevel,
    val source: OnlineTraceSource,
    val type: OnlineTraceType,

    val context: OnlineTraceContext,
    val state: OnlineTraceStateSummary? = null,

    /*
     * Dados complementares pequenos e pesquisáveis.
     *
     * Não use nomes de jogadores, payload HTTP integral, mãos dos adversários
     * ou snapshots completos neste mapa.
     */
    val attributes: Map<String, String> = emptyMap(),
)

@Serializable
data class OnlineTraceEntry(
    val sequence: Long,
    val event: OnlineTraceEvent,
)

fun interface OnlineTraceSink {
    fun record(
        event: OnlineTraceEvent,
    )
}

object NoOpOnlineTraceSink : OnlineTraceSink {
    override fun record(
        event: OnlineTraceEvent,
    ) = Unit
}

class CompositeOnlineTraceSink(
    private vararg val sinks: OnlineTraceSink,
) : OnlineTraceSink {
    override fun record(
        event: OnlineTraceEvent,
    ) {
        sinks.forEach { sink ->
            sink.record(event)
        }
    }
}

class OnlineTraceLogger(
    private val sink: OnlineTraceSink = NoOpOnlineTraceSink,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val clientSessionId: String? = null,
) {
    fun log(
        level: OnlineTraceLevel,
        source: OnlineTraceSource,
        type: OnlineTraceType,
        context: OnlineTraceContext,
        state: OnlineTraceStateSummary? = null,
        attributes: Map<String, String> = emptyMap(),
    ) {
        val resolvedContext = if (
            context.clientSessionId == null && clientSessionId != null
        ) {
            context.copy(
                clientSessionId = clientSessionId,
            )
        } else {
            context
        }

        sink.record(
            OnlineTraceEvent(
                occurredAtEpochMillis = nowEpochMillis(),
                level = level,
                source = source,
                type = type,
                context = resolvedContext,
                state = state,
                attributes = attributes,
            )
        )
    }
}