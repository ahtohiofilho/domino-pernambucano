package com.ahtohiofilho.dominopernambucano.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ahtohiofilho.dominopernambucano.R

enum class MatchResultAudioOutcome {
    Victory,
    Defeat,
}

class MatchResultAudioTransitionTracker(
    initiallyMatchFinished: Boolean,
) {
    private var matchFinishedEpisodeActive =
        initiallyMatchFinished

    private var suppressCurrentEpisode =
        initiallyMatchFinished

    private var emittedForCurrentEpisode = false

    fun accept(
        isMatchFinished: Boolean,
        winnerTeamIndex: Int?,
        localPlayerIndex: Int,
    ): MatchResultAudioOutcome? {
        if (!isMatchFinished) {
            matchFinishedEpisodeActive = false
            suppressCurrentEpisode = false
            emittedForCurrentEpisode = false
            return null
        }

        if (!matchFinishedEpisodeActive) {
            matchFinishedEpisodeActive = true
            suppressCurrentEpisode = false
            emittedForCurrentEpisode = false
        }

        if (
            suppressCurrentEpisode ||
            emittedForCurrentEpisode
        ) {
            return null
        }

        val localTeamIndex = localPlayerIndex % 2
        val opponentTeamIndex =
            if (localTeamIndex == 0) 1 else 0

        val outcome = when (winnerTeamIndex) {
            localTeamIndex -> MatchResultAudioOutcome.Victory
            opponentTeamIndex -> MatchResultAudioOutcome.Defeat
            else -> null
        }

        if (outcome != null) {
            emittedForCurrentEpisode = true
        }

        return outcome
    }
}

class AndroidMatchResultSoundPlayer(
    private val context: Context,
) {
    private val lock = Any()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION,
                )
                .build(),
        )
        .build()

    private val soundIds = IntArray(SoundCount)
    private val loaded = BooleanArray(SoundCount)

    private var pendingOutcome: MatchResultAudioOutcome? = null
    private var released = false

    init {
        soundPool.setOnLoadCompleteListener {
                _,
                sampleId,
                status,
            ->
            if (status != 0) {
                return@setOnLoadCompleteListener
            }

            var pendingToPlay: MatchResultAudioOutcome? = null

            synchronized(lock) {
                val index = soundIds.indexOf(sampleId)

                if (index >= 0) {
                    loaded[index] = true

                    val pending = pendingOutcome

                    if (
                        pending != null &&
                        soundIndex(pending) == index
                    ) {
                        pendingOutcome = null
                        pendingToPlay = pending
                    }
                }
            }

            pendingToPlay?.let { outcome ->
                playLoaded(outcome)
            }
        }

        soundIds[VictoryIndex] = soundPool.load(
            context,
            R.raw.match_result_victory,
            1,
        )

        soundIds[DefeatIndex] = soundPool.load(
            context,
            R.raw.match_result_defeat,
            1,
        )
    }

    fun play(
        outcome: MatchResultAudioOutcome,
    ) {
        if (
            released ||
            !AndroidSoundEffectsPreferences.isEnabled(context)
        ) {
            return
        }

        val ready = synchronized(lock) {
            val index = soundIndex(outcome)

            if (loaded[index]) {
                true
            } else {
                pendingOutcome = outcome
                false
            }
        }

        if (ready) {
            playLoaded(outcome)
        }
    }

    fun release() {
        if (released) {
            return
        }

        released = true
        soundPool.release()
    }

    private fun playLoaded(
        outcome: MatchResultAudioOutcome,
    ) {
        if (
            released ||
            !AndroidSoundEffectsPreferences.isEnabled(context)
        ) {
            return
        }

        val index = soundIndex(outcome)

        soundPool.play(
            soundIds[index],
            ResultVolume,
            ResultVolume,
            1,
            0,
            1f,
        )
    }

    private fun soundIndex(
        outcome: MatchResultAudioOutcome,
    ): Int {
        return when (outcome) {
            MatchResultAudioOutcome.Victory -> VictoryIndex
            MatchResultAudioOutcome.Defeat -> DefeatIndex
        }
    }

    private companion object {
        const val VictoryIndex = 0
        const val DefeatIndex = 1
        const val SoundCount = 2
        const val ResultVolume = 0.5f
    }
}