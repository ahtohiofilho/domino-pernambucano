package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpResponseDto
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyntheticIdentityProvisionerTest {
    @Test
    fun canonical_promotion_preserves_player_and_is_reused() {
        val root = Files.createTempDirectory("synthetic-identities")
        val gateway = FakeProvisioningGateway()
        val store = SyntheticIdentityStore(
            stateDirectory = root,
            baseUrl = "http://127.0.0.1:18080",
        )
        val profiles = syntheticRoster.take(2)
        val provisioner = SyntheticIdentityProvisioner(
            gateway = gateway,
            store = store,
            nowEpochMillis = { 1_000L },
        )

        val first = provisioner.provision(profiles)
        val second = provisioner.provision(profiles)

        assertEquals(2, gateway.anonymousSessionsCreated)
        assertEquals(first, second)
        assertEquals(first, store.load().take(2))
        assertEquals(
            first.map { account -> account.playerId },
            listOf("player-1", "player-2"),
        )
    }

    @Test
    fun promotion_that_changes_player_id_is_rejected() {
        val root = Files.createTempDirectory("synthetic-identities")
        val gateway = FakeProvisioningGateway(
            changePlayerIdOnPromotion = true,
        )
        val store = SyntheticIdentityStore(
            stateDirectory = root,
            baseUrl = "http://127.0.0.1:18080",
        )

        assertThrows(IllegalArgumentException::class.java) {
            SyntheticIdentityProvisioner(
                gateway = gateway,
                store = store,
            ).provision(syntheticRoster.take(1))
        }
    }

    @Test
    fun expired_session_recovers_the_same_account_without_reprovisioning() {
        val root = Files.createTempDirectory("synthetic-identities")
        val gateway = FakeProvisioningGateway()
        val store = SyntheticIdentityStore(
            stateDirectory = root,
            baseUrl = "https://domino.example",
        )
        val profile = syntheticRoster.first()
        val first = SyntheticIdentityProvisioner(
            gateway = gateway,
            store = store,
            nowEpochMillis = { 1_000L },
        ).provision(listOf(profile)).single()

        gateway.expireAccountSessions()
        val recovered = SyntheticIdentityProvisioner(
            gateway = gateway,
            store = store,
            nowEpochMillis = { 10_000_000L },
        ).provision(listOf(profile)).single()

        assertEquals(first.accountId, recovered.accountId)
        assertEquals(first.playerId, recovered.playerId)
        assertEquals(1, gateway.anonymousSessionsCreated)
        assertEquals(1, gateway.syntheticSessionsRecovered)
        assertEquals(recovered, store.load().single())
    }
}

private class FakeProvisioningGateway(
    private val changePlayerIdOnPromotion: Boolean = false,
) : MiniProductionGateway {
    var anonymousSessionsCreated: Int = 0
        private set
    var syntheticSessionsRecovered: Int = 0
        private set
    private val anonymousPlayersByToken = mutableMapOf<String, String>()
    private val profilesByAccountToken =
        mutableMapOf<String, OnlineAccountProfileResponseDto>()

    override fun isReady(): Boolean = true

    override fun createAnonymousSession(): OnlineAnonymousSessionDto {
        anonymousSessionsCreated++
        val playerId = "player-$anonymousSessionsCreated"
        val token = "anonymous-token-$anonymousSessionsCreated"
        anonymousPlayersByToken[token] = playerId
        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = token,
            expiresAtEpochMillis = 9_999_999L,
        )
    }

    override fun promoteAccount(
        anonymousAccessToken: String,
    ): OnlineAccountSessionDto {
        val originalPlayerId = requireNotNull(
            anonymousPlayersByToken[anonymousAccessToken],
        )
        val sequence = originalPlayerId.substringAfterLast('-')
        return OnlineAccountSessionDto(
            accountId = "account-$sequence",
            playerId = if (changePlayerIdOnPromotion) {
                "changed-$originalPlayerId"
            } else {
                originalPlayerId
            },
            accessToken = "account-token-$sequence",
            expiresAtEpochMillis = 9_999_999L,
        )
    }

    override fun recoverSyntheticAccount(
        accountId: String,
    ): OnlineAccountSessionDto {
        syntheticSessionsRecovered++
        val sequence = accountId.substringAfterLast('-')
        return OnlineAccountSessionDto(
            accountId = accountId,
            playerId = "player-$sequence",
            accessToken = "recovered-token-$sequence",
            expiresAtEpochMillis = 20_000_000L,
        ).also { session ->
            profilesByAccountToken[session.accessToken] = requireNotNull(
                profilesByAccountToken["account-token-$sequence"],
            )
        }
    }

    fun expireAccountSessions() = Unit

    override fun fetchAccountProfile(
        accessToken: String,
    ): OnlineAccountProfileResponseDto {
        return profilesByAccountToken[accessToken]
            ?: throw MiniProductionHttpException(404, "accounts/profile", "")
    }

    override fun updateAccountProfile(
        accessToken: String,
        profile: SyntheticProfile,
    ): OnlineAccountProfileResponseDto {
        val response = OnlineAccountProfileResponseDto(
            publicDisplayName = profile.publicDisplayName,
            tableName = profile.tableCode,
            updatedAtEpochMillis = 1_000L,
        )
        profilesByAccountToken[accessToken] = response
        return response
    }

    override fun enterRankedQueue(
        accessToken: String,
        tableCode: String,
    ): PublicRankedQueueHttpResponseDto = error("Não usado neste teste.")

    override fun fetchMatchSnapshot(
        accessToken: String,
        matchId: String,
    ): OnlineMatchSnapshotDto = error("Não usado neste teste.")

    override fun submitAction(
        accessToken: String,
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto = error("Não usado neste teste.")
}
