package com.ahtohiofilho.dominopernambucano.online

private const val DEFAULT_ACCOUNT_REFRESH_WINDOW_MILLIS = 5 * 60 * 1_000L

class OnlineAccountSessionExpiredException : IllegalStateException(
    "A credencial da conta expirou e precisa ser recuperada pelo fluxo de login.",
)

class OnlineSessionCredentialPersistenceException : IllegalStateException(
    "O armazenamento local não confirmou a nova credencial online.",
)

class OnlineAccountRecoveryBlockedByAnonymousSessionException :
    IllegalStateException(
        "Uma sessão anônima local deve ser vinculada, não substituída por recuperação.",
    )

class OnlineSessionCredentialRepository(
    private val store: OnlineSessionCredentialStore,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
    private val accountRefreshWindowMillis: Long =
        DEFAULT_ACCOUNT_REFRESH_WINDOW_MILLIS,
) {
    init {
        require(accountRefreshWindowMillis >= 0L) {
            "A janela de renovação da conta não pode ser negativa."
        }
    }

    fun getStoredCredentialOrNull(): OnlineSessionCredential? {
        val storedCredential = store.read() ?: return null

        if (!storedCredential.hasValidShape()) {
            store.clear()
            return null
        }

        return storedCredential
    }

    fun getValidCredentialOrNull(): OnlineSessionCredential? {
        val storedCredential = getStoredCredentialOrNull()
            ?: return null

        if (storedCredential.isValidAt(nowEpochMillis())) {
            return storedCredential
        }

        if (storedCredential.sessionKind == OnlineSessionKind.ANONYMOUS) {
            store.clear()
        }

        /*
         * Uma conta expirada é preservada para recuperação. Apagá-la e criar
         * outra sessão anônima produziria silenciosamente um novo playerId.
         */
        return null
    }

    suspend fun getOrCreateUsableCredential(
        createAnonymousSession:
            suspend () -> OnlineAnonymousSessionDto,
        refreshAccountSession:
            suspend (accessToken: String) -> OnlineAccountSessionDto,
    ): OnlineSessionCredential {
        val storedCredential = getStoredCredentialOrNull()

        if (storedCredential == null) {
            return saveAnonymous(
                session = createAnonymousSession(),
            )
        }

        if (storedCredential.sessionKind == OnlineSessionKind.ACCOUNT) {
            if (!storedCredential.isValidAt(nowEpochMillis())) {
                throw OnlineAccountSessionExpiredException()
            }

            val refreshAtEpochMillis =
                storedCredential.expiresAtEpochMillis -
                        accountRefreshWindowMillis

            if (nowEpochMillis() >= refreshAtEpochMillis) {
                return promoteOrRefresh(
                    currentCredential = storedCredential,
                    promoteAccount = refreshAccountSession,
                )
            }

            return storedCredential
        }

        if (storedCredential.isValidAt(nowEpochMillis())) {
            return storedCredential
        }

        if (!store.clear()) {
            throw OnlineSessionCredentialPersistenceException()
        }

        return saveAnonymous(
            session = createAnonymousSession(),
        )
    }

    suspend fun promoteCurrentCredential(
        promoteAccount:
            suspend (accessToken: String) -> OnlineAccountSessionDto,
    ): OnlineSessionCredential {
        val currentCredential = getStoredCredentialOrNull()
            ?: throw IllegalStateException(
                "Não há credencial online para promover.",
            )

        if (!currentCredential.isValidAt(nowEpochMillis())) {
            if (currentCredential.sessionKind == OnlineSessionKind.ACCOUNT) {
                throw OnlineAccountSessionExpiredException()
            }

            throw IllegalStateException(
                "A sessão anônima expirou antes da promoção.",
            )
        }

        return promoteOrRefresh(
            currentCredential = currentCredential,
            promoteAccount = promoteAccount,
        )
    }

    suspend fun recoverAccountCredential(
        recoverAccount: suspend () -> OnlineAccountSessionDto,
    ): OnlineSessionCredential {
        val currentCredential = getStoredCredentialOrNull()

        if (
            currentCredential?.sessionKind ==
            OnlineSessionKind.ANONYMOUS
        ) {
            throw OnlineAccountRecoveryBlockedByAnonymousSessionException()
        }

        val recoveredCredential = recoverAccount()
            .toOnlineSessionCredential()

        requireValidForStorage(recoveredCredential)

        if (currentCredential != null) {
            require(
                recoveredCredential.playerId == currentCredential.playerId
            ) {
                "A recuperação alterou o playerId da conta online."
            }
            require(
                recoveredCredential.accountId == currentCredential.accountId
            ) {
                "A recuperação alterou o accountId da conta online."
            }
        }

        if (!store.write(recoveredCredential)) {
            throw OnlineSessionCredentialPersistenceException()
        }

        return recoveredCredential
    }

    fun clear(): Boolean {
        return store.clear()
    }

    private fun saveAnonymous(
        session: OnlineAnonymousSessionDto,
    ): OnlineSessionCredential {
        val credential = session.toOnlineSessionCredential()
        requireValidForStorage(credential)

        if (!store.write(credential)) {
            throw OnlineSessionCredentialPersistenceException()
        }

        return credential
    }

    private suspend fun promoteOrRefresh(
        currentCredential: OnlineSessionCredential,
        promoteAccount:
            suspend (accessToken: String) -> OnlineAccountSessionDto,
    ): OnlineSessionCredential {
        val promotedCredential = promoteAccount(
            currentCredential.accessToken,
        ).toOnlineSessionCredential()

        requireValidForStorage(promotedCredential)

        require(promotedCredential.playerId == currentCredential.playerId) {
            "A promoção alterou o playerId da credencial online."
        }

        if (currentCredential.sessionKind == OnlineSessionKind.ACCOUNT) {
            require(
                promotedCredential.accountId == currentCredential.accountId
            ) {
                "A renovação alterou o accountId da credencial online."
            }
        }

        if (!store.write(promotedCredential)) {
            throw OnlineSessionCredentialPersistenceException()
        }

        return promotedCredential
    }

    private fun requireValidForStorage(
        credential: OnlineSessionCredential,
    ) {
        require(credential.hasValidShape()) {
            "A credencial online recebida está incompleta."
        }
        require(credential.isValidAt(nowEpochMillis())) {
            "A credencial online recebida já está expirada."
        }
    }
}

private fun OnlineSessionCredential.hasValidShape(): Boolean {
    return playerId.isNotBlank() &&
            accessToken.isNotBlank() &&
            expiresAtEpochMillis > 0L &&
            (
                    sessionKind == OnlineSessionKind.ANONYMOUS ||
                            !accountId.isNullOrBlank()
                    )
}

private fun OnlineSessionCredential.isValidAt(
    nowEpochMillis: Long,
): Boolean {
    return nowEpochMillis < expiresAtEpochMillis
}
