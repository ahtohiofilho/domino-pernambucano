package com.ahtohiofilho.dominopernambucano.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ahtohiofilho.dominopernambucano.ui.theme.DominoSemanticColors
import com.ahtohiofilho.dominopernambucano.ui.theme.dominoSpacing

internal const val MenuScaffoldTag = "menu_scaffold"
internal const val MenuScaffoldContentTag = "menu_scaffold_content"

@Composable
fun MenuScaffold(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DominoSemanticColors.appBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(
                horizontal = MaterialTheme.dominoSpacing.lg,
                vertical = MaterialTheme.dominoSpacing.md,
            )
            .testTag(MenuScaffoldTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(
                    max = 430.dp,
                )
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.dominoSpacing.xxs,
                    vertical = MaterialTheme.dominoSpacing.xs,
                )
                .testTag(MenuScaffoldContentTag),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.md,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
