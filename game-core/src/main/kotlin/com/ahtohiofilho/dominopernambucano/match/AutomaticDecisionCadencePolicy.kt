package com.ahtohiofilho.dominopernambucano.match

import java.util.Locale

/**
 * Shared human-like decision cadence for non-human participants.
 *
 * Ranked SYNTHETIC identities and server-controlled APPLICATION participants
 * deliberately consume this same deterministic policy. Identity chooses a
 * stable personal center, while each logical turn receives bounded jitter.
 */
object AutomaticDecisionCadencePolicy {
    const val MinDecisionDelayMillis = 1_000L
    const val MaxDecisionDelayMillis = 4_000L

    private const val PersonalityBaseMinMillis = 1_800L
    private const val PersonalitySpreadMillis = 1_400
    private const val DecisionJitterSampleRange = 1_601
    private const val DecisionJitterCenterMillis = 800

    fun resolvePersonalityBaseDelayMillis(
        identityKey: String,
    ): Long {
        val normalizedIdentity = identityKey
            .trim()
            .uppercase(Locale.ROOT)

        val personalityOffsetMillis = Math.floorMod(
            normalizedIdentity.hashCode(),
            PersonalitySpreadMillis + 1,
        )

        return PersonalityBaseMinMillis +
            personalityOffsetMillis.toLong()
    }

    fun resolveDecisionDelayMillis(
        identityKey: String,
        roundNumber: Int,
        localSeatIndex: Int,
        gameStateHash: Int,
    ): Long {
        val normalizedIdentity = identityKey
            .trim()
            .uppercase(Locale.ROOT)

        val seedPrefix =
            "$normalizedIdentity|" +
                "$roundNumber|" +
                "$localSeatIndex|" +
                gameStateHash

        val sampleA = Math.floorMod(
            "$seedPrefix|A".hashCode(),
            DecisionJitterSampleRange,
        )
        val sampleB = Math.floorMod(
            "$seedPrefix|B".hashCode(),
            DecisionJitterSampleRange,
        )
        val triangularJitterMillis =
            ((sampleA + sampleB) / 2) -
                DecisionJitterCenterMillis

        return (
            resolvePersonalityBaseDelayMillis(
                identityKey = normalizedIdentity,
            ) + triangularJitterMillis.toLong()
        ).coerceIn(
            MinDecisionDelayMillis,
            MaxDecisionDelayMillis,
        )
    }
}