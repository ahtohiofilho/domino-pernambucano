package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RankingPublicationPolicyTest {
    @Test
    fun production_thresholds_are_canonical() {
        val expected = mapOf(
            RankingCycleKind.DAILY to 100,
            RankingCycleKind.WEEKLY to 500,
            RankingCycleKind.MONTHLY to 1_000,
            RankingCycleKind.ANNUAL to 5_000,
        )

        assertEquals(
            RankingPublicationProfile.PRODUCTION,
            RankingPublicationPolicy.production.profile,
        )
        expected.forEach { (kind, threshold) ->
            assertEquals(
                threshold,
                RankingPublicationPolicy.production.thresholdFor(kind),
            )
        }
        assertEquals(
            RankingPublicationPolicy.production,
            DEFAULT_RANKING_PUBLICATION_POLICY,
        )
    }

    @Test
    fun homologation_thresholds_are_fixed_at_four() {
        assertEquals(
            RankingPublicationProfile.HOMOLOGATION,
            RankingPublicationPolicy.homologation.profile,
        )
        RankingCycleKind.values().forEach { kind ->
            assertEquals(
                4,
                RankingPublicationPolicy.homologation.thresholdFor(kind),
            )
        }
    }

    @Test
    fun miniproduction_thresholds_are_fixed_at_twelve() {
        assertEquals(
            RankingPublicationProfile.MINIPRODUCTION,
            RankingPublicationPolicy.miniproduction.profile,
        )
        RankingCycleKind.values().forEach { kind ->
            assertEquals(
                12,
                RankingPublicationPolicy.miniproduction
                    .thresholdFor(kind),
            )
        }
    }

    @Test
    fun publication_boundary_is_evaluated_without_truncating_capacity() {
        RankingCycleKind.values().forEach { kind ->
            val policy = RankingPublicationPolicy.production
            val threshold = policy.thresholdFor(kind)

            val below = policy.evaluate(
                kind = kind,
                totalEligiblePlayers = threshold - 1,
            )
            assertEquals(
                RankingPublicationStatus.BELOW_THRESHOLD,
                below.status,
            )
            assertFalse(below.isPublished)
            assertEquals(1, below.eligiblePlayersRemaining)

            val atThreshold = policy.evaluate(
                kind = kind,
                totalEligiblePlayers = threshold,
            )
            assertEquals(
                RankingPublicationStatus.PUBLISHED,
                atThreshold.status,
            )
            assertTrue(atThreshold.isPublished)
            assertEquals(0, atThreshold.eligiblePlayersRemaining)

            val above = policy.evaluate(
                kind = kind,
                totalEligiblePlayers = threshold + 1,
            )
            assertEquals(
                RankingPublicationStatus.PUBLISHED,
                above.status,
            )
            assertTrue(above.isPublished)
            assertEquals(0, above.eligiblePlayersRemaining)
            assertEquals(
                threshold + 1,
                above.totalEligiblePlayers,
            )
        }
    }

    @Test
    fun server_environment_selects_a_fixed_publication_profile() {
        assertEquals(
            RankingPublicationPolicy.homologation,
            OnlineServerEnvironment.HOMOLOGATION
                .rankingPublicationPolicy,
        )
        assertEquals(
            RankingPublicationPolicy.miniproduction,
            OnlineServerEnvironment.MINIPRODUCTION
                .rankingPublicationPolicy,
        )

        listOf(
            OnlineServerEnvironment.DEVELOPMENT,
            OnlineServerEnvironment.TEST,
            OnlineServerEnvironment.PRODUCTION,
        ).forEach { environment ->
            assertEquals(
                RankingPublicationPolicy.production,
                environment.rankingPublicationPolicy,
            )
        }
    }

    @Test
    fun test_profile_accepts_an_injected_policy() {
        val thresholds = RankingPublicationThresholds(
            daily = 2,
            weekly = 3,
            monthly = 5,
            annual = 8,
        )
        val policy = RankingPublicationPolicy.test(thresholds)

        assertEquals(RankingPublicationProfile.TEST, policy.profile)
        assertEquals(2, policy.thresholdFor(RankingCycleKind.DAILY))
        assertEquals(3, policy.thresholdFor(RankingCycleKind.WEEKLY))
        assertEquals(5, policy.thresholdFor(RankingCycleKind.MONTHLY))
        assertEquals(8, policy.thresholdFor(RankingCycleKind.ANNUAL))
    }

    @Test
    fun thresholds_must_be_positive() {
        try {
            RankingPublicationThresholds(
                daily = 0,
                weekly = 1,
                monthly = 1,
                annual = 1,
            )
            fail("Threshold diário inválido foi aceito.")
        } catch (_: IllegalArgumentException) {
            // Resultado esperado.
        }

        try {
            RankingPublicationDecision(
                publicationThreshold = 1,
                totalEligiblePlayers = -1,
            )
            fail("Total negativo de elegíveis foi aceito.")
        } catch (_: IllegalArgumentException) {
            // Resultado esperado.
        }
    }
}
