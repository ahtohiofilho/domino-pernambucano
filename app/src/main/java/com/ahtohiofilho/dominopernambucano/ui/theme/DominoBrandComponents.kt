package com.ahtohiofilho.dominopernambucano.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

enum class DominoBrandAccent {
    Yellow,
    Red,
    Green,
    Blue,
}

enum class DominoScreenLayout {
    Centered,
    Top,
}

@Composable
fun DominoBrandScaffold(
    modifier: Modifier = Modifier,
    contentVerticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DominoSemanticColors.brandBackground),
    ) {
        DominoPatternBackground(
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = MaterialTheme.dominoSpacing.lg,
                    vertical = MaterialTheme.dominoSpacing.md,
                )
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.dominoSpacing.md,
                alignment = contentVerticalAlignment,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
fun DominoScreenScaffold(
    title: String,
    layout: DominoScreenLayout,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    statusMessage: String? = null,
    statusModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DominoScreenScaffold(
        title = AnnotatedString(title),
        layout = layout,
        modifier = modifier,
        contentModifier = contentModifier,
        titleModifier = titleModifier,
        statusMessage = statusMessage,
        statusModifier = statusModifier,
        content = content,
    )
}

@Composable
fun DominoScreenScaffold(
    title: AnnotatedString,
    layout: DominoScreenLayout,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    statusMessage: String? = null,
    statusModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val verticalAlignment = when (layout) {
        DominoScreenLayout.Centered ->
            Alignment.CenterVertically
        DominoScreenLayout.Top -> Alignment.Top
    }

    DominoBrandScaffold(
        modifier = modifier,
        contentVerticalAlignment = verticalAlignment,
    ) {
        Column(
            modifier = contentModifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.dominoSpacing.sm,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DominoScreenTitle(
                text = title,
                modifier = titleModifier,
            )

            statusMessage?.let { message ->
                DominoScreenStatus(
                    text = message,
                    modifier = statusModifier,
                )
            }

            content()
        }
    }
}

@Composable
fun BoxScope.DominoPatternBackground(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.5.dp.toPx()
        val tileWidth = 68.dp.toPx()
        val tileHeight = 34.dp.toPx()
        val radius = 10.dp.toPx()
        val patternColor = DominoSemanticColors.brandPattern

        fun drawTile(
            topLeft: Offset,
            horizontal: Boolean,
        ) {
            val width = if (horizontal) tileWidth else tileHeight
            val height = if (horizontal) tileHeight else tileWidth
            val tileSize = Size(width, height)

            drawRoundRect(
                color = patternColor,
                topLeft = topLeft,
                size = tileSize,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = strokeWidth),
            )

            if (horizontal) {
                val middleX = topLeft.x + width / 2f
                drawLine(
                    color = patternColor,
                    start = Offset(middleX, topLeft.y + 4.dp.toPx()),
                    end = Offset(
                        middleX,
                        topLeft.y + height - 4.dp.toPx(),
                    ),
                    strokeWidth = strokeWidth,
                )
            } else {
                val middleY = topLeft.y + height / 2f
                drawLine(
                    color = patternColor,
                    start = Offset(topLeft.x + 4.dp.toPx(), middleY),
                    end = Offset(
                        topLeft.x + width - 4.dp.toPx(),
                        middleY,
                    ),
                    strokeWidth = strokeWidth,
                )
            }
        }

        drawTile(
            topLeft = Offset(
                x = -18.dp.toPx(),
                y = 88.dp.toPx(),
            ),
            horizontal = false,
        )
        drawTile(
            topLeft = Offset(
                x = size.width - 52.dp.toPx(),
                y = 188.dp.toPx(),
            ),
            horizontal = true,
        )
        drawTile(
            topLeft = Offset(
                x = 22.dp.toPx(),
                y = size.height - 118.dp.toPx(),
            ),
            horizontal = true,
        )
        drawTile(
            topLeft = Offset(
                x = size.width - 48.dp.toPx(),
                y = size.height - 92.dp.toPx(),
            ),
            horizontal = false,
        )
    }
}

@Composable
fun DominoBrandHeader(
    subtitle: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            MaterialTheme.dominoSpacing.xs,
        ),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(color = DominoColorTokens.AccentYellow),
                ) {
                    append("D")
                }
                withStyle(
                    SpanStyle(color = DominoColorTokens.AccentRed),
                ) {
                    append("o")
                }
                withStyle(
                    SpanStyle(color = DominoColorTokens.AccentYellow),
                ) {
                    append("m")
                }
                withStyle(
                    SpanStyle(color = DominoColorTokens.AccentGreen),
                ) {
                    append("i")
                }
                withStyle(
                    SpanStyle(color = DominoColorTokens.AccentYellow),
                ) {
                    append("n")
                }
                withStyle(
                    SpanStyle(color = DominoColorTokens.PureWhite),
                ) {
                    append("ó ")
                }
                withStyle(
                    SpanStyle(color = DominoColorTokens.AccentRed),
                ) {
                    append("P")
                }
                withStyle(
                    SpanStyle(color = DominoColorTokens.PureWhite),
                ) {
                    append("E")
                }
            },
            color = DominoSemanticColors.brandText,
            style = if (compact) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.displaySmall
            },
            fontWeight = FontWeight.ExtraBold,
        )

        subtitle?.let { subtitleText ->
            Text(
                text = subtitleText,
                color = DominoSemanticColors.brandText.copy(
                    alpha = 0.88f,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun DominoScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    DominoScreenTitle(
        text = AnnotatedString(text),
        modifier = modifier,
    )
}

@Composable
fun DominoScreenTitle(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                heading()
            },
        text = text,
        color = DominoSemanticColors.brandText,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun DominoScreenStatus(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        text = text,
        color = DominoSemanticColors.brandText.copy(
            alpha = 0.86f,
        ),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun DominoIconBadge(
    accent: DominoBrandAccent,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val accentColor = accent.color()

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(accentColor)
            .border(
                width = 2.dp,
                color = DominoColorTokens.PureWhite.copy(
                    alpha = 0.28f,
                ),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun DominoPrimaryActionCard(
    title: String,
    supportingText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val shape = DominoBrandShapes.hero

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = shape,
            )
            .clip(shape)
            .background(DominoSemanticColors.brandPrimaryAction)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.58f)
            .heightIn(min = 96.dp)
            .padding(
                horizontal = MaterialTheme.dominoSpacing.lg,
                vertical = MaterialTheme.dominoSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.let { content ->
            DominoIconBadge(
                accent = DominoBrandAccent.Blue,
                content = content,
            )
            Spacer(modifier = Modifier.width(
                MaterialTheme.dominoSpacing.md,
            ))
        }

        DominoActionCopy(
            title = title,
            supportingText = supportingText,
            titleColor =
                DominoSemanticColors.brandPrimaryActionContent,
            supportingColor =
                DominoSemanticColors.brandPrimaryActionContent.copy(
                    alpha = 0.76f,
                ),
            modifier = Modifier.weight(1f),
        )

        DominoChevron(
            color = DominoSemanticColors.brandPrimaryActionContent,
        )
    }
}

@Composable
fun DominoSecondaryActionCard(
    title: String,
    supportingText: String?,
    accent: DominoBrandAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevated: Boolean = false,
    leadingContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val shape = DominoBrandShapes.card
    val accentColor = accent.color()
    val containerColor = if (elevated) {
        DominoSemanticColors.brandSurfaceElevated
    } else {
        DominoSemanticColors.brandSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (elevated) 4.dp else 0.dp,
                shape = shape,
                clip = false,
            )
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = accentColor.copy(
                    alpha = if (elevated) 0.82f else 0.72f,
                ),
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.58f)
            .heightIn(min = 84.dp)
            .padding(
                horizontal = MaterialTheme.dominoSpacing.md,
                vertical = MaterialTheme.dominoSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.let { content ->
            DominoIconBadge(
                accent = accent,
                content = content,
            )
            Spacer(modifier = Modifier.width(
                MaterialTheme.dominoSpacing.md,
            ))
        }

        DominoActionCopy(
            title = title,
            supportingText = supportingText,
            titleColor = DominoSemanticColors.brandText,
            supportingColor =
                DominoSemanticColors.brandSupportingText,
            modifier = Modifier.weight(1f),
        )

        DominoChevron(
            color = DominoSemanticColors.brandText,
        )
    }
}

@Composable
private fun DominoActionCopy(
    title: String,
    supportingText: String?,
    titleColor: Color,
    supportingColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(
            MaterialTheme.dominoSpacing.xxs,
        ),
    ) {
        Text(
            text = title,
            color = titleColor,
            style = MaterialTheme.typography.titleLarge,
        )

        supportingText?.let { support ->
            Text(
                text = support,
                color = supportingColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DominoChevron(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(24.dp),
    ) {
        val strokeWidth = 2.5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.36f, size.height * 0.24f),
            end = Offset(size.width * 0.66f, size.height * 0.50f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.66f, size.height * 0.50f),
            end = Offset(size.width * 0.36f, size.height * 0.76f),
            strokeWidth = strokeWidth,
        )
    }
}

private fun DominoBrandAccent.color(): Color {
    return when (this) {
        DominoBrandAccent.Yellow -> DominoColorTokens.AccentYellow
        DominoBrandAccent.Red -> DominoColorTokens.AccentRed
        DominoBrandAccent.Green -> DominoColorTokens.AccentGreen
        DominoBrandAccent.Blue -> DominoColorTokens.PernambucoBlueElevated
    }
}
