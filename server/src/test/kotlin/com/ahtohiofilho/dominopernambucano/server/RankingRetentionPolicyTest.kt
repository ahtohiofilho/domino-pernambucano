package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingRetentionPolicyTest {
    @Test
    fun current_policy_defines_versioned_top_n_by_cycle() {
        val policy = RankingRetentionPolicy.current

        assertEquals(1, policy.version)
        assertEquals(100, policy.limitFor(RankingCycleKind.DAILY))
        assertEquals(500, policy.limitFor(RankingCycleKind.WEEKLY))
        assertEquals(1_000, policy.limitFor(RankingCycleKind.MONTHLY))
        assertEquals(5_000, policy.limitFor(RankingCycleKind.ANNUAL))
    }
}
