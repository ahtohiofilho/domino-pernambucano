package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.MAX_ONLINE_TABLE_NAME_LENGTH
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus

internal data class OnlineAccountDialogPalette(
    val container: Color,
    val titleContent: Color,
    val bodyContent: Color,
    val primaryContainer: Color,
    val primaryContent: Color,
    val disabledPrimaryContainer: Color,
    val disabledPrimaryContent: Color,
    val dismissContent: Color,
    val disabledDismissContent: Color,
)

internal val OnlineAccountDialogAccessiblePalette =
    OnlineAccountDialogPalette(
        container = Color(0xFFF7F8FC),
        titleContent = Color(0xFF0B2D63),
        bodyContent = Color(0xFF16365C),
        primaryContainer = Color(0xFF0B3B7A),
        primaryContent = Color.White,
        disabledPrimaryContainer = Color(0xFF466A98),
        disabledPrimaryContent = Color.White,
        dismissContent = Color(0xFF0B3B7A),
        disabledDismissContent = Color(0xFF59697D),
    )

internal const val ONLINE_ACCOUNT_CONNECTED_MESSAGE =
    "Seu perfil e seu histórico online foram preservados."

internal fun onlineAccountDialogVisibleFeedback(
    status: OnlineGoogleAccountStatus,
    feedbackMessage: String?,
): String? {
    return feedbackMessage.takeUnless {
        status == OnlineGoogleAccountStatus.CONNECTED
    }
}

@Composable
fun OnlineAccountDialog(
    status: OnlineGoogleAccountStatus,
    actionInProgress: Boolean,
    feedbackMessage: String?,
    profileState: OnlineAccountProfileUiState,
    onPublicDisplayNameChange: (String) -> Unit,
    onTableNameChange: (String) -> Unit,
    onSaveProfileClick: () -> Unit,
    onRetryProfileClick: () -> Unit,
    onConnectGoogleClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val presentation = status.toPresentation()
    val palette = OnlineAccountDialogAccessiblePalette
    val editor =
        profileState as? OnlineAccountProfileUiState.Editing
    val profileActionInProgress =
        editor?.actionInProgress == true
    val anyActionInProgress =
        actionInProgress || profileActionInProgress
    val visibleFeedbackMessage =
        onlineAccountDialogVisibleFeedback(
            status = status,
            feedbackMessage = feedbackMessage,
        )

    AlertDialog(
        onDismissRequest = {
            if (!anyActionInProgress) {
                onDismissRequest()
            }
        },
        title = {
            Text(text = "Conta")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = presentation.message)

                visibleFeedbackMessage?.let { message ->
                    Text(text = message)
                }

                if (
                    status == OnlineGoogleAccountStatus.CONNECTED
                ) {
                    OnlineAccountProfileContent(
                        state = profileState,
                        onPublicDisplayNameChange =
                            onPublicDisplayNameChange,
                        onTableNameChange = onTableNameChange,
                    )
                }
            }
        },
        confirmButton = {
            when {
                status != OnlineGoogleAccountStatus.CONNECTED -> {
                    presentation.actionLabel?.let { actionLabel ->
                        Button(
                            onClick = onConnectGoogleClick,
                            enabled = !anyActionInProgress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    palette.primaryContainer,
                                contentColor =
                                    palette.primaryContent,
                                disabledContainerColor =
                                    palette.disabledPrimaryContainer,
                                disabledContentColor =
                                    palette.disabledPrimaryContent,
                            ),
                        ) {
                            Text(
                                text = if (actionInProgress) {
                                    "Conectando..."
                                } else {
                                    actionLabel
                                },
                            )
                        }
                    }
                }

                editor != null -> {
                    Button(
                        onClick = onSaveProfileClick,
                        enabled =
                            editor.saveEnabled &&
                                !actionInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                palette.primaryContainer,
                            contentColor =
                                palette.primaryContent,
                            disabledContainerColor =
                                palette.disabledPrimaryContainer,
                            disabledContentColor =
                                palette.disabledPrimaryContent,
                        ),
                    ) {
                        Text(
                            text = if (
                                editor.actionInProgress
                            ) {
                                "Salvando..."
                            } else {
                                "Salvar perfil"
                            },
                        )
                    }
                }

                profileState is
                    OnlineAccountProfileUiState.Failure &&
                    profileState.retryable -> {
                    Button(
                        onClick = onRetryProfileClick,
                        enabled = !anyActionInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                palette.primaryContainer,
                            contentColor =
                                palette.primaryContent,
                        ),
                    ) {
                        Text(text = "Tentar novamente")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !anyActionInProgress,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = palette.dismissContent,
                    disabledContentColor =
                        palette.disabledDismissContent,
                ),
            ) {
                Text(text = "Fechar")
            }
        },
        containerColor = palette.container,
        titleContentColor = palette.titleContent,
        textContentColor = palette.bodyContent,
    )
}

@Composable
private fun OnlineAccountProfileContent(
    state: OnlineAccountProfileUiState,
    onPublicDisplayNameChange: (String) -> Unit,
    onTableNameChange: (String) -> Unit,
) {
    when (state) {
        OnlineAccountProfileUiState.NotAvailable -> {
            Text(
                text =
                    "O perfil online não está disponível neste ambiente.",
            )
        }

        OnlineAccountProfileUiState.Loading -> {
            Text(text = "Carregando perfil...")
        }

        is OnlineAccountProfileUiState.Failure -> {
            Text(text = state.message)
        }

        is OnlineAccountProfileUiState.Editing -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text =
                        "O nome público aparece no ranking. " +
                            "O nome de mesa é a forma curta usada durante a partida.",
                )

                OutlinedTextField(
                    value = state.publicDisplayName,
                    onValueChange = onPublicDisplayNameChange,
                    enabled = !state.actionInProgress,
                    singleLine = true,
                    label = {
                        Text(text = "Nome público")
                    },
                    supportingText = {
                        Text(
                            text =
                                "${state.publicDisplayName.length}/" +
                                    MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization =
                            KeyboardCapitalization.Words,
                    ),
                )

                OutlinedTextField(
                    value = state.tableName,
                    onValueChange = onTableNameChange,
                    enabled = !state.actionInProgress,
                    singleLine = true,
                    label = {
                        Text(text = "Nome de mesa")
                    },
                    supportingText = {
                        Text(
                            text =
                                "${state.tableName.length}/" +
                                    MAX_ONLINE_TABLE_NAME_LENGTH,
                        )
                    },
                )

                state.validationMessage?.let { message ->
                    Text(text = message)
                }

                state.feedbackMessage?.let { message ->
                    Text(text = message)
                }
            }
        }
    }
}

private data class OnlineAccountPresentation(
    val message: String,
    val actionLabel: String?,
)

private fun OnlineGoogleAccountStatus.toPresentation():
    OnlineAccountPresentation {
    return when (this) {
        OnlineGoogleAccountStatus.UNAVAILABLE ->
            OnlineAccountPresentation(
                message =
                    "O acesso com Google ainda não está disponível neste build.",
                actionLabel = null,
            )

        OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL ->
            OnlineAccountPresentation(
                message =
                    "Use uma conta Google para recuperar seu jogador ou criar " +
                        "um vínculo seguro neste dispositivo.",
                actionLabel = "Continuar com Google",
            )

        OnlineGoogleAccountStatus.VISITOR ->
            OnlineAccountPresentation(
                message =
                    "Você está jogando como visitante. Vincule uma conta Google " +
                        "sem alterar seu jogador atual.",
                actionLabel = "Vincular com Google",
            )

        OnlineGoogleAccountStatus.CONNECTED ->
            OnlineAccountPresentation(
                message = ONLINE_ACCOUNT_CONNECTED_MESSAGE,
                actionLabel = null,
            )

        OnlineGoogleAccountStatus.RECOVERY_REQUIRED ->
            OnlineAccountPresentation(
                message =
                    "Confirme novamente sua conta Google para continuar usando " +
                        "o mesmo jogador.",
                actionLabel = "Recuperar com Google",
            )
    }
}
