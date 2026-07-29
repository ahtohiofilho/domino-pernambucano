package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.components.DominoOutlinedTextField
import com.ahtohiofilho.dominopernambucano.ui.components.DominoTextFieldTone
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

internal const val PlayModePublicIdentityFieldTag =
    "play_mode_public_identity_field"

@Composable
fun PlayModeScreen(
    onlineDisplayName: String,
    onlineTableName: String,
    onlineIdentityManagedByAccount: Boolean,
    onOnlineDisplayNameChange: (String) -> Unit,
    onBackClick: () -> Unit,
    rankedAccountAvailable: Boolean,
    onRankedGameClick: () -> Unit,
    onLocalGameClick: () -> Unit,
    onCreateOnlineRoomClick: () -> Unit,
    onJoinOnlineRoomClick: () -> Unit,
) {
    MenuScaffold {
        Text(
            text = stringResource(R.string.play_mode_title),
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.play_mode_description),
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        OnlinePublicIdentityField(
            displayName = onlineDisplayName,
            tableName = onlineTableName,
            editable = !onlineIdentityManagedByAccount,
            onDisplayNameChange = onOnlineDisplayNameChange,
        )

        PrimaryMenuButton(
            text = stringResource(R.string.play_mode_ranked),
            onClick = onRankedGameClick,
            enabled = rankedAccountAvailable,
        )

        if (!rankedAccountAvailable) {
            Text(
                text =
                    stringResource(
                        R.string.play_mode_ranked_account_required,
                    ),
                color =
                    DominoSemanticColors.primaryTextOnDark.copy(
                        alpha = 0.72f,
                    ),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        SecondaryMenuButton(
            text = stringResource(R.string.play_mode_local),
            onClick = onLocalGameClick,
        )

        SecondaryMenuButton(
            text = stringResource(R.string.play_mode_create_room),
            onClick = onCreateOnlineRoomClick,
        )

        SecondaryMenuButton(
            text = stringResource(R.string.play_mode_join_room),
            onClick = onJoinOnlineRoomClick,
        )

        SecondaryMenuButton(
            text = stringResource(R.string.common_back),
            onClick = onBackClick,
        )
    }
}

@Composable
private fun OnlinePublicIdentityField(
    displayName: String,
    tableName: String,
    editable: Boolean,
    onDisplayNameChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.play_mode_public_identity),
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        DominoOutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.testTag(
                PlayModePublicIdentityFieldTag,
            ),
            enabled = editable,
            label = stringResource(R.string.play_mode_public_name),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
            ),
            tone = DominoTextFieldTone.OnDark,
        )

        Text(
            text = stringResource(
                R.string.play_mode_lists_and_rankings,
                displayName,
            ),
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = stringResource(
                R.string.play_mode_table_name,
                tableName,
            ),
            color = DominoSemanticColors.primaryTextOnDark,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = if (editable) {
                stringResource(
                    R.string.play_mode_editable_identity_hint,
                )
            } else {
                stringResource(
                    R.string.play_mode_managed_identity_hint,
                )
            },
            color = DominoSemanticColors.primaryTextOnDark.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(
    name = "Modo de jogo - visitante",
    showBackground = true,
    backgroundColor = 0xFF08275C,
)
@Composable
private fun PlayModeVisitorPreview() {
    DominoPernambucanoTheme {
        PlayModeScreen(
            onlineDisplayName = "Antônio Filho",
            onlineTableName = "antonio",
            onlineIdentityManagedByAccount = false,
            onOnlineDisplayNameChange = {},
            onBackClick = {},
            rankedAccountAvailable = false,
            onRankedGameClick = {},
            onLocalGameClick = {},
            onCreateOnlineRoomClick = {},
            onJoinOnlineRoomClick = {},
        )
    }
}
