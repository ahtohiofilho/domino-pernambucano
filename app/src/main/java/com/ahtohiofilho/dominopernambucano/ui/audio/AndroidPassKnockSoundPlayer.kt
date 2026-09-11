package com.ahtohiofilho.dominopernambucano.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ahtohiofilho.dominopernambucano.R

class AndroidPassKnockSoundPlayer(
    private val context: Context,
) {
    private val lock = Any()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION,
                )
                .build(),
        )
        .build()

    private var soundId = 0
    private var loaded = false
    private var released = false

    init {
        soundPool.setOnLoadCompleteListener {
                _,
                sampleId,
                status,
            ->
            if (
                status == 0 &&
                sampleId == soundId
            ) {
                synchronized(lock) {
                    loaded = true
                }
            }
        }

        soundId = soundPool.load(
            context,
            R.raw.pass_knock_single,
            1,
        )
    }

    fun playImpact() {
        if (
            released ||
            !AndroidSoundEffectsPreferences.isEnabled(context)
        ) {
            return
        }

        val canPlay = synchronized(lock) {
            loaded
        }

        if (!canPlay) {
            return
        }

        soundPool.play(
            soundId,
            1f,
            1f,
            1,
            0,
            1f,
        )
    }

    fun release() {
        if (released) {
            return
        }

        released = true
        soundPool.release()
    }
}