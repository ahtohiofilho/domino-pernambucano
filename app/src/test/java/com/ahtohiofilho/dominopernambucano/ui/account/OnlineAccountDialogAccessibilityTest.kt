package com.ahtohiofilho.dominopernambucano.ui.account

import androidx.compose.ui.graphics.Color
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAccountDialogAccessibilityTest {
    @Test
    fun account_dialog_text_pairs_meet_wcag_aa_normal_text() {
        assertContrastAtLeast(
            foreground = DominoSemanticColors.dialogTitle,
            background = DominoSemanticColors.dialogSurface,
            expectedMinimum = WCAG_AA_NORMAL_TEXT_MINIMUM,
            label = "title",
        )
        assertContrastAtLeast(
            foreground = DominoSemanticColors.dialogBody,
            background = DominoSemanticColors.dialogSurface,
            expectedMinimum = WCAG_AA_NORMAL_TEXT_MINIMUM,
            label = "body",
        )
        assertContrastAtLeast(
            foreground = DominoSemanticColors.dialogActionContent,
            background = DominoSemanticColors.dialogAction,
            expectedMinimum = WCAG_AA_NORMAL_TEXT_MINIMUM,
            label = "primary action",
        )
        assertContrastAtLeast(
            foreground =
                DominoSemanticColors.dialogDisabledActionContent,
            background = DominoSemanticColors.dialogDisabledAction,
            expectedMinimum = WCAG_AA_NORMAL_TEXT_MINIMUM,
            label = "disabled primary action",
        )
        assertContrastAtLeast(
            foreground = DominoSemanticColors.dialogDismissAction,
            background = DominoSemanticColors.dialogSurface,
            expectedMinimum = WCAG_AA_NORMAL_TEXT_MINIMUM,
            label = "dismiss action",
        )
        assertContrastAtLeast(
            foreground =
                DominoSemanticColors.dialogDisabledDismissAction,
            background = DominoSemanticColors.dialogSurface,
            expectedMinimum = WCAG_AA_NORMAL_TEXT_MINIMUM,
            label = "disabled dismiss action",
        )
    }

    @Test
    fun account_dialog_uses_an_opaque_light_surface() {
        assertTrue(
            "The dialog container must remain visibly light.",
            relativeLuminance(
                DominoSemanticColors.dialogSurface,
            ) >= 0.90,
        )

        listOf(
            DominoSemanticColors.dialogSurface,
            DominoSemanticColors.dialogTitle,
            DominoSemanticColors.dialogBody,
            DominoSemanticColors.dialogAction,
            DominoSemanticColors.dialogActionContent,
            DominoSemanticColors.dialogDisabledAction,
            DominoSemanticColors.dialogDisabledActionContent,
            DominoSemanticColors.dialogDismissAction,
            DominoSemanticColors.dialogDisabledDismissAction,
        ).forEach { color ->
            assertEquals(1f, color.alpha, 0f)
        }
    }

    @Test
    fun account_dialog_palette_remains_brand_consistent_and_stable() {
        assertEquals(
            Color(0xFFF7F8FC),
            DominoSemanticColors.dialogSurface,
        )
        assertEquals(
            Color(0xFF0B2D63),
            DominoSemanticColors.dialogTitle,
        )
        assertEquals(
            Color(0xFF16365C),
            DominoSemanticColors.dialogBody,
        )
        assertEquals(
            Color(0xFF0B3B7A),
            DominoSemanticColors.dialogAction,
        )
        assertEquals(
            Color.White,
            DominoSemanticColors.dialogActionContent,
        )
        assertEquals(
            Color(0xFF466A98),
            DominoSemanticColors.dialogDisabledAction,
        )
        assertEquals(
            Color.White,
            DominoSemanticColors.dialogDisabledActionContent,
        )
        assertEquals(
            Color(0xFF0B3B7A),
            DominoSemanticColors.dialogDismissAction,
        )
        assertEquals(
            Color(0xFF59697D),
            DominoSemanticColors.dialogDisabledDismissAction,
        )
    }

    private fun assertContrastAtLeast(
        foreground: Color,
        background: Color,
        expectedMinimum: Double,
        label: String,
    ) {
        val actual = contrastRatio(
            foreground = foreground,
            background = background,
        )

        assertTrue(
            "$label contrast must be at least " +
                "$expectedMinimum: actual=$actual",
            actual >= expectedMinimum,
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
