package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoBrandAccent
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPrimaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenLayout
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoScreenScaffold
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSecondaryActionCard
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

internal const val PlayModeTitleTag = "play_mode_title"
internal const val PlayModeContentGroupTag = "play_mode_content_group"
internal const val PlayModeStatusTag = "play_mode_status"

@Composable
fun PlayModeScreen(
    onBackClick: () -> Unit,
    onlineActionInProgress: Boolean = false,
    onlineFeedbackMessage: String? = null,
    onRankedGameClick: () -> Unit,
    onLocalGameClick: () -> Unit,
    onCreateOnlineRoomClick: () -> Unit,
    onJoinOnlineRoomClick: () -> Unit,
) {
    DominoScreenScaffold(
        title = stringResource(R.string.play_mode_title),
        layout = DominoScreenLayout.Centered,
        contentModifier = Modifier.testTag(
            PlayModeContentGroupTag,
        ),
        titleModifier = Modifier.testTag(PlayModeTitleTag),
        statusMessage = onlineFeedbackMessage,
        statusModifier = Modifier.testTag(PlayModeStatusTag),
    ) {
        DominoPrimaryActionCard(
            title = stringResource(R.string.play_mode_ranked),
            supportingText = if (onlineActionInProgress) {
                stringResource(R.string.account_connecting)
            } else {
                stringResource(R.string.play_mode_ranked_support)
            },
            onClick = onRankedGameClick,
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
            onRankedGameClick = {},
            onLocalGameClick = {},
            onCreateOnlineRoomClick = {},
            onJoinOnlineRoomClick = {},
        )
    }
}
