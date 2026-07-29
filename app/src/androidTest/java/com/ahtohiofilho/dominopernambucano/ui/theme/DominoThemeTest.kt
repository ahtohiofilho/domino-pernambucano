package com.ahtohiofilho.dominopernambucano.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DominoThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theme_exposes_brand_color_typography_shapes_and_spacing() {
        var primary: Color? = null
        var titleMediumSize = 0f
        var largeShape: Any? = null
        var mediumSpacing: Dp? = null

        composeRule.setContent {
            DominoPernambucanoTheme {
                primary = MaterialTheme.colorScheme.primary
                titleMediumSize =
                    MaterialTheme.typography.titleMedium.fontSize.value
                largeShape = MaterialTheme.shapes.large
                mediumSpacing = MaterialTheme.dominoSpacing.md

                Box(
                    modifier = Modifier.testTag("theme"),
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(DominoColorTokens.PernambucoBlue, primary)
            assertEquals(16f, titleMediumSize, 0f)
            assertNotNull(largeShape)
            assertEquals(DominoShapes.large, largeShape)
            assertEquals(16.dp, mediumSpacing)
        }
    }
}
