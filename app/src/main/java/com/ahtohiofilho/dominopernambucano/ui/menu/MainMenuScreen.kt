package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountDialog
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountProfileUiState
import com.ahtohiofilho.dominopernambucano.ui.components.DominoTextAction
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val MainMenuContentGroupTag = "main_menu_content_group"
internal const val MainMenuTitleTag = "main_menu_title"
internal const val MainMenuActionsGroupTag = "main_menu_actions_group"
internal const val MainMenuRankingActionTag = "main_menu_ranking_action"
internal const val MainMenuAccountActionTag = "main_menu_account_action"

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
    onlineAccountProfileUiState: OnlineAccountProfileUiState =
        OnlineAccountProfileUiState.NotAvailable,
    onAccountDialogOpened: () -> Unit = {},
    onAccountProfilePublicDisplayNameChange: (String) -> Unit =
        {},
    onAccountProfileTableNameChange: (String) -> Unit = {},
    onAccountProfileSaveClick: () -> Unit = {},
    onAccountProfileRetryClick: () -> Unit = {},
    onPlayClick: () -> Unit,
    onRankingClick: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MainMenuContentGroupTag),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.xxl,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainMenuTitleTag)
                    .semantics {
                        heading()
                    },
                text = dominoPeBrandNameOnBlue(),
                fontSize = 42.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainMenuActionsGroupTag),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.md,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                    text = stringResource(R.string.main_menu_play),
                    onClick = onPlayClick,
                    enabled = !menuActionInProgress,
                )

                DominoTextAction(
                    modifier = Modifier.testTag(
                        MainMenuRankingActionTag,
                    ),
                    text = stringResource(R.string.main_menu_ranking),
                    onClick = onRankingClick,
                    enabled = !menuActionInProgress,
                    contentColor =
                        DominoSemanticColors.primaryTextOnDark.copy(
                            alpha = 0.82f,
                        ),
                )

                DominoTextAction(
                    modifier = Modifier.testTag(
                        MainMenuAccountActionTag,
                    ),
                    text = stringResource(R.string.main_menu_account),
                    onClick = {
                        accountDialogVisible = true
                        onAccountDialogOpened()
                    },
                    enabled = !menuActionInProgress,
                    contentColor =
                        DominoSemanticColors.primaryTextOnDark.copy(
                            alpha = 0.82f,
                        ),
                )
            }
        }
    }

    if (accountDialogVisible) {
        OnlineAccountDialog(
            status = onlineGoogleAccountStatus,
            actionInProgress = onlineGoogleAccountActionInProgress,
            feedbackMessage = onlineGoogleAccountFeedbackMessage,
            profileState = onlineAccountProfileUiState,
            onPublicDisplayNameChange =
                onAccountProfilePublicDisplayNameChange,
            onTableNameChange =
                onAccountProfileTableNameChange,
            onSaveProfileClick =
                onAccountProfileSaveClick,
            onRetryProfileClick =
                onAccountProfileRetryClick,
            onConnectGoogleClick = onConnectGoogleAccountClick,
            onDismissRequest = {
                accountDialogVisible = false
            },
        )
    }
}

internal fun dominoPeBrandNameOnBlue(): AnnotatedString {
    return buildAnnotatedString {
        appendBrandGlyph("D", DominoColorTokens.AccentYellow)
        appendBrandGlyph("o", DominoColorTokens.AccentRed)
        appendBrandGlyph("m", DominoColorTokens.AccentYellow)
        appendBrandGlyph("i", DominoColorTokens.AccentGreen)
        appendBrandGlyph("n", DominoColorTokens.AccentYellow)
        appendBrandGlyph("ó", DominoColorTokens.PureWhite)
        append(" ")
        appendBrandGlyph("P", DominoColorTokens.AccentRed)
        appendBrandGlyph("E", DominoColorTokens.PureWhite)
    }
}

private fun AnnotatedString.Builder.appendBrandGlyph(
    glyph: String,
    color: Color,
) {
    withStyle(
        style = SpanStyle(color = color),
    ) {
        append(glyph)
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
