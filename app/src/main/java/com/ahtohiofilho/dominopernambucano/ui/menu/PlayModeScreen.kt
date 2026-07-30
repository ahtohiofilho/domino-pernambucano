package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandAccent
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandShapes
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPrimaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenTitle
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSecondaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val PlayModePublicIdentityFieldTag =
    "play_mode_public_identity_field"
internal const val PlayModeTitleTag = "play_mode_title"
internal const val PlayModeContentGroupTag = "play_mode_content_group"

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

            Text(
                text = stringResource(R.string.play_mode_description),
                color = DominoSemanticColors.brandText.copy(
                    alpha = 0.80f,
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            OnlinePublicIdentityField(
                displayName = onlineDisplayName,
                tableName = onlineTableName,
                editable = !onlineIdentityManagedByAccount,
                onDisplayNameChange = onOnlineDisplayNameChange,
            )

            DominoPrimaryActionCard(
                title = stringResource(R.string.play_mode_ranked),
                supportingText = stringResource(
                    R.string.play_mode_ranked_support,
                ),
                onClick = onRankedGameClick,
                enabled = rankedAccountAvailable,
                leadingContent = {
                    BrandGlyph("▶")
                },
            )

            if (!rankedAccountAvailable) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(
                        R.string.play_mode_ranked_account_required,
                    ),
                    color = DominoSemanticColors.brandText.copy(
                        alpha = 0.76f,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            DominoSecondaryActionCard(
                title = stringResource(R.string.play_mode_local),
                supportingText = stringResource(
                    R.string.play_mode_local_support,
                ),
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
                supportingText = stringResource(
                    R.string.play_mode_create_room_support,
                ),
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
                supportingText = stringResource(
                    R.string.play_mode_join_room_support,
                ),
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
}

@Composable
private fun OnlinePublicIdentityField(
    displayName: String,
    tableName: String,
    editable: Boolean,
    onDisplayNameChange: (String) -> Unit,
) {
    val shape = DominoBrandShapes.card

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                DominoSemanticColors.brandSurfaceElevated,
            )
            .border(
                width = 1.dp,
                color = DominoSemanticColors.brandBorder,
                shape = shape,
            )
            .padding(MaterialTheme.dominoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(
            MaterialTheme.dominoSpacing.xs,
        ),
    ) {
        Text(
            text = stringResource(
                R.string.play_mode_public_identity,
            ),
            color = DominoSemanticColors.brandText,
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
            label = stringResource(
                R.string.play_mode_public_name,
            ),
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
            color = DominoSemanticColors.brandSupportingText,
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = stringResource(
                R.string.play_mode_table_name,
                tableName,
            ),
            color = DominoSemanticColors.brandText,
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
            color = DominoSemanticColors.brandSupportingText,
            style = MaterialTheme.typography.bodySmall,
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
