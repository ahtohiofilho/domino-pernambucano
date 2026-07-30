package com.ahtohiofilho.dominopernambucano.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object DominoBrandShapes {
    val compact = RoundedCornerShape(8.dp)
    val control = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(20.dp)
    val hero = RoundedCornerShape(28.dp)
}

val DominoShapes = Shapes(
    extraSmall = DominoBrandShapes.compact,
    small = DominoBrandShapes.compact,
    medium = DominoBrandShapes.control,
    large = DominoBrandShapes.card,
    extraLarge = DominoBrandShapes.hero,
)
