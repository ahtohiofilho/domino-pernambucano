package com.ahtohiofilho.dominopernambucano.ui.audio

import kotlin.random.Random

class TilePlacementAudioTransitionTracker(
    initiallyPresentingMove: Boolean,
) {
    private var wasPresentingMove = initiallyPresentingMove

    fun accept(
        isPresentingMove: Boolean,
    ): Boolean {
        val shouldPlay =
            wasPresentingMove && !isPresentingMove

        wasPresentingMove = isPresentingMove

        return shouldPlay
    }
}

internal class NonRepeatingSoundIndexSelector(
    private val soundCount: Int,
    private val nextInt: (Int) -> Int = { bound ->
        Random.nextInt(bound)
    },
) {
    private var lastIndex: Int? = null

    init {
        require(soundCount > 0)
    }

    fun nextIndex(): Int {
        if (soundCount == 1) {
            lastIndex = 0
            return 0
        }

        val previous = lastIndex

        val selected = if (previous == null) {
            nextInt(soundCount)
        } else {
            val candidate = nextInt(soundCount - 1)
            if (candidate >= previous) {
                candidate + 1
            } else {
                candidate
            }
        }

        lastIndex = selected
        return selected
    }
}
