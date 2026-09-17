package com.ahtohiofilho.dominopernambucano.ui.personalization

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import kotlin.random.Random

private const val HAND_APPEARANCE_PREFERENCES_NAME =
    "hand_appearance"
internal const val HAND_APPEARANCE_STORAGE_KEY =
    "skin_tone_v1"

enum class HandAppearanceTone(
    val storageValue: String,
) {
    TONE_1("tone_1"),
    TONE_2("tone_2"),
    TONE_3("tone_3"),
    TONE_4("tone_4");

    companion object {
        fun fromStorageValue(
            rawValue: String?,
        ): HandAppearanceTone {
            return fromStorageValueOrNull(
                rawValue = rawValue,
            ) ?: TONE_1
        }

        fun fromStorageValueOrNull(
            rawValue: String?,
        ): HandAppearanceTone? {
            val normalized = rawValue
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return null

            return entries.firstOrNull { tone ->
                tone.storageValue == normalized
            }
        }

        fun randomInitialTone(
            random: Random = Random.Default,
        ): HandAppearanceTone {
            return entries[random.nextInt(entries.size)]
        }
    }
}

internal interface HandAppearanceStorage {
    fun readString(
        key: String,
    ): String?

    fun writeString(
        key: String,
        value: String,
    ): Boolean
}

internal class HandAppearanceRepository(
    private val storage: HandAppearanceStorage,
    private val initialToneProvider: () -> HandAppearanceTone = {
        HandAppearanceTone.randomInitialTone()
    },
) {
    fun read(): HandAppearanceTone {
        val storedValue = storage.readString(
            key = HAND_APPEARANCE_STORAGE_KEY,
        )

        HandAppearanceTone.fromStorageValueOrNull(
            rawValue = storedValue,
        )?.let { tone ->
            return tone
        }

        if (!storedValue.isNullOrBlank()) {
            return HandAppearanceTone.TONE_1
        }

        val initialTone = initialToneProvider()

        storage.writeString(
            key = HAND_APPEARANCE_STORAGE_KEY,
            value = initialTone.storageValue,
        )

        return initialTone
    }

    fun write(
        tone: HandAppearanceTone,
    ): Boolean {
        return storage.writeString(
            key = HAND_APPEARANCE_STORAGE_KEY,
            value = tone.storageValue,
        )
    }
}

class SharedPreferencesHandAppearanceStore(
    context: Context,
) {
    private val repository = HandAppearanceRepository(
        storage = SharedPreferencesHandAppearanceStorage(
            context = context.applicationContext,
        ),
    )

    fun read(): HandAppearanceTone {
        return repository.read()
    }

    fun write(
        tone: HandAppearanceTone,
    ): Boolean {
        return repository.write(tone)
    }
}

private class SharedPreferencesHandAppearanceStorage(
    context: Context,
) : HandAppearanceStorage {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(
            HAND_APPEARANCE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun readString(
        key: String,
    ): String? {
        return preferences.getString(
            key,
            null,
        )
    }

    override fun writeString(
        key: String,
        value: String,
    ): Boolean {
        return preferences.edit()
            .putString(
                key,
                value,
            )
            .commit()
    }
}

internal fun HandAppearanceTone.toKnockHandColorFilter():
    ColorFilter? {
    return when (this) {
        HandAppearanceTone.TONE_1 -> null

        HandAppearanceTone.TONE_2 ->
            ColorFilter.tint(
                color = Color(0xFFD79B72),
                blendMode = BlendMode.Modulate,
            )

        HandAppearanceTone.TONE_3 ->
            ColorFilter.tint(
                color = Color(0xFFAD6948),
                blendMode = BlendMode.Modulate,
            )

        HandAppearanceTone.TONE_4 ->
            ColorFilter.tint(
                color = Color(0xFF6B3F2C),
                blendMode = BlendMode.Modulate,
            )
    }
}