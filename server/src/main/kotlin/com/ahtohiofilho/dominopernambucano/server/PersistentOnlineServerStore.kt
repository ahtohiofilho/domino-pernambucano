package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedCycleLadder
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.match.DominoMatchClockPolicy
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.JoinOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomCompleteRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomRemoveAutomaticPlayerRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomLeaveRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomSeatChangeRequestDto
import com.ahtohiofilho.dominopernambucano.online.PrivateRoomStartRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfile
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomOperationResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceLogger
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE =
    "DOMINO_SERVER_STATE_FILE"

class PersistentOnlineServerStore private constructor(
    private val delegate: InMemoryOnlineServerStore,
    private val statePersistence: OnlineServerStatePersistence,
    private val lockChannel: FileChannel,
    private val processLock: FileLock,
    initialState: OnlineServerStoreState,
) : OnlineServerStore {
    private val lock = Any()
    private var lastPersistedState = initialState
    @Volatile
    private var persistenceAvailable = true
    @Volatile
    private var closed = false

    override fun promoteAccount(
        playerId: String,
        expectedAccountId: String?,
    ): OnlineServerAccount? = mutate {
        delegate.promoteAccount(
            playerId = playerId,
            expectedAccountId = expectedAccountId,
        )
    }

    override fun promoteSyntheticAccount(
        playerId: String,
        expectedAccountId: String?,
    ): OnlineServerAccount? = mutate {
        delegate.promoteSyntheticAccount(
            playerId = playerId,
            expectedAccountId = expectedAccountId,
        )
    }

    override fun linkExternalIdentity(
        playerId: String,
        expectedAccountId: String?,
        provider: OnlineExternalIdentityProvider,
        subject: String,
    ): OnlineExternalIdentityLinkResult = mutate {
        delegate.linkExternalIdentity(
            playerId = playerId,
            expectedAccountId = expectedAccountId,
            provider = provider,
            subject = subject,
        )
    }

    override fun registerEmailPasswordIdentity(
        playerId: String,
        expectedAccountId: String?,
        subject: String,
        credential: OnlineServerPasswordCredential,
    ): OnlineExternalIdentityLinkResult = mutate {
        delegate.registerEmailPasswordIdentity(
            playerId = playerId,
            expectedAccountId = expectedAccountId,
            subject = subject,
            credential = credential,
        )
    }

    override fun findAccountByExternalIdentity(
        provider: OnlineExternalIdentityProvider,
        subject: String,
    ): OnlineServerAccount? = read {
        delegate.findAccountByExternalIdentity(
            provider = provider,
            subject = subject,
        )
    }

    override fun getAccountPasswordCredential(
        accountId: String,
    ): OnlineServerPasswordCredential? = read {
        delegate.getAccountPasswordCredential(
            accountId = accountId,
        )
    }

    override fun setAccountPasswordCredential(
        accountId: String,
        credential: OnlineServerPasswordCredential,
    ): OnlineServerAccount? = mutate {
        delegate.setAccountPasswordCredential(
            accountId = accountId,
            credential = credential,
        )
    }

    override fun findSyntheticAccount(
        accountId: String,
    ): OnlineServerAccount? = read {
        delegate.findSyntheticAccount(accountId = accountId)
    }

    override fun isAccountIdentityActive(
        accountId: String,
        playerId: String,
    ): Boolean = read {
        delegate.isAccountIdentityActive(
            accountId = accountId,
            playerId = playerId,
        )
    }

    override fun deleteHumanAccount(
        accountId: String,
        playerId: String,
    ): OnlineAccountDeletionResult = mutate {
        delegate.deleteHumanAccount(
            accountId = accountId,
            playerId = playerId,
        )
    }

    override fun getAccountProfile(
        accountId: String,
    ): OnlineAccountProfile? = read {
        delegate.getAccountProfile(
            accountId = accountId,
        )
    }

    override fun updateAccountProfile(
        accountId: String,
        publicDisplayName: String,
        tableName: String?,
    ): OnlineAccountProfile? = mutate {
        delegate.updateAccountProfile(
            accountId = accountId,
            publicDisplayName = publicDisplayName,
            tableName = tableName,
        )
    }

    override fun getPublicDisplayNames(
        accountIds: Set<String>,
    ): Map<String, String> = read {
        delegate.getPublicDisplayNames(
            accountIds = accountIds,
        )
    }

    override fun getRankedCycleLadder(
        kind: RankingCycleKind,
        completedAtEpochMillis: Long,
        rankingRuleVersion: Int,
    ): RankedCycleLadder = read {
        delegate.getRankedCycleLadder(
            kind = kind,
            completedAtEpochMillis = completedAtEpochMillis,
            rankingRuleVersion = rankingRuleVersion,
        )
    }

    override fun getClosedRankedCycleSnapshot(
        cycleId: String,
    ): RankedCycleSnapshot? = read {
        delegate.getClosedRankedCycleSnapshot(
            cycleId = cycleId,
        )
    }

    override fun listClosedRankedCycleSnapshots(
        kind: RankingCycleKind,
        offset: Int,
        limit: Int,
    ): RankedCycleSnapshotPage = read {
        delegate.listClosedRankedCycleSnapshots(
            kind = kind,
            offset = offset,
            limit = limit,
        )
    }

    override fun enqueuePublicRanked(
        request: CreateOnlineRoomRequestDto,
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult = mutate {
        delegate.enqueuePublicRanked(
            request = request,
            identity = identity,
        )
    }

    override fun cancelPublicRankedQueue(
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult = mutate {
        delegate.cancelPublicRankedQueue(
            identity = identity,
        )
    }

    override fun getPublicRankedQueueStatus(
        identity: OnlineRequestIdentity,
    ): PublicRankedQueueResult = mutate {
        delegate.getPublicRankedQueueStatus(
            identity = identity,
        )
    }

    override fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto = mutate {
        delegate.createRoom(request)
    }

    override fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto = mutate {
        delegate.joinRoom(request)
    }

    override fun movePrivateRoomSeat(
        request: PrivateRoomSeatChangeRequestDto,
    ): OnlineRoomOperationResultDto = mutate {
        delegate.movePrivateRoomSeat(request)
    }

    override fun completePrivateRoom(
        request: PrivateRoomCompleteRequestDto,
    ): OnlineRoomOperationResultDto = mutate {
        delegate.completePrivateRoom(request)
    }

    override fun removePrivateRoomAutomaticPlayer(
        request: PrivateRoomRemoveAutomaticPlayerRequestDto,
    ): OnlineRoomOperationResultDto = mutate {
        delegate.removePrivateRoomAutomaticPlayer(request)
    }

    override fun leavePrivateRoom(
        request: PrivateRoomLeaveRequestDto,
    ): OnlineRoomOperationResultDto = mutate {
        delegate.leavePrivateRoom(request)
    }

    override fun startPrivateRoom(
        request: PrivateRoomStartRequestDto,
    ): OnlineRoomOperationResultDto = mutate {
        delegate.startPrivateRoom(request)
    }

    override fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto = mutate {
        delegate.submitAction(action)
    }

    override fun advanceAuthoritativeTime(): Boolean {
        return synchronized(lock) {
            checkOpen()

            val stateChanged = delegate.advanceAuthoritativeTime()

            if (stateChanged) {
                persistUpdatedState()
            }

            stateChanged
        }
    }

    override fun getRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto? = read {
        delegate.getRoomSnapshot(roomId)
    }

    override fun isRoomParticipant(
        roomId: String,
        playerId: String,
    ): Boolean = read {
        delegate.isRoomParticipant(
            roomId = roomId,
            playerId = playerId,
        )
    }

    override fun isMatchParticipant(
        matchId: String,
        playerId: String,
    ): Boolean = read {
        delegate.isMatchParticipant(
            matchId = matchId,
            playerId = playerId,
        )
    }

    override fun getMatchSnapshotForParticipant(
        matchId: String,
        playerId: String,
    ): OnlineMatchSnapshotDto? = read {
        delegate.getMatchSnapshotForParticipant(
            matchId = matchId,
            playerId = playerId,
        )
    }

    override fun getMatchSnapshotsAfterForParticipant(
        matchId: String,
        playerId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>? = read {
        delegate.getMatchSnapshotsAfterForParticipant(
            matchId = matchId,
            playerId = playerId,
            afterRevision = afterRevision,
        )
    }

    override fun getMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto? = read {
        delegate.getMatchSnapshot(matchId)
    }

    override fun getMatchSnapshotsAfter(
        matchId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto>? = read {
        delegate.getMatchSnapshotsAfter(
            matchId = matchId,
            afterRevision = afterRevision,
        )
    }

    override fun readiness(): OnlineServerStoreReadiness {
        return if (!closed && persistenceAvailable) {
            OnlineServerStoreReadiness.READY
        } else {
            OnlineServerStoreReadiness.UNAVAILABLE
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }

            closed = true

            try {
                processLock.release()
            } finally {
                lockChannel.close()
            }
        }
    }

    private fun <T> read(
        operation: () -> T,
    ): T {
        return synchronized(lock) {
            checkOpen()
            operation()
        }
    }

    private fun <T> mutate(
        operation: () -> T,
    ): T {
        return synchronized(lock) {
            checkOpen()

            val result = operation()
            persistUpdatedState()
            result
        }
    }

    private fun persistUpdatedState() {
        val updatedState = delegate.snapshotPersistentState()

        if (updatedState == lastPersistedState) {
            return
        }

        try {
            statePersistence.write(updatedState)
        } catch (error: Exception) {
            persistenceAvailable = false
            delegate.restorePersistentState(lastPersistedState)
            throw error
        }

        lastPersistedState = updatedState
        persistenceAvailable = true
    }

    private fun checkOpen() {
        check(!closed) {
            "O store persistente já foi encerrado."
        }
    }

    companion object {
        internal fun open(
            stateFile: File,
            clockPolicy: DominoMatchClockPolicy =
                DominoMatchClockPolicy.OnlinePerPlayerRound,
            autoFillDevelopmentBotsAfterTwoHumanPlayers: Boolean = false,
            resourcePolicy: OnlineServerStoreResourcePolicy =
                OnlineServerStoreResourcePolicy.Default,
            rankingPublicationPolicy: RankingPublicationPolicy =
                DEFAULT_RANKING_PUBLICATION_POLICY,
            nowEpochMillis: () -> Long = {
                System.currentTimeMillis()
            },
            traceLogger: OnlineTraceLogger = OnlineTraceLogger(
                nowEpochMillis = nowEpochMillis,
            ),
            accountIdFactory: () -> String = {
                "account-${UUID.randomUUID()}"
            },
        ): PersistentOnlineServerStore {
            return open(
                statePersistence = FileOnlineServerStatePersistence(
                    stateFile = stateFile,
                ),
                clockPolicy = clockPolicy,
                autoFillDevelopmentBotsAfterTwoHumanPlayers =
                    autoFillDevelopmentBotsAfterTwoHumanPlayers,
                resourcePolicy = resourcePolicy,
                rankingPublicationPolicy =
                    rankingPublicationPolicy,
                nowEpochMillis = nowEpochMillis,
                accountIdFactory = accountIdFactory,
                traceLogger = traceLogger,
            )
        }

        internal fun open(
            statePersistence: OnlineServerStatePersistence,
            clockPolicy: DominoMatchClockPolicy =
                DominoMatchClockPolicy.OnlinePerPlayerRound,
            autoFillDevelopmentBotsAfterTwoHumanPlayers: Boolean = false,
            resourcePolicy: OnlineServerStoreResourcePolicy =
                OnlineServerStoreResourcePolicy.Default,
            rankingPublicationPolicy: RankingPublicationPolicy =
                DEFAULT_RANKING_PUBLICATION_POLICY,
            nowEpochMillis: () -> Long = {
                System.currentTimeMillis()
            },
            traceLogger: OnlineTraceLogger = OnlineTraceLogger(
                nowEpochMillis = nowEpochMillis,
            ),
            accountIdFactory: () -> String = {
                "account-${UUID.randomUUID()}"
            },
        ): PersistentOnlineServerStore {
            val lockChannel = statePersistence.openLockChannel()
            val processLock = try {
                lockChannel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }

            if (processLock == null) {
                lockChannel.close()
                throw IllegalStateException(
                    "O arquivo de estado autoritativo já está em uso: " +
                            statePersistence.description,
                )
            }

            try {
                val initialState = statePersistence.readOrCreate()
                val delegate = InMemoryOnlineServerStore(
                    clockPolicy = clockPolicy,
                    autoFillDevelopmentBotsAfterTwoHumanPlayers =
                        autoFillDevelopmentBotsAfterTwoHumanPlayers,
                    resourcePolicy = resourcePolicy,
                    rankingPublicationPolicy =
                        rankingPublicationPolicy,
                    nowEpochMillis = nowEpochMillis,
                    accountIdFactory = accountIdFactory,
                    traceLogger = traceLogger,
                )

                delegate.restorePersistentState(initialState)

                return PersistentOnlineServerStore(
                    delegate = delegate,
                    statePersistence = statePersistence,
                    lockChannel = lockChannel,
                    processLock = processLock,
                    initialState = initialState,
                )
            } catch (error: Exception) {
                processLock.release()
                lockChannel.close()
                throw error
            }
        }
    }
}

internal fun createDefaultOnlineServerStore(
    serverEnvironment: OnlineServerEnvironment,
    autoFillDevelopmentBotsAfterTwoHumanPlayers: Boolean,
    traceLogger: OnlineTraceLogger,
    resourcePolicy: OnlineServerStoreResourcePolicy =
        OnlineServerStoreResourcePolicy.Default,
    nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): OnlineServerStore {
    val configuredStateFile = readEnvironmentVariable(
        ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }

    if (
        serverEnvironment == OnlineServerEnvironment.HOMOLOGATION &&
        configuredStateFile != null
    ) {
        throw IllegalStateException(
            "$ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE deve permanecer " +
                "ausente na homologacao isolada.",
        )
    }

    if (configuredStateFile == null) {
        check(!serverEnvironment.requiresPersistentState) {
            "$ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE deve ser " +
                    "configurada no ambiente persistente."
        }

        return InMemoryOnlineServerStore(
            autoFillDevelopmentBotsAfterTwoHumanPlayers =
                autoFillDevelopmentBotsAfterTwoHumanPlayers,
            resourcePolicy = resourcePolicy,
            rankingPublicationPolicy =
                serverEnvironment.rankingPublicationPolicy,
            nowEpochMillis = nowEpochMillis,
            traceLogger = traceLogger,
        )
    }

    val stateFile = File(configuredStateFile)

    if (serverEnvironment.requiresPersistentState) {
        require(stateFile.isAbsolute) {
            "$ONLINE_SERVER_STATE_FILE_ENVIRONMENT_VARIABLE deve usar " +
                    "caminho absoluto no ambiente persistente."
        }
    }

    return PersistentOnlineServerStore.open(
        stateFile = stateFile,
        autoFillDevelopmentBotsAfterTwoHumanPlayers =
            autoFillDevelopmentBotsAfterTwoHumanPlayers,
        resourcePolicy = resourcePolicy,
        rankingPublicationPolicy =
            serverEnvironment.rankingPublicationPolicy,
        nowEpochMillis = nowEpochMillis,
        traceLogger = traceLogger,
    )
}

internal interface OnlineServerStatePersistence {
    val description: String

    fun openLockChannel(): FileChannel

    fun readOrCreate(): OnlineServerStoreState

    fun write(
        state: OnlineServerStoreState,
    )
}

internal class FileOnlineServerStatePersistence(
    stateFile: File,
    private val json: Json = Json {
        encodeDefaults = true
    },
) : OnlineServerStatePersistence {
    private val stateFile = stateFile.absoluteFile

    override val description: String
        get() = stateFile.absolutePath

    override fun openLockChannel(): FileChannel {
        ensureParentDirectory()

        return FileChannel.open(
            File(
                stateFile.parentFile,
                "${stateFile.name}.lock",
            ).toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
    }

    override fun readOrCreate(): OnlineServerStoreState {
        if (!stateFile.exists()) {
            return OnlineServerStoreState.Empty.also { emptyState ->
                write(emptyState)
            }
        }

        require(stateFile.isFile) {
            "O caminho de estado autoritativo não é um arquivo: " +
                    stateFile.absolutePath
        }
        require(
            stateFile.length() > 0L &&
                    stateFile.length() <= MAX_STATE_FILE_BYTES
        ) {
            "O arquivo de estado autoritativo está vazio ou excede o limite."
        }

        return try {
            json.decodeFromString<OnlineServerStoreState>(
                stateFile.readText(Charsets.UTF_8),
            )
        } catch (error: Exception) {
            throw IllegalStateException(
                "Não foi possível carregar o estado autoritativo persistido.",
                error,
            )
        }
    }

    override fun write(
        state: OnlineServerStoreState,
    ) {
        ensureParentDirectory()

        val encodedState = json.encodeToString(state)
            .toByteArray(Charsets.UTF_8)

        require(encodedState.size.toLong() <= MAX_STATE_FILE_BYTES) {
            "O estado autoritativo excede o limite persistível."
        }

        val temporaryFile = File(
            stateFile.parentFile,
            ".${stateFile.name}.${UUID.randomUUID()}.tmp",
        )

        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(encodedState)
                output.flush()
                output.fd.sync()
            }

            try {
                Files.move(
                    temporaryFile.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }

    private fun ensureParentDirectory() {
        val parentDirectory = stateFile.parentFile

        check(
            parentDirectory.isDirectory || parentDirectory.mkdirs()
        ) {
            "Não foi possível criar o diretório do estado autoritativo: " +
                    parentDirectory.absolutePath
        }
    }

    private companion object {
        const val MAX_STATE_FILE_BYTES = 64L * 1_024L * 1_024L
    }
}
