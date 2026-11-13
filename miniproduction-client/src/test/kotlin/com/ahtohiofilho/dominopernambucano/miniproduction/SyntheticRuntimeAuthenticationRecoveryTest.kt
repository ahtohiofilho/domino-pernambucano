package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpResponseDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpStatus
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticRuntimeAuthenticationRecoveryTest {
    @Test
    fun one_expired_account_recovers_without_stopping_healthy_accounts() {
        val gateway = RecoveringQueueGateway()
        var recoveries = 0
        runPopulation(
            gateway = gateway,
            recovery = { account, _ ->
                recoveries++
                account.copy(accessToken = "renewed-token-1")
            },
        )
        assertEquals(1, recoveries)
        assertTrue(gateway.acceptedRenewedToken)
        assertTrue(gateway.healthyAccountsPolled)
    }

    @Test
    fun failed_recovery_is_isolated_instead_of_crashing_the_population() {
        val gateway = RecoveringQueueGateway()
        var recoveries = 0
        runPopulation(
            gateway = gateway,
            recovery = { _, _ ->
                recoveries++
                throw IOException("temporary credential recovery failure")
            },
        )
        assertEquals(1, recoveries)
        assertTrue(gateway.healthyAccountsPolled)
    }

    private fun runPopulation(
        gateway: MiniProductionGateway,
        recovery: (SyntheticAccountCredential, SyntheticProfile) -> SyntheticAccountCredential,
    ) {
        var now = 0L
        val running = AtomicBoolean(true)
        val profiles = syntheticRoster.take(16)
        val credentials = profiles.map { profile ->
            SyntheticAccountCredential(
                profileIndex = profile.index,
                accountId = "account-${profile.index}",
                playerId = "player-${profile.index}",
                accessToken = "old-token-${profile.index}",
                expiresAtEpochMillis = Long.MAX_VALUE,
            )
        }
        val config = MiniProductionClientConfig(
            baseUri = URI.create("http://127.0.0.1:18080"),
            stateDirectory = Files.createTempDirectory("synthetic-runtime-401"),
            syntheticProvisioningSecret = "0123456789abcdef0123456789abcdef",
            populationSize = 16,
            standbyPoolSize = 6,
            standbyPollIntervalMillis = 5_000L,
        )
        AutonomousSyntheticPopulation(
            config = config,
            gateway = gateway,
            profiles = profiles,
            credentials = credentials,
            running = running,
            nowEpochMillis = { now },
            sleeper = { millis ->
                now += millis
                if (now >= 11_000L) running.set(false)
            },
            recoverCredential = recovery,
        ).run()
    }

    private class RecoveringQueueGateway : MiniProductionGateway {
        var acceptedRenewedToken = false
        var healthyAccountsPolled = false

        override fun isReady(): Boolean = true

        override fun enterRankedQueue(
            accessToken: String,
            tableCode: String,
        ): PublicRankedQueueHttpResponseDto {
            if (accessToken == "old-token-1") {
                throw MiniProductionHttpException(401, "ranked-queue", "")
            }
            if (accessToken == "renewed-token-1") acceptedRenewedToken = true
            if (accessToken == "old-token-2") healthyAccountsPolled = true
            return PublicRankedQueueHttpResponseDto(
                status = PublicRankedQueueHttpStatus.WAITING,
                queuePosition = 1,
            )
        }

        override fun createAnonymousSession(): OnlineAnonymousSessionDto = error("unused")
        override fun promoteAccount(anonymousAccessToken: String): OnlineAccountSessionDto =
            error("unused")
        override fun recoverSyntheticAccount(accountId: String): OnlineAccountSessionDto =
            error("unused")
        override fun fetchAccountProfile(accessToken: String): OnlineAccountProfileResponseDto =
            error("unused")
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
}
