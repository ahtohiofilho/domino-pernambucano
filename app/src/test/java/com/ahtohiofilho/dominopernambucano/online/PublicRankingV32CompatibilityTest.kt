package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class PublicRankingV32CompatibilityTest {
    @Test
    fun v32_json_policy_ignores_new_ranking_table_name_field() {
        val payload = """
            {
              "displayName": "Maria Isabel",
              "tableName": "ISA"
            }
        """.trimIndent()

        val decoded = createOnlineJson()
            .decodeFromString<LegacyV32RankingIdentity>(payload)

        assertEquals("Maria Isabel", decoded.displayName)
    }
}

@Serializable
private data class LegacyV32RankingIdentity(
    val displayName: String? = null,
)
