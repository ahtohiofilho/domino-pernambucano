package com.ahtohiofilho.dominopernambucano.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoPernambucanoTheme
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors

private val PrimaryContentPadding = PaddingValues(
    horizontal = 20.dp,
    vertical = 14.dp,
)

private val SecondaryContentPadding = PaddingValues(
    horizontal = 20.dp,
    vertical = 13.dp,
)

@Composable
internal fun DominoPrimaryButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = 54.dp)
            .loadingSemantics(loading),
        shape = MaterialTheme.shapes.large,
        contentPadding = PrimaryContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = DominoSemanticColors.primarySurface,
            contentColor = DominoSemanticColors.primaryTextOnLight,
        ),
    ) {
        DominoActionContent(
            text = text,
            loading = loading,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
internal fun DominoSecondaryButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = 52.dp)
            .loadingSemantics(loading),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = DominoSemanticColors.highContrastBorder,
        ),
        contentPadding = SecondaryContentPadding,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
    ) {
        DominoActionContent(
            text = text,
            loading = loading,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun DominoTextAction(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = 48.dp)
            .loadingSemantics(loading),
        colors = ButtonDefaults.textButtonColors(
            contentColor = DominoSemanticColors.primaryTextOnDark,
        ),
    ) {
        DominoActionContent(
            text = text,
            loading = loading,
            fontWeight = FontWeight.Bold,
            useLabelStyle = true,
        )
    }
}

@Composable
private fun DominoActionContent(
    text: String,
    loading: Boolean,
    fontWeight: FontWeight,
    useLabelStyle: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = LocalContentColor.current

        Text(
            text = text,
            color = contentColor.copy(
                alpha = if (loading) 0f else contentColor.alpha,
            ),
            style = if (useLabelStyle) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = fontWeight,
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .clearAndSetSemantics {},
                color = contentColor,
                strokeWidth = 2.dp,
            )
        }
    }
}

private fun Modifier.loadingSemantics(
    loading: Boolean,
): Modifier {
    return if (loading) {
        semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    } else {
        this
    }
}

@Preview(
    name = "Ações habilitadas",
    showBackground = true,
    backgroundColor = 0xFF08275C,
)
@Composable
private fun DominoButtonsEnabledPreview() {
    DominoPernambucanoTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            DominoPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Ação principal",
                onClick = {},
            )
            DominoSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Ação secundária",
                onClick = {},
            )
            DominoTextAction(
                text = "Ação textual",
                onClick = {},
            )
        }
    }
}

@Preview(
    name = "Ações em andamento",
    showBackground = true,
    backgroundColor = 0xFF08275C,
)
@Composable
private fun DominoButtonsLoadingPreview() {
    DominoPernambucanoTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            DominoPrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Ação principal",
                onClick = {},
                loading = true,
            )
            DominoSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Ação secundária",
                onClick = {},
                loading = true,
            )
        }
    }
}
