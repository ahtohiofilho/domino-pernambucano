package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus

@Composable
fun OnlineAccountDialog(
    status: OnlineGoogleAccountStatus,
    actionInProgress: Boolean,
    feedbackMessage: String?,
    onConnectGoogleClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val presentation = status.toPresentation()

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
                    feedbackMessage,
                ).joinToString(separator = "\n\n"),
            )
        },
        confirmButton = {
            presentation.actionLabel?.let { actionLabel ->
                Button(
                    onClick = onConnectGoogleClick,
                    enabled = !actionInProgress,
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
            ) {
                Text(text = "Fechar")
            }
        },
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
                message =
                    "Sua conta está conectada. Seu jogador e suas participações " +
                        "online permanecem preservados.",
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
