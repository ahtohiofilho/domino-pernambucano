package com.ahtohiofilho.dominopernambucano.online

/*
 * Vínculo local mínimo necessário para voltar a consultar uma participação
 * online existente depois de morte de processo ou reabertura do aplicativo.
 * O snapshot remoto continua sendo a fonte de verdade.
 */
data class OnlineParticipationBinding(
    val backendScope: String,
    val roomId: String,
    val matchId: String?,
    val playerId: String,
    val seatIndex: Int,
)

interface OnlineParticipationStore {
    fun read(): OnlineParticipationBinding?

    fun write(
        binding: OnlineParticipationBinding,
    )

    fun clear()
}

class InMemoryOnlineParticipationStore(
    initialBinding: OnlineParticipationBinding? = null,
) : OnlineParticipationStore {
    private var binding: OnlineParticipationBinding? =
        initialBinding?.normalizedOrNull()

    override fun read(): OnlineParticipationBinding? {
        return binding
    }

    override fun write(
        binding: OnlineParticipationBinding,
    ) {
        this.binding = binding.normalizedOrNull()
    }

    override fun clear() {
        binding = null
    }
}

fun OnlineBackendConfig.toOnlineParticipationBackendScope(): String {
    return when (mode) {
        OnlineBackendMode.FAKE -> "fake"

        OnlineBackendMode.REMOTE -> {
            val normalizedBaseUrl = baseUrl
                ?.trim()
                ?.trimEnd('/')
                .orEmpty()

            "remote:$normalizedBaseUrl"
        }
    }
}

fun createOnlineParticipationBinding(
    backendScope: String,
    roomSnapshot: OnlineRoomSnapshotDto,
    playerId: String,
    seatIndex: Int,
): OnlineParticipationBinding? {
    return OnlineParticipationBinding(
        backendScope = backendScope,
        roomId = roomSnapshot.roomId,
        matchId = roomSnapshot.matchId,
        playerId = playerId,
        seatIndex = seatIndex,
    ).normalizedOrNull()
}

fun OnlineParticipationBinding.normalizedOrNull(): OnlineParticipationBinding? {
    val normalizedBackendScope = backendScope.trim()
    val normalizedRoomId = roomId.trim()
    val normalizedPlayerId = playerId.trim()
    val normalizedMatchId = matchId
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }

    if (
        normalizedBackendScope.isBlank() ||
        normalizedRoomId.isBlank() ||
        normalizedPlayerId.isBlank() ||
        seatIndex !in 0..3
    ) {
        return null
    }

    return copy(
        backendScope = normalizedBackendScope,
        roomId = normalizedRoomId,
        matchId = normalizedMatchId,
        playerId = normalizedPlayerId,
    )
}
