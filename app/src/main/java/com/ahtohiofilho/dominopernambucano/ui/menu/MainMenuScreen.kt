package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountDialog

@Composable
fun MainMenuScreen(
    pendingOnlineParticipation:
    OnlinePendingParticipationLocalResolution,
    pendingOnlineParticipationInspection:
    OnlinePendingParticipationInspectionState,
    pendingOnlineParticipationSessionRejection:
    OnlinePendingParticipationSessionRejection =
        OnlinePendingParticipationSessionRejection.NotRejected,
    pendingOnlineMatchResumeInProgress: Boolean,
    pendingOnlineMatchResumeFeedbackMessage: String? = null,
    onlineGoogleAccountStatus: OnlineGoogleAccountStatus =
        OnlineGoogleAccountStatus.UNAVAILABLE,
    onlineGoogleAccountActionInProgress: Boolean = false,
    onlineGoogleAccountFeedbackMessage: String? = null,
    onPlayClick: () -> Unit,
    onInspectPendingOnlineParticipationClick: () -> Unit,
    onDiscardRejectedPendingOnlineParticipationClick: () -> Unit = {},
    onResumePendingOnlineMatchClick: () -> Unit,
    onConnectGoogleAccountClick: () -> Unit = {},
) {
    var accountDialogVisible by remember {
        mutableStateOf(false)
    }

    val inspectionInProgress =
        pendingOnlineParticipationInspection is
                OnlinePendingParticipationInspectionState.InProgress

    val rejectedBinding = (
            pendingOnlineParticipationSessionRejection as?
                    OnlinePendingParticipationSessionRejection
                    .RemoteSessionRejected
            )?.binding

    val discardRejectedPendingOnlineParticipationAvailable =
        pendingOnlineParticipation is
                OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation &&
                pendingOnlineParticipation.binding == rejectedBinding

    val recoverablePendingOnlineParticipation =
        (
                pendingOnlineParticipationInspection
                        as? OnlinePendingParticipationInspectionState.Completed
                )?.result as? OnlinePendingParticipationRemoteInspection.Recoverable

    val resumePendingOnlineMatchAvailable =
        pendingOnlineParticipation is
                OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation &&
                recoverablePendingOnlineParticipation != null &&
                !discardRejectedPendingOnlineParticipationAvailable

    val menuActionInProgress =
        inspectionInProgress || pendingOnlineMatchResumeInProgress

    val menuMessage = pendingOnlineMatchResumeFeedbackMessage ?: when (
        val inspection = pendingOnlineParticipationInspection
    ) {
        OnlinePendingParticipationInspectionState.NotRequested -> {
            pendingOnlineParticipation.initialInspectionMessageOrNull()
        }

        OnlinePendingParticipationInspectionState.InProgress -> {
            "Verificando sua participação online..."
        }

        is OnlinePendingParticipationInspectionState.Completed -> {
            inspection.result.toInspectionMessage()
        }
    }

    MenuScaffold {
        Text(
            text = "MESA, PARCERIA E ESTRATÉGIA",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.74f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.14.sp,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Dominó PE",
            color = DominoSemanticColors.primaryTextOnDark,
            fontSize = 42.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Um jogo de parceria, leitura de mesa e tomada de decisão.",
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        menuMessage?.let { message ->
            Text(
                text = message,
                color = DominoSemanticColors.primaryTextOnDark
                    .copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (
            pendingOnlineParticipation is
                    OnlinePendingParticipationLocalResolution
                    .ReadyForRemoteReconciliation &&
            !resumePendingOnlineMatchAvailable
        ) {
            SecondaryMenuButton(
                text = if (inspectionInProgress) {
                    "Verificando..."
                } else {
                    "Verificar participação online"
                },
                onClick = onInspectPendingOnlineParticipationClick,
                enabled = !menuActionInProgress,
            )
        }

        if (resumePendingOnlineMatchAvailable) {
            SecondaryMenuButton(
                text = if (pendingOnlineMatchResumeInProgress) {
                    "Retomando..."
                } else {
                    when (
                        recoverablePendingOnlineParticipation
                            ?.roomSnapshot
                            ?.status
                    ) {
                        OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
                            "Retomar sala online"
                        }

                        OnlineRoomStatusDto.IN_MATCH -> {
                            "Retomar partida online"
                        }

                        else -> {
                            "Retomar participação online"
                        }
                    }
                },
                onClick = onResumePendingOnlineMatchClick,
                enabled = !menuActionInProgress,
            )
        }

        if (discardRejectedPendingOnlineParticipationAvailable) {
            SecondaryMenuButton(
                text =
                    "Remover participa\u00e7\u00e3o online rejeitada",
                onClick =
                    onDiscardRejectedPendingOnlineParticipationClick,
                enabled = !menuActionInProgress,
            )
        }

        PrimaryMenuButton(
            text = "Jogar",
            onClick = onPlayClick,
            enabled = !menuActionInProgress,
        )

        TextButton(
            onClick = {
                accountDialogVisible = true
            },
            enabled = !menuActionInProgress,
        ) {
            Text(
                text = "Conta",
                color = DominoSemanticColors.primaryTextOnDark
                    .copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (accountDialogVisible) {
        OnlineAccountDialog(
            status = onlineGoogleAccountStatus,
            actionInProgress = onlineGoogleAccountActionInProgress,
            feedbackMessage = onlineGoogleAccountFeedbackMessage,
            onConnectGoogleClick = onConnectGoogleAccountClick,
            onDismissRequest = {
                accountDialogVisible = false
            },
        )
    }
}

private fun OnlinePendingParticipationLocalResolution
        .initialInspectionMessageOrNull(): String? {
    return when (this) {
        OnlinePendingParticipationLocalResolution.NoPendingParticipation,
        is OnlinePendingParticipationLocalResolution
        .ReadyForRemoteReconciliation -> null

        is OnlinePendingParticipationLocalResolution
        .BlockedByMissingValidAnonymousSession,
        is OnlinePendingParticipationLocalResolution
        .BlockedByAnonymousSessionIdentityMismatch -> {
            "A participação online anterior não pode ser verificada neste dispositivo."
        }
    }
}

private fun OnlinePendingParticipationRemoteInspection
        .toInspectionMessage(): String {
    return when (this) {
        is OnlinePendingParticipationRemoteInspection.Recoverable -> {
            when (roomSnapshot.status) {
                OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
                    "Participação online confirmada. " +
                            "A sala ainda está aguardando jogadores."
                }

                OnlineRoomStatusDto.IN_MATCH -> {
                    "Participação online confirmada."
                }

                else -> {
                    "Participação online confirmada."
                }
            }
        }

        is OnlinePendingParticipationRemoteInspection.NoLongerRecoverable -> {
            "A participação online anterior não está mais disponível."
        }

        OnlinePendingParticipationRemoteInspection.RemoteSessionRejected -> {
            "N\u00e3o foi poss\u00edvel verificar a participa\u00e7\u00e3o: " +
                    "a sess\u00e3o online deste dispositivo foi rejeitada."
        }

        is OnlinePendingParticipationRemoteInspection.NotAttempted -> {
            "Não foi possível verificar sua participação neste dispositivo."
        }

        is OnlinePendingParticipationRemoteInspection.TemporarilyUnavailable -> {
            "Não foi possível verificar agora. Tente novamente."
        }
    }
}
