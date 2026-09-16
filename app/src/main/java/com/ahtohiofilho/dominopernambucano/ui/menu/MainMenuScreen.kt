package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountIntent
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlinePasswordAccountManager
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationRemoteInspection
import com.ahtohiofilho.dominopernambucano.online.OnlineRoomStatusDto
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationInspectionState
import com.ahtohiofilho.dominopernambucano.session.OnlinePendingParticipationSessionRejection
import com.ahtohiofilho.dominopernambucano.ui.account.IdentityAvatar
import com.ahtohiofilho.dominopernambucano.ui.account.identityHubAvatarLabel
import com.ahtohiofilho.dominopernambucano.ui.account.identityHubCompactDisplayName
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountDialog
import com.ahtohiofilho.dominopernambucano.ui.account.OnlineAccountProfileUiState
import com.ahtohiofilho.dominopernambucano.ui.account.RankedAccountEntryMode
import com.ahtohiofilho.dominopernambucano.ui.account.RankedAccountEntryScreen
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandAccent
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoColorTokens
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoCompactActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPrimaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSecondaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val MainMenuContentGroupTag = "main_menu_content_group"
internal const val MainMenuTitleTag = "main_menu_title"
internal const val MainMenuActionsGroupTag = "main_menu_actions_group"
internal const val MainMenuRankingActionTag = "main_menu_ranking_action"
internal const val MainMenuAccountActionTag = "main_menu_account_action"
internal const val MainMenuProfileActionTag = "main_menu_profile_action"
internal const val MainMenuStatusTag = "main_menu_status"
internal const val MainMenuSettingsActionTag = "main_menu_settings_action"
internal const val MainMenuRulesActionTag = "main_menu_rules_action"

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
    onlineGoogleAvailable: Boolean = true,
    onlineEmailAvailable: Boolean = false,
    onlinePasswordAccountManager: OnlinePasswordAccountManager? = null,
    onlineEmailAddress: String = "",
    onlineEmailCode: String = "",
    onlineEmailIntent: OnlineEmailAccountIntent? = null,
    onlineEmailCodeRequested: Boolean = false,
    onlineEmailActionInProgress: Boolean = false,
    onlineEmailFeedbackMessage: String? = null,
    onlineAccountDisplayName: String? = null,
    onlineAccountTableName: String? = null,
    onProfileClick: () -> Unit = {},
    onlineAccountProfileUiState: OnlineAccountProfileUiState =
        OnlineAccountProfileUiState.NotAvailable,
    openAccountDialogOnEnter: Boolean = false,
    onAccountDialogOpenRequestConsumed: () -> Unit = {},
    onAccountDialogOpened: () -> Unit = {},
    onPasswordAuthenticated: () -> Unit = {},
    onAccountProfilePublicDisplayNameChange: (String) -> Unit =
        {},
    onAccountProfileTableNameChange: (String) -> Unit = {},
    onAccountProfileSaveClick: () -> Unit = {},
    onAccountProfileRetryClick: () -> Unit = {},
    onEmailAddressChange: (String) -> Unit = {},
    onEmailCodeChange: (String) -> Unit = {},
    onEmailStartLinkClick: () -> Unit = {},
    onEmailStartRecoverClick: () -> Unit = {},
    onEmailConfirmCodeClick: () -> Unit = {},
    onEmailResetClick: () -> Unit = {},
    onAccountDialogDismissed: () -> Unit = {},
    onPlayClick: () -> Unit,
    onRankingClick: () -> Unit,
    onInspectPendingOnlineParticipationClick: () -> Unit,
    onDiscardRejectedPendingOnlineParticipationClick: () -> Unit = {},
    onResumePendingOnlineMatchClick: () -> Unit,
    onConnectGoogleAccountClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
) {
    var accountDialogVisible by remember {
        mutableStateOf(false)
    }
    var accountEntryVisible by remember {
        mutableStateOf(false)
    }
    var accountEntryMode by remember {
        mutableStateOf(RankedAccountEntryMode.SIGN_IN)
    }
    var reopenAccountAfterGoogleAction by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(openAccountDialogOnEnter) {
        if (openAccountDialogOnEnter) {
            onAccountDialogOpenRequestConsumed()
            if (
                onlineGoogleAccountStatus ==
                    OnlineGoogleAccountStatus.CONNECTED
            ) {
                accountDialogVisible = true
                onAccountDialogOpened()
            } else if (onlinePasswordAccountManager != null) {
                accountEntryMode = RankedAccountEntryMode.SIGN_IN
                accountEntryVisible = true
            } else {
                accountDialogVisible = true
                onAccountDialogOpened()
            }
        }
    }

    LaunchedEffect(
        onlineGoogleAccountStatus,
        accountEntryVisible,
    ) {
        if (
            accountEntryVisible &&
            onlineGoogleAccountStatus ==
                OnlineGoogleAccountStatus.CONNECTED
        ) {
            accountEntryVisible = false
            accountEntryMode = RankedAccountEntryMode.SIGN_IN
            accountDialogVisible = true
        }
    }

    LaunchedEffect(onlineGoogleAccountActionInProgress) {
        if (
            !onlineGoogleAccountActionInProgress &&
            reopenAccountAfterGoogleAction
        ) {
            reopenAccountAfterGoogleAction = false
            accountDialogVisible = true
        }
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

    val menuMessage =
        pendingOnlineMatchResumeFeedbackMessage ?: when (
            val inspection = pendingOnlineParticipationInspection
        ) {
            OnlinePendingParticipationInspectionState.NotRequested -> {
                when (pendingOnlineParticipation) {
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
                    is OnlinePendingParticipationLocalResolution
                        .ReadyForRemoteReconciliation -> null

                    is OnlinePendingParticipationLocalResolution
                        .BlockedByMissingValidAnonymousSession,
                    is OnlinePendingParticipationLocalResolution
                        .BlockedByAnonymousSessionIdentityMismatch -> {
                        stringResource(
                            R.string
                                .main_menu_previous_participation_unverifiable,
                        )
                    }
                }
            }

            OnlinePendingParticipationInspectionState.InProgress -> {
                stringResource(
                    R.string.main_menu_checking_participation,
                )
            }

            is OnlinePendingParticipationInspectionState.Completed -> {
                when (val result = inspection.result) {
                    is OnlinePendingParticipationRemoteInspection
                        .Recoverable -> {
                        when (result.roomSnapshot.status) {
                            OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
                                stringResource(
                                    R.string
                                        .main_menu_participation_confirmed_waiting,
                                )
                            }

                            OnlineRoomStatusDto.FINISHED -> {
                                stringResource(
                                    R.string.main_menu_participation_finished,
                                )
                            }

                            else -> {
                                stringResource(
                                    R.string.main_menu_participation_confirmed,
                                )
                            }
                        }
                    }

                    is OnlinePendingParticipationRemoteInspection
                        .NoLongerRecoverable -> {
                        stringResource(
                            R.string.main_menu_participation_unavailable,
                        )
                    }

                    OnlinePendingParticipationRemoteInspection
                        .RemoteSessionRejected -> {
                        stringResource(
                            R.string.main_menu_session_rejected,
                        )
                    }

                    is OnlinePendingParticipationRemoteInspection
                        .NotAttempted -> {
                        stringResource(
                            R.string.main_menu_inspection_unavailable,
                        )
                    }

                    is OnlinePendingParticipationRemoteInspection
                        .TemporarilyUnavailable -> {
                        stringResource(
                            R.string.main_menu_try_again,
                        )
                    }
                }
            }
        }

    if (accountEntryVisible && onlinePasswordAccountManager != null) {
        RankedAccountEntryScreen(
            mode = accountEntryMode,
            actionInProgress = onlineGoogleAccountActionInProgress,
            feedbackMessage = onlineGoogleAccountFeedbackMessage,
            googleAvailable = onlineGoogleAvailable,
            passwordAccountManager = onlinePasswordAccountManager,
            onContinueGoogleClick = onConnectGoogleAccountClick,
            onAuthenticated = {
                onPasswordAuthenticated()
                accountEntryVisible = false
                accountEntryMode = RankedAccountEntryMode.SIGN_IN
                accountDialogVisible = true
            },
            onModeChange = { mode ->
                accountEntryMode = mode
            },
            onBackClick = {
                accountEntryVisible = false
                accountEntryMode = RankedAccountEntryMode.SIGN_IN
            },
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        DominoScreenScaffold(
            title = dominoPeBrandNameOnBlue(),
            layout = DominoScreenLayout.Centered,
            contentModifier = Modifier.testTag(
                MainMenuContentGroupTag,
            ),
            titleModifier = Modifier.testTag(MainMenuTitleTag),
            statusMessage = menuMessage,
            statusModifier = Modifier.testTag(MainMenuStatusTag),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = MaterialTheme.dominoSpacing.lg,
                    )
                    .testTag(MainMenuActionsGroupTag),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.md,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (
                    pendingOnlineParticipation is
                            OnlinePendingParticipationLocalResolution
                            .ReadyForRemoteReconciliation &&
                    !resumePendingOnlineMatchAvailable
                ) {
                    DominoCompactActionCard(
                        title = if (inspectionInProgress) {
                            stringResource(
                                R.string.main_menu_checking_participation,
                            )
                        } else {
                            stringResource(
                                R.string.main_menu_check_participation,
                            )
                        },
                        accent = DominoBrandAccent.Blue,
                        onClick =
                            onInspectPendingOnlineParticipationClick,
                        enabled = !menuActionInProgress,
                        leadingContent = {
                            BrandGlyph("?")
                        },
                    )
                }

                if (resumePendingOnlineMatchAvailable) {
                    val resumeTitle =
                        if (pendingOnlineMatchResumeInProgress) {
                            stringResource(
                                R.string.main_menu_resuming,
                            )
                        } else {
                            when (
                                recoverablePendingOnlineParticipation
                                    ?.roomSnapshot
                                    ?.status
                            ) {
                                OnlineRoomStatusDto.WAITING_FOR_PLAYERS -> {
                                    stringResource(
                                        R.string.main_menu_resume_room,
                                    )
                                }

                                OnlineRoomStatusDto.IN_MATCH -> {
                                    stringResource(
                                        R.string.main_menu_resume_match,
                                    )
                                }

                                OnlineRoomStatusDto.FINISHED -> {
                                    stringResource(
                                        R.string.main_menu_view_match_result,
                                    )
                                }

                                else -> {
                                    stringResource(
                                        R.string
                                            .main_menu_resume_participation,
                                    )
                                }
                            }
                        }

                    DominoCompactActionCard(
                        title = resumeTitle,
                        accent = DominoBrandAccent.Green,
                        onClick = onResumePendingOnlineMatchClick,
                        enabled = !menuActionInProgress,
                        leadingContent = {
                            BrandGlyph("↻")
                        },
                    )
                }

                if (discardRejectedPendingOnlineParticipationAvailable) {
                    DominoCompactActionCard(
                        title = stringResource(
                            R.string
                                .main_menu_remove_rejected_participation,
                        ),
                        accent = DominoBrandAccent.Red,
                        onClick =
                            onDiscardRejectedPendingOnlineParticipationClick,
                        enabled = !menuActionInProgress,
                        leadingContent = {
                            BrandGlyph("×")
                        },
                    )
                }

                DominoPrimaryActionCard(
                    title = stringResource(R.string.main_menu_play),
                    supportingText = null,
                    onClick = onPlayClick,
                    enabled = !menuActionInProgress,
                    leadingContent = {
                        BrandGlyph("▶")
                    },
                )

                DominoSecondaryActionCard(
                    modifier = Modifier.testTag(
                        MainMenuRankingActionTag,
                    ),
                    title = stringResource(
                        R.string.main_menu_ranking,
                    ),
                    supportingText = stringResource(
                        R.string.main_menu_ranking_support,
                    ),
                    accent = DominoBrandAccent.Red,
                    onClick = onRankingClick,
                    enabled = !menuActionInProgress,
                    elevated = true,
                    leadingContent = {
                        BrandGlyph("★")
                    },
                )

            }
        }

        val profileDisplayName =
            identityHubCompactDisplayName(
                displayName = onlineAccountDisplayName,
                tableName = onlineAccountTableName,
            )
        val profileAvatarLabel =
            identityHubAvatarLabel(
                displayName = onlineAccountDisplayName,
                tableName = onlineAccountTableName,
            )
        val profileLabel =
            profileDisplayName
                ?: stringResource(
                    R.string.identity_profile_fallback,
                )
        val profileDescription = profileLabel

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    top = MaterialTheme.dominoSpacing.sm,
                    start = MaterialTheme.dominoSpacing.lg,
                )
                .widthIn(max = 220.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    enabled = !menuActionInProgress,
                    role = Role.Button,
                    onClick = onProfileClick,
                )
                .testTag(MainMenuProfileActionTag)
                .semantics {
                    contentDescription = profileDescription
                }
                .padding(
                    top = 5.dp,
                    end = MaterialTheme.dominoSpacing.xs,
                    bottom = 5.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdentityAvatar(
                label = profileAvatarLabel,
                size = 38.dp,
            )

            Spacer(
                modifier = Modifier.width(
                    MaterialTheme.dominoSpacing.xs,
                ),
            )

            Text(
                text = profileLabel,
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val rulesDescription = stringResource(
            R.string.main_menu_rules_content_description,
        )

        IconButton(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    bottom = MaterialTheme.dominoSpacing.md,
                    start = MaterialTheme.dominoSpacing.lg,
                )
                .testTag(MainMenuRulesActionTag)
                .semantics {
                    contentDescription = rulesDescription
                },
            onClick = onRulesClick,
        ) {
            Text(
                text = "?",
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
        }

        val settingsDescription = stringResource(
            R.string.main_menu_settings_content_description,
        )

        IconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    top = MaterialTheme.dominoSpacing.md,
                    end = MaterialTheme.dominoSpacing.lg,
                )
                .testTag(MainMenuSettingsActionTag)
                .semantics {
                    contentDescription = settingsDescription
                },
            onClick = onSettingsClick,
        ) {
            Text(
                text = "⚙",
                color = DominoSemanticColors.brandText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (accountDialogVisible) {
        OnlineAccountDialog(
            status = onlineGoogleAccountStatus,
            actionInProgress = onlineGoogleAccountActionInProgress,
            feedbackMessage = onlineGoogleAccountFeedbackMessage,
            googleAvailable = onlineGoogleAvailable,
            emailAvailable = onlineEmailAvailable,
            emailAddress = onlineEmailAddress,
            emailCode = onlineEmailCode,
            emailIntent = onlineEmailIntent,
            emailCodeRequested = onlineEmailCodeRequested,
            emailActionInProgress = onlineEmailActionInProgress,
            emailFeedbackMessage = onlineEmailFeedbackMessage,
            profileState = onlineAccountProfileUiState,
            onPublicDisplayNameChange =
                onAccountProfilePublicDisplayNameChange,
            onTableNameChange =
                onAccountProfileTableNameChange,
            onSaveProfileClick =
                onAccountProfileSaveClick,
            onRetryProfileClick =
                onAccountProfileRetryClick,
            onEmailAddressChange = onEmailAddressChange,
            onEmailCodeChange = onEmailCodeChange,
            onEmailStartLinkClick = onEmailStartLinkClick,
            onEmailStartRecoverClick =
                onEmailStartRecoverClick,
            onEmailConfirmCodeClick =
                onEmailConfirmCodeClick,
            onEmailResetClick = onEmailResetClick,
            onConnectGoogleClick = {
                reopenAccountAfterGoogleAction = true
                accountDialogVisible = false
                onConnectGoogleAccountClick()
            },
            onDismissRequest = {
                accountDialogVisible = false
                onAccountDialogDismissed()
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

@Composable
private fun AccountSilhouetteGlyph() {
    Canvas(
        modifier = Modifier.size(24.dp),
    ) {
        val color = DominoSemanticColors.brandText
        val dimension = minOf(size.width, size.height)

        drawCircle(
            color = color,
            radius = dimension * 0.18f,
            center = Offset(
                x = size.width * 0.50f,
                y = size.height * 0.30f,
            ),
        )

        val bust = Path().apply {
            moveTo(
                x = size.width * 0.16f,
                y = size.height * 0.88f,
            )
            cubicTo(
                x1 = size.width * 0.20f,
                y1 = size.height * 0.64f,
                x2 = size.width * 0.34f,
                y2 = size.height * 0.54f,
                x3 = size.width * 0.50f,
                y3 = size.height * 0.54f,
            )
            cubicTo(
                x1 = size.width * 0.66f,
                y1 = size.height * 0.54f,
                x2 = size.width * 0.80f,
                y2 = size.height * 0.64f,
                x3 = size.width * 0.84f,
                y3 = size.height * 0.88f,
            )
            close()
        }

        drawPath(
            path = bust,
            color = color,
        )
    }
}

@Composable
private fun BrandGlyph(
    glyph: String,
) {
    Text(
        text = glyph,
        color = DominoSemanticColors.brandText,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
}
