package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
fun MainMenuScreen(
    pendingOnlineParticipation:
        OnlinePendingParticipationLocalResolution,
    pendingOnlineParticipationInspection:
        OnlinePendingParticipationInspectionState,
    onPlayClick: () -> Unit,
    onInspectPendingOnlineParticipationClick: () -> Unit,
) {
    val inspectionInProgress =
        pendingOnlineParticipationInspection is OnlinePendingParticipationInspectionState.InProgress

    val inspectionMessage = when (
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
            text = "Dominó\nPernambucano",
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

        inspectionMessage?.let { message ->
            Text(
                text = message,
                color = DominoSemanticColors.primaryTextOnDark
                    .copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (
            pendingOnlineParticipation is OnlinePendingParticipationLocalResolution.ReadyForRemoteReconciliation
        ) {
            SecondaryMenuButton(
                text = if (inspectionInProgress) {
                    "Verificando..."
                } else {
                    "Verificar participação online"
                },
                onClick = onInspectPendingOnlineParticipationClick,
                enabled = !inspectionInProgress,
            )
        }

        PrimaryMenuButton(
            text = "Jogar",
            onClick = onPlayClick,
            enabled = !inspectionInProgress,
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
            "Participação online confirmada."
        }

        is OnlinePendingParticipationRemoteInspection.NoLongerRecoverable -> {
            "A participação online anterior não está mais disponível."
        }

        is OnlinePendingParticipationRemoteInspection.NotAttempted -> {
            "Não foi possível verificar sua participação neste dispositivo."
        }

        is OnlinePendingParticipationRemoteInspection.TemporarilyUnavailable -> {
            "Não foi possível verificar agora. Tente novamente."
        }
    }
}