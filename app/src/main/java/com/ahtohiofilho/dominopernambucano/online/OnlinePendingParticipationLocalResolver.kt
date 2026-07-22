package com.ahtohiofilho.dominopernambucano.online

sealed interface OnlinePendingParticipationLocalResolution {
    data object NoPendingParticipation :
        OnlinePendingParticipationLocalResolution

    data class ReadyForRemoteReconciliation(
        val binding: OnlineParticipationBinding,
    ) : OnlinePendingParticipationLocalResolution

    data class BlockedByMissingValidAnonymousSession(
        val binding: OnlineParticipationBinding,
    ) : OnlinePendingParticipationLocalResolution

    data class BlockedByAnonymousSessionIdentityMismatch(
        val binding: OnlineParticipationBinding,
    ) : OnlinePendingParticipationLocalResolution
}

/*
 * Classifica somente as pré-condições persistidas no dispositivo. Nenhuma
 * resolução desta etapa cria sessão, consulta a rede ou altera um vínculo
 * válido de participação.
 */
class OnlinePendingParticipationLocalResolver(
    private val onlineParticipationBindingRepository:
        OnlineParticipationBindingRepository?,
    private val onlineAnonymousSessionRepository:
        OnlineAnonymousSessionRepository? = null,
    private val onlineSessionCredentialRepository:
        OnlineSessionCredentialRepository? = null,
) {
    fun resolve(): OnlinePendingParticipationLocalResolution {
        val binding = onlineParticipationBindingRepository
            ?.getValidBindingOrNull()
            ?: return OnlinePendingParticipationLocalResolution
                .NoPendingParticipation

        val credential = onlineSessionCredentialRepository
            ?.getValidCredentialOrNull()
            ?: onlineAnonymousSessionRepository
                ?.getValidSessionOrNull()
                ?.toOnlineSessionCredential()
            ?: return OnlinePendingParticipationLocalResolution
                .BlockedByMissingValidAnonymousSession(
                    binding = binding,
                )

        if (credential.playerId != binding.playerId) {
            return OnlinePendingParticipationLocalResolution
                .BlockedByAnonymousSessionIdentityMismatch(
                    binding = binding,
                )
        }

        return OnlinePendingParticipationLocalResolution
            .ReadyForRemoteReconciliation(
                binding = binding,
            )
    }
}
