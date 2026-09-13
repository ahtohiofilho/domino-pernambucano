package com.ahtohiofilho.dominopernambucano.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ahtohiofilho.dominopernambucano.R

class AndroidTilePlacementSoundPlayer(
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

    private val selector =
        NonRepeatingSoundIndexSelector(
            soundCount = SoundResources.size,
        )

    private val loaded = BooleanArray(
        SoundResources.size,
    )

    private val soundIds: IntArray

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

            synchronized(lock) {
                val index = soundIds
                    .indexOf(sampleId)

                if (index >= 0) {
                    loaded[index] = true
                }
            }
        }

        soundIds = SoundResources
            .map { resourceId ->
                soundPool.load(
                    context,
                    resourceId,
                    1,
                )
            }
            .toIntArray()
    }

    fun playTilePlacement() {
        if (
            released ||
            !AndroidSoundEffectsPreferences.isEnabled(context)
        ) {
            return
        }

        val preferredIndex = synchronized(lock) {
            selector.nextIndex()
        }

        val indexToPlay = synchronized(lock) {
            if (loaded[preferredIndex]) {
                preferredIndex
            } else {
                loaded.indices.firstOrNull { index ->
                    loaded[index]
                }
            }
        } ?: return

        val volume = SoundVolumes[indexToPlay]

        soundPool.play(
            soundIds[indexToPlay],
            volume,
            volume,
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

    private companion object {
        val SoundResources = intArrayOf(
            R.raw.tile_place_01,
            R.raw.tile_place_02,
            R.raw.tile_place_03,
            R.raw.tile_place_04,
            R.raw.tile_place_05,
        )

        val SoundVolumes = floatArrayOf(
            0.99f,
            0.80f,
            0.96f,
            0.63f,
            0.96f,
        )
    }
}
