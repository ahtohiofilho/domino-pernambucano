package com.ahtohiofilho.dominopernambucano.ui.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandAppearanceRepositoryTest {
    @Test
    fun `missing preference generates and persists initial random tone`() {
        val storage = FakeHandAppearanceStorage()
        val repository = HandAppearanceRepository(
            storage = storage,
            initialToneProvider = {
                HandAppearanceTone.TONE_3
            },
        )

        assertEquals(
            HandAppearanceTone.TONE_3,
            repository.read(),
        )
        assertEquals(
            HandAppearanceTone.TONE_3.storageValue,
            storage.value,
        )
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun `blank preference generates and persists initial random tone`() {
        val storage = FakeHandAppearanceStorage(
            value = "   ",
        )
        val repository = HandAppearanceRepository(
            storage = storage,
            initialToneProvider = {
                HandAppearanceTone.TONE_4
            },
        )

        assertEquals(
            HandAppearanceTone.TONE_4,
            repository.read(),
        )
        assertEquals(
            HandAppearanceTone.TONE_4.storageValue,
            storage.value,
        )
        assertEquals(1, storage.writeCount)
    }

    @Test
    fun `unknown stored value falls back to legacy tone without overwrite`() {
        val storage = FakeHandAppearanceStorage(
            value = "future-tone",
        )
        val repository = HandAppearanceRepository(
            storage = storage,
        )

        assertEquals(
            HandAppearanceTone.TONE_1,
            repository.read(),
        )
        assertEquals("future-tone", storage.value)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `all four tones round trip through stable storage values`() {
        HandAppearanceTone.entries.forEach { tone ->
            val storage = FakeHandAppearanceStorage()
            val repository = HandAppearanceRepository(
                storage = storage,
            )

            assertTrue(
                repository.write(tone),
            )
            assertEquals(
                tone.storageValue,
                storage.value,
            )
            assertEquals(
                tone,
                repository.read(),
            )
        }
    }

    @Test
    fun `storage values remain explicit and ordinal independent`() {
        assertEquals("tone_1", HandAppearanceTone.TONE_1.storageValue)
        assertEquals("tone_2", HandAppearanceTone.TONE_2.storageValue)
        assertEquals("tone_3", HandAppearanceTone.TONE_3.storageValue)
        assertEquals("tone_4", HandAppearanceTone.TONE_4.storageValue)
    }
}

private class FakeHandAppearanceStorage(
    var value: String? = null,
) : HandAppearanceStorage {
    var writeCount: Int = 0

    override fun readString(
        key: String,
    ): String? {
        assertEquals(
            HAND_APPEARANCE_STORAGE_KEY,
            key,
        )
        return value
    }

    override fun writeString(
        key: String,
        value: String,
    ): Boolean {
        assertEquals(
            HAND_APPEARANCE_STORAGE_KEY,
            key,
        )
        writeCount += 1
        this.value = value
        return true
    }
}