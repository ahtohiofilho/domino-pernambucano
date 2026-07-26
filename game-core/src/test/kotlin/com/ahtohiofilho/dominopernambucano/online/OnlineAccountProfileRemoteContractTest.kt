package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnlineAccountProfileRemoteContractTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun route_and_profile_contract_are_stable() {
        assertEquals(
            "accounts/profile",
            OnlineRemoteRoutes.ACCOUNT_PROFILE,
        )

        val request = OnlineAccountProfileUpdateRequestDto(
            publicDisplayName = "Antônio Filho",
            tableName = "AFI",
        )
        val response = OnlineAccountProfileResponseDto(
            publicDisplayName = "Antônio Filho",
            tableName = "AFI",
            updatedAtEpochMillis = 2_000L,
        )

        assertEquals(
            request,
            json.decodeFromString<
                OnlineAccountProfileUpdateRequestDto
            >(
                json.encodeToString(request),
            ),
        )
        assertEquals(
            response,
            json.decodeFromString<
                OnlineAccountProfileResponseDto
            >(
                json.encodeToString(response),
            ),
        )

        val encodedResponse = json.encodeToString(response)

        assertFalse(encodedResponse.contains("accountId"))
        assertFalse(encodedResponse.contains("playerId"))
    }
}
