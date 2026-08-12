package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto

internal class SyntheticIdentityProvisioner(
    private val gateway: MiniProductionGateway,
    private val store: SyntheticIdentityStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun provision(
        profiles: List<SyntheticProfile>,
    ): List<SyntheticAccountCredential> {
        val accountsByProfile = store.load()
            .associateBy { account -> account.profileIndex }
            .toMutableMap()

        profiles.forEach { profile ->
            val existing = accountsByProfile[profile.index]
            val resolved = existing
                ?.let { account -> restore(account, profile) }
                ?: create(profile)

            if (resolved != existing) {
                accountsByProfile[profile.index] = resolved
                store.save(accountsByProfile.values.toList())
            }
        }

        return profiles.map { profile ->
            requireNotNull(accountsByProfile[profile.index])
        }
    }

    private fun restore(
        account: SyntheticAccountCredential,
        profile: SyntheticProfile,
    ): SyntheticAccountCredential {
        if (account.expiresAtEpochMillis <= nowEpochMillis() + 60_000L) {
            return create(profile)
        }

        val currentProfile = try {
            gateway.fetchAccountProfile(account.accessToken)
        } catch (failure: MiniProductionHttpException) {
            return when (failure.statusCode) {
                401, 403 -> create(profile)
                404 -> updateOrReplace(account, profile)
                else -> throw failure
            }
        }

        if (currentProfile.matches(profile)) {
            return account
        }

        return updateOrReplace(account, profile)
    }

    private fun updateOrReplace(
        account: SyntheticAccountCredential,
        profile: SyntheticProfile,
    ): SyntheticAccountCredential {
        val updated = try {
            gateway.updateAccountProfile(
                accessToken = account.accessToken,
                profile = profile,
            )
        } catch (failure: MiniProductionHttpException) {
            return when (failure.statusCode) {
                401, 403 -> create(profile)
                else -> throw failure
            }
        }

        require(updated.matches(profile)) {
            "O servidor não confirmou o perfil sintético ${profile.index}."
        }
        return account
    }

    private fun create(
        profile: SyntheticProfile,
    ): SyntheticAccountCredential {
        val anonymousSession = gateway.createAnonymousSession()
        val accountSession = gateway.promoteAccount(
            anonymousAccessToken = anonymousSession.accessToken,
        )

        require(accountSession.playerId == anonymousSession.playerId) {
            "A promoção da conta sintética alterou o playerId."
        }

        val updatedProfile = gateway.updateAccountProfile(
            accessToken = accountSession.accessToken,
            profile = profile,
        )
        require(updatedProfile.matches(profile)) {
            "O servidor não confirmou o novo perfil sintético."
        }

        return SyntheticAccountCredential(
            profileIndex = profile.index,
            accountId = accountSession.accountId,
            playerId = accountSession.playerId,
            accessToken = accountSession.accessToken,
            expiresAtEpochMillis = accountSession.expiresAtEpochMillis,
        )
    }
}

private fun OnlineAccountProfileResponseDto.matches(
    profile: SyntheticProfile,
): Boolean {
    return publicDisplayName == profile.publicDisplayName &&
        tableName == profile.tableCode
}
