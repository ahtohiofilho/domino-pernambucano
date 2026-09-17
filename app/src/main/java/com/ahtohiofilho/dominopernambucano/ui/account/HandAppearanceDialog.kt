package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.ui.personalization.HandAppearanceTone
import com.ahtohiofilho.dominopernambucano.ui.personalization.toKnockHandColorFilter
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

@Composable
internal fun HandAppearanceDialog(
    selectedTone: HandAppearanceTone,
    onToneSelected: (HandAppearanceTone) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var pendingTone by remember(selectedTone) {
        mutableStateOf(selectedTone)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(
                    R.string.identity_hub_hand_appearance_dialog_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HandAppearanceTone.entries
                    .chunked(2)
                    .forEachIndexed { rowIndex, rowTones ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceEvenly,
                        ) {
                            rowTones.forEachIndexed {
                                    columnIndex,
                                    tone,
                                ->
                                HandAppearanceChoice(
                                    tone = tone,
                                    optionNumber =
                                        rowIndex * 2 +
                                            columnIndex + 1,
                                    selected =
                                        tone == pendingTone,
                                    onClick = {
                                        pendingTone = tone
                                    },
                                )
                            }
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onToneSelected(pendingTone)
                },
            ) {
                Text(
                    text = stringResource(
                        R.string.identity_hub_hand_appearance_confirm,
                    ),
                    color = DominoSemanticColors.dialogAction,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(
                    text = stringResource(
                        R.string.common_close,
                    ),
                    color =
                        DominoSemanticColors.dialogDismissAction,
                )
            }
        },
        containerColor = DominoSemanticColors.dialogSurface,
        titleContentColor = DominoSemanticColors.dialogTitle,
        textContentColor = DominoSemanticColors.dialogBody,
    )
}

@Composable
private fun HandAppearanceChoice(
    tone: HandAppearanceTone,
    optionNumber: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = Modifier.size(112.dp),
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = DominoSemanticColors.dialogAction,
        ),
    ) {
        val choiceShape = RoundedCornerShape(22.dp)

        Box(
            modifier = if (selected) {
                Modifier
                    .fillMaxSize()
                    .background(
                        color = DominoSemanticColors.dialogAction.copy(
                            alpha = 0.10f,
                        ),
                        shape = choiceShape,
                    )
                    .border(
                        width = 2.dp,
                        color = DominoSemanticColors.dialogAction,
                        shape = choiceShape,
                    )
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.knock_hand_base,
                ),
                contentDescription = stringResource(
                    R.string.identity_hub_hand_choice_accessibility,
                    optionNumber,
                ),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        // Mesma orientação da mão do jogador local.
                        rotationZ = 180f
                    },
                colorFilter = tone.toKnockHandColorFilter(),
            )

            if (selected) {
                Text(
                    text = "\u2713",
                    modifier = Modifier.align(
                        Alignment.TopEnd,
                    ),
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}