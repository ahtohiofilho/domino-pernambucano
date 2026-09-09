package com.ahtohiofilho.dominopernambucano.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.ahtohiofilho.dominopernambucano.R

object AndroidMenuMusicController {
    private const val LogTag = "DominoMenuMusic"
    private const val MusicVolume = 0.18f

    private val lock = Any()

    private var player: MediaPlayer? = null
    private var applicationContext: Context? = null
    private var appForeground = false
    private var menuActive = false

    fun isEnabled(
        context: Context,
    ): Boolean {
        return AndroidMenuMusicPreferences.isEnabled(
            context.applicationContext,
        )
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        val appContext = context.applicationContext

        AndroidMenuMusicPreferences.setEnabled(
            context = appContext,
            enabled = enabled,
        )

        synchronized(lock) {
            applicationContext = appContext

            if (!enabled) {
                releasePlayerLocked()
            } else {
                reconcileLocked()
            }
        }
    }

    fun setMenuActive(
        context: Context,
        active: Boolean,
    ) {
        synchronized(lock) {
            applicationContext = context.applicationContext
            menuActive = active
            reconcileLocked()
        }
    }

    fun setAppForeground(
        context: Context,
        foreground: Boolean,
    ) {
        synchronized(lock) {
            applicationContext = context.applicationContext
            appForeground = foreground
            reconcileLocked()
        }
    }

    fun release() {
        synchronized(lock) {
            appForeground = false
            releasePlayerLocked()
        }
    }

    private fun reconcileLocked() {
        val context = applicationContext ?: return
        val shouldPlay =
            appForeground &&
                menuActive &&
                AndroidMenuMusicPreferences.isEnabled(context)

        if (!shouldPlay) {
            pausePlayerLocked()
            return
        }

        val activePlayer = player ?: createPlayer(
            context = context,
        )?.also { created ->
            player = created
        } ?: return

        runCatching {
            if (!activePlayer.isPlaying) {
                activePlayer.start()
            }
        }.onFailure { error ->
            Log.w(
                LogTag,
                "Unable to start menu music.",
                error,
            )
            releasePlayerLocked()
        }
    }

    private fun pausePlayerLocked() {
        val activePlayer = player ?: return

        runCatching {
            if (activePlayer.isPlaying) {
                activePlayer.pause()
            }
        }.onFailure { error ->
            Log.w(
                LogTag,
                "Unable to pause menu music.",
                error,
            )
            releasePlayerLocked()
        }
    }

    private fun createPlayer(
        context: Context,
    ): MediaPlayer? {
        val descriptor = runCatching {
            context.resources.openRawResourceFd(
                R.raw.menu_music_loop,
            )
        }.getOrElse { error ->
            Log.w(
                LogTag,
                "Unable to open menu music resource.",
                error,
            )
            return null
        }

        val newPlayer = MediaPlayer()

        return try {
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_MUSIC,
                    )
                    .build(),
            )
            newPlayer.setDataSource(
                descriptor.fileDescriptor,
                descriptor.startOffset,
                descriptor.length,
            )
            newPlayer.isLooping = true
            newPlayer.setVolume(
                MusicVolume,
                MusicVolume,
            )
            newPlayer.prepare()
            newPlayer
        } catch (error: Exception) {
            runCatching {
                newPlayer.release()
            }
            Log.w(
                LogTag,
                "Unable to prepare menu music.",
                error,
            )
            null
        } finally {
            descriptor.close()
        }
    }

    private fun releasePlayerLocked() {
        val activePlayer = player ?: return
        player = null

        runCatching {
            activePlayer.release()
        }
    }
}
