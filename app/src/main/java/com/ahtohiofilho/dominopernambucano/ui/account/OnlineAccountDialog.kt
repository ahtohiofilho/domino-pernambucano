package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    onConnectGoogleClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val presentation = status.toPresentation()
    val palette = OnlineAccountDialogAccessiblePalette
    val visibleFeedbackMessage =
        onlineAccountDialogVisibleFeedback(
            status = status,
            feedbackMessage = feedbackMessage,
        )

    AlertDialog(
        onDismissRequest = {
            if (!actionInProgress) {
                onDismissRequest()
            }
        },
        title = {
            Text(text = "Conta")
        },
        text = {
            Text(
                text = listOfNotNull(
                    presentation.message,
                    visibleFeedbackMessage,
                ).joinToString(separator = "\n\n"),
            )
        },
        confirmButton = {
            presentation.actionLabel?.let { actionLabel ->
                Button(
                    onClick = onConnectGoogleClick,
                    enabled = !actionInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryContainer,
                        contentColor = palette.primaryContent,
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
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !actionInProgress,
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
