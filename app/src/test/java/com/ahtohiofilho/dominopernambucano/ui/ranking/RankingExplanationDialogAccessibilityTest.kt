package com.ahtohiofilho.dominopernambucano.ui.ranking

import androidx.compose.ui.graphics.Color
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingExplanationDialogAccessibilityTest {
    @Test
    fun ranking_explanation_dialog_reuses_approved_dialog_semantics() {
        assertEquals(
            DominoSemanticColors.dialogSurface,
            RankingExplanationDialogColors.container,
        )
        assertEquals(
            DominoSemanticColors.dialogTitle,
            RankingExplanationDialogColors.title,
        )
        assertEquals(
            DominoSemanticColors.dialogBody,
            RankingExplanationDialogColors.body,
        )
        assertEquals(
            DominoSemanticColors.dialogDismissAction,
            RankingExplanationDialogColors.action,
        )
    }

    @Test
    fun ranking_explanation_dialog_palette_meets_wcag_aa_normal_text() {
        assertContrastAtLeast(
            foreground = RankingExplanationDialogColors.title,
            background = RankingExplanationDialogColors.container,
            label = "title",
        )
        assertContrastAtLeast(
            foreground = RankingExplanationDialogColors.body,
            background = RankingExplanationDialogColors.container,
            label = "body",
        )
        assertContrastAtLeast(
            foreground = RankingExplanationDialogColors.action,
            background = RankingExplanationDialogColors.container,
            label = "action",
        )
    }

    private fun assertContrastAtLeast(
        foreground: Color,
        background: Color,
        label: String,
    ) {
        val actual = contrastRatio(
            foreground = foreground,
            background = background,
        )

        assertTrue(
            "$label contrast must be at least " +
                "$WCAG_AA_NORMAL_TEXT_MINIMUM: actual=$actual",
            actual >= WCAG_AA_NORMAL_TEXT_MINIMUM,
        )
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        val lighter = max(foregroundLuminance, backgroundLuminance)
        val darker = min(foregroundLuminance, backgroundLuminance)

        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val red = linearize(color.red.toDouble())
        val green = linearize(color.green.toDouble())
        val blue = linearize(color.blue.toDouble())

        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun linearize(channel: Double): Double {
        return if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private companion object {
        const val WCAG_AA_NORMAL_TEXT_MINIMUM = 4.5
    }
}