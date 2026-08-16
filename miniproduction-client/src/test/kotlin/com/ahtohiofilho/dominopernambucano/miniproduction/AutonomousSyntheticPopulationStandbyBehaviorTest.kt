package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpStatus
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousSyntheticPopulationStandbyBehaviorTest {
    @Test
    fun idle_runtime_uses_only_six_standby_accounts_without_startup_burst() {
        var now = 0L
        val running = AtomicBoolean(true)
        val queueCalls = mutableListOf<QueueCall>()
        val gateway = RecordingGateway(
            nowEpochMillis = { now },
            queueCalls = queueCalls,
        )
        val population = population(
            running = running,
            gateway = gateway,
            nowEpochMillis = { now },
            sleeper = { millis ->
                now += millis
                if (now >= 11_000L) {
                    running.set(false)
                }
            },
        )

        population.run()

        val standbyCodes = syntheticRoster.take(6)
            .mapTo(mutableSetOf()) { profile -> profile.tableCode }
        val dormantCodes = syntheticRoster.drop(6).take(10)
            .mapTo(mutableSetOf()) { profile -> profile.tableCode }
        val calledCodes = queueCalls
            .mapTo(mutableSetOf()) { call -> call.tableCode }

        assertEquals(standbyCodes, calledCodes)
        assertTrue(queueCalls.size <= 15)
        assertTrue(queueCalls.size >= 12)
        assertTrue(
            queueCalls.none { call -> call.tableCode in dormantCodes },
        )

        val firstCallByCode = queueCalls
            .groupBy { call -> call.tableCode }
            .mapValues { (_, calls) ->
                requireNotNull(calls.minOfOrNull { call -> call.atEpochMillis })
            }
        val initialTimes = syntheticRoster.take(6)
            .map { profile -> firstCallByCode.getValue(profile.tableCode) }

        assertEquals(0L, initialTimes.first())
        initialTimes.zipWithNext().forEach { (previous, next) ->
            assertTrue(next - previous >= 750L)
        }
    }

    @Test
    fun matched_standby_is_replaced_by_a_dormant_account_on_next_loop() {
        var now = 0L
        val running = AtomicBoolean(true)
        val queueCalls = mutableListOf<QueueCall>()
        val firstCode = syntheticRoster.first().tableCode
        val replacementCode = syntheticRoster[6].tableCode
        val gateway = RecordingGateway(
            nowEpochMillis = { now },
            queueCalls = queueCalls,
            matchedTableCode = firstCode,
        )
        val population = population(
            running = running,
            gateway = gateway,
            nowEpochMillis = { now },
            sleeper = { millis ->
                now += millis
                if (now >= 100L) {
                    running.set(false)
                }
            },
        )

        population.run()

        assertEquals(firstCode, queueCalls.first().tableCode)
        assertTrue(
            queueCalls.any { call ->
                call.tableCode == replacementCode
            },
        )
        assertFalse(
            queueCalls.any { call ->
                call.tableCode == syntheticRoster[7].tableCode
            },
        )
    }

    private fun population(
        running: AtomicBoolean,
        gateway: MiniProductionGateway,
        nowEpochMillis: () -> Long,
        sleeper: (Long) -> Unit,
    ): AutonomousSyntheticPopulation {
        val profiles = syntheticRoster.take(16)
        val credentials = profiles.map { profile ->
            SyntheticAccountCredential(
                profileIndex = profile.index,
                accountId = "account-${profile.index}",
                playerId = "player-${profile.index}",
                accessToken = "test-token-${profile.index}",
                expiresAtEpochMillis = Long.MAX_VALUE,
            )
        }
        val config = MiniProductionClientConfig(
            baseUri = URI.create("http://127.0.0.1:18080"),
            stateDirectory = Files.createTempDirectory(
                "synthetic-standby-runtime",
            ),
            syntheticProvisioningSecret = TEST_SECRET,
            populationSize = 16,
            pollIntervalMillis = 750L,
            standbyPoolSize = 6,
            standbyPollIntervalMillis = 5_000L,
        )

        return AutonomousSyntheticPopulation(
            config = config,
            gateway = gateway,
            profiles = profiles,
            credentials = credentials,
            running = running,
            nowEpochMillis = nowEpochMillis,
            sleeper = sleeper,
        )
    }

    private data class QueueCall(
        val atEpochMillis: Long,
        val tableCode: String,
    )

    private class RecordingGateway(
        private val nowEpochMillis: () -> Long,
        private val queueCalls: MutableList<QueueCall>,
        private val matchedTableCode: String? = null,
    ) : MiniProductionGateway {
        private var matchedReturned = false

        override fun isReady(): Boolean = true

        override fun enterRankedQueue(
            accessToken: String,
            tableCode: String,
        ): PublicRankedQueueHttpResponseDto {
            queueCalls += QueueCall(
                atEpochMillis = nowEpochMillis(),
                tableCode = tableCode,
            )
            return if (
                tableCode == matchedTableCode &&
                !matchedReturned
            ) {
                matchedReturned = true
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.MATCHED,
                    matchId = "match-1",
                    localSeatIndex = 0,
                )
            } else {
                PublicRankedQueueHttpResponseDto(
                    status = PublicRankedQueueHttpStatus.WAITING,
                    queuePosition = 1,
                )
            }
        }

        override fun createAnonymousSession(): OnlineAnonymousSessionDto =
            error("unused")

        override fun promoteAccount(
            anonymousAccessToken: String,
        ): OnlineAccountSessionDto = error("unused")

        override fun recoverSyntheticAccount(
            accountId: String,
        ): OnlineAccountSessionDto = error("unused")

        override fun fetchAccountProfile(
            accessToken: String,
        ): OnlineAccountProfileResponseDto = error("unused")

        override fun updateAccountProfile(
            accessToken: String,
            profile: SyntheticProfile,
        ): OnlineAccountProfileResponseDto = error("unused")

        override fun fetchMatchSnapshot(
            accessToken: String,
            matchId: String,
        ): OnlineMatchSnapshotDto = error("unused")

        override fun submitAction(
            accessToken: String,
            action: OnlinePlayerActionDto,
        ): OnlineActionResultDto = error("unused")
    }

    private companion object {
        const val TEST_SECRET =
            "0123456789abcdef0123456789abcdef"
    }
}
