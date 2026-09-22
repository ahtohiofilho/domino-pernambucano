package com.ahtohiofilho.dominopernambucano.ui.game

import com.ahtohiofilho.dominopernambucano.R
import com.ahtohiofilho.dominopernambucano.match.DominoMatchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PostMatchRankedRematchPolicyTest {
    @Test
    fun public_ranked_play_again_returns_to_ranked_queue() {
        assertEquals(
            PostMatchPlayAgainAction.OPEN_RANKED_QUEUE,
            resolvePostMatchPlayAgainAction(
                matchMode = DominoMatchMode.PUBLIC_RANKED,
            ),
        )
    }

    @Test
    fun local_and_private_unranked_play_again_start_new_match_in_place() {
        listOf(
            DominoMatchMode.OFFLINE_LOCAL,
            DominoMatchMode.PRIVATE_UNRANKED,
        ).forEach { matchMode ->
            assertEquals(
                PostMatchPlayAgainAction.START_NEW_MATCH,
                resolvePostMatchPlayAgainAction(
                    matchMode = matchMode,
                ),
            )
        }
    }

    @Test
    fun post_match_round_label_uses_stable_match_mode() {
        assertEquals(
            R.string.post_match_local_rounds,
            postMatchRoundsLabelResource(
                matchMode = DominoMatchMode.OFFLINE_LOCAL,
            ),
        )
        assertEquals(
            R.string.post_match_online_rounds,
            postMatchRoundsLabelResource(
                matchMode = DominoMatchMode.PRIVATE_UNRANKED,
            ),
        )
        assertEquals(
            R.string.post_match_ranked_rounds,
            postMatchRoundsLabelResource(
                matchMode = DominoMatchMode.PUBLIC_RANKED,
            ),
        )
    }
}
