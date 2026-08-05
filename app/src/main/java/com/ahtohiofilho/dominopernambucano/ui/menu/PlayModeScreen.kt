package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.ui.components.DominoPrimaryButton
import com.ahtohiofilho.dominopernambucano.ui.components.DominoTextAction
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandAccent
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPrimaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenTitle
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSecondaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val PlayModeTitleTag = "play_mode_title"
internal const val PlayModeContentGroupTag = "play_mode_content_group"
internal const val PlayModeStatusTag = "play_mode_status"
internal const val PlayModeAccountChoiceDialogTag =
    "play_mode_account_choice_dialog"
internal const val PlayModePlayGamesActionTag =
    "play_mode_play_games_action"
internal const val PlayModeGoogleActionTag =
    "play_mode_google_action"

@Composable
fun PlayModeScreen(
    onBackClick: () -> Unit,
    onlineAccountStatus: OnlineGoogleAccountStatus =
        OnlineGoogleAccountStatus.UNAVAILABLE,
    playGamesAvailable: Boolean = false,
    googleAvailable: Boolean = false,
    playGamesActionInProgress: Boolean = false,
    googleActionInProgress: Boolean = false,
    onlineFeedbackMessage: String? = null,
    onConnectPlayGamesClick: () -> Unit = {},
    onConnectGoogleClick: () -> Unit = {},
    onRankedGameClick: () -> Unit,
    onLocalGameClick: () -> Unit,
    onCreateOnlineRoomClick: () -> Unit,
    onJoinOnlineRoomClick: () -> Unit,
) {
    var accountChoiceVisible by remember {
        mutableStateOf(false)
    }
    val onlineActionInProgress =
        playGamesActionInProgress || googleActionInProgress
    val onlineAccountConnected =
        onlineAccountStatus == OnlineGoogleAccountStatus.CONNECTED

    DominoBrandScaffold {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PlayModeContentGroupTag),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.sm,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DominoScreenTitle(
                text = stringResource(R.string.play_mode_title),
                modifier = Modifier.testTag(PlayModeTitleTag),
            )

            onlineFeedbackMessage?.let { message ->
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PlayModeStatusTag)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    text = message,
                    color = DominoSemanticColors.brandText.copy(
                        alpha = 0.86f,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            DominoPrimaryActionCard(
                title = stringResource(R.string.play_mode_ranked),
                supportingText = null,
                onClick = {
                    if (onlineAccountConnected) {
                        onRankedGameClick()
                    } else {
                        accountChoiceVisible = true
                    }
                },
                enabled = !onlineActionInProgress,
                leadingContent = {
                    BrandGlyph("▶")
                },
            )

            DominoSecondaryActionCard(
                title = stringResource(R.string.play_mode_local),
                supportingText = null,
                accent = DominoBrandAccent.Blue,
                onClick = onLocalGameClick,
                leadingContent = {
                    BrandGlyph("◆")
                },
            )

            DominoSecondaryActionCard(
                title = stringResource(
                    R.string.play_mode_create_room,
                ),
                supportingText = null,
                accent = DominoBrandAccent.Green,
                onClick = onCreateOnlineRoomClick,
                leadingContent = {
                    BrandGlyph("+")
                },
            )

            DominoSecondaryActionCard(
                title = stringResource(
                    R.string.play_mode_join_room,
                ),
                supportingText = null,
                accent = DominoBrandAccent.Red,
                onClick = onJoinOnlineRoomClick,
                leadingContent = {
                    BrandGlyph("#")
                },
            )

            DominoSecondaryActionCard(
                title = stringResource(R.string.common_back),
                supportingText = null,
                accent = DominoBrandAccent.Blue,
                onClick = onBackClick,
                leadingContent = {
                    BrandGlyph("←")
                },
            )
        }
    }

    if (accountChoiceVisible) {
        OnlineProviderChoiceDialog(
            status = onlineAccountStatus,
            playGamesAvailable = playGamesAvailable,
            googleAvailable = googleAvailable,
            playGamesActionInProgress =
                playGamesActionInProgress,
            googleActionInProgress = googleActionInProgress,
            feedbackMessage = onlineFeedbackMessage,
            onConnectPlayGamesClick = onConnectPlayGamesClick,
            onConnectGoogleClick = onConnectGoogleClick,
            onDismissRequest = {
                accountChoiceVisible = false
            },
        )
    }
}

@Composable
private fun OnlineProviderChoiceDialog(
    status: OnlineGoogleAccountStatus,
    playGamesAvailable: Boolean,
    googleAvailable: Boolean,
    playGamesActionInProgress: Boolean,
    googleActionInProgress: Boolean,
    feedbackMessage: String?,
    onConnectPlayGamesClick: () -> Unit,
    onConnectGoogleClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val actionInProgress =
        playGamesActionInProgress || googleActionInProgress
    val providerLabels = providerActionLabels(status)

    AlertDialog(
        modifier = Modifier.testTag(
            PlayModeAccountChoiceDialogTag,
        ),
        onDismissRequest = {
            if (!actionInProgress) {
                onDismissRequest()
            }
        },
        title = {
            Text(
                text = stringResource(
                    R.string.play_mode_account_choice_title,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xs,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (playGamesAvailable || googleAvailable) {
                            R.string
                                .play_mode_account_choice_description
                        } else {
                            R.string
                                .play_mode_account_choice_unavailable
                        },
                    ),
                )

                feedbackMessage?.let { message ->
                    Text(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        text = message,
                    )
                }
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.dominoSpacing.xs,
                ),
            ) {
                if (playGamesAvailable) {
                    DominoPrimaryButton(
                        modifier = Modifier.testTag(
                            PlayModePlayGamesActionTag,
                        ),
                        text = if (playGamesActionInProgress) {
                            stringResource(
                                R.string
                                    .account_play_games_connecting,
                            )
                        } else {
                            providerLabels.playGames
                        },
                        onClick = onConnectPlayGamesClick,
                        enabled = !actionInProgress,
                        loading = playGamesActionInProgress,
                        containerColor =
                            DominoSemanticColors.dialogAction,
                        contentColor =
                            DominoSemanticColors.dialogActionContent,
                        disabledContainerColor =
                            DominoSemanticColors.dialogDisabledAction,
                        disabledContentColor =
                            DominoSemanticColors
                                .dialogDisabledActionContent,
                    )
                }

                if (googleAvailable) {
                    DominoTextAction(
                        modifier = Modifier.testTag(
                            PlayModeGoogleActionTag,
                        ),
                        text = if (googleActionInProgress) {
                            stringResource(
                                R.string.account_google_connecting,
                            )
                        } else {
                            providerLabels.google
                        },
                        onClick = onConnectGoogleClick,
                        enabled = !actionInProgress,
                        contentColor =
                            DominoSemanticColors.dialogDismissAction,
                        disabledContentColor =
                            DominoSemanticColors
                                .dialogDisabledDismissAction,
                    )
                }
            }
        },
        dismissButton = {
            DominoTextAction(
                text = stringResource(R.string.common_close),
                onClick = onDismissRequest,
                enabled = !actionInProgress,
                contentColor =
                    DominoSemanticColors.dialogDismissAction,
                disabledContentColor =
                    DominoSemanticColors.dialogDisabledDismissAction,
            )
        },
        containerColor = DominoSemanticColors.dialogSurface,
        titleContentColor = DominoSemanticColors.dialogTitle,
        textContentColor = DominoSemanticColors.dialogBody,
    )
}

private data class ProviderActionLabels(
    val playGames: String,
    val google: String,
)

@Composable
private fun providerActionLabels(
    status: OnlineGoogleAccountStatus,
): ProviderActionLabels {
    return when (status) {
        OnlineGoogleAccountStatus.VISITOR ->
            ProviderActionLabels(
                playGames = stringResource(
                    R.string.account_play_games_link,
                ),
                google = stringResource(
                    R.string.account_google_link,
                ),
            )

        OnlineGoogleAccountStatus.RECOVERY_REQUIRED ->
            ProviderActionLabels(
                playGames = stringResource(
                    R.string.account_play_games_recover,
                ),
                google = stringResource(
                    R.string.account_google_recover,
                ),
            )

        else ->
            ProviderActionLabels(
                playGames = stringResource(
                    R.string.account_play_games_continue,
                ),
                google = stringResource(
                    R.string.account_google_continue,
                ),
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

@Preview(
    name = "Modo de jogo",
    showBackground = true,
    backgroundColor = 0xFF08275C,
)
@Composable
private fun PlayModePreview() {
    DominoPernambucanoTheme {
        PlayModeScreen(
            onBackClick = {},
            onlineAccountStatus =
                OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
            playGamesAvailable = true,
            googleAvailable = true,
            onRankedGameClick = {},
            onLocalGameClick = {},
            onCreateOnlineRoomClick = {},
            onJoinOnlineRoomClick = {},
        )
    }
}
