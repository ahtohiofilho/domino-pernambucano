package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming

/*
 * Presentation lifecycle contract for online matches.
 *
 * The server remains authoritative and may publish later snapshots at any time.
 * These presentation phases, however, carry player-visible narrative meaning
 * and therefore keep their currently visible frame until the UI reports
 * completion. Later authoritative revisions stay buffered in the coordinator.
 *
 * Hard resync remains the explicit exception when revision history is broken.
 */
internal enum class OnlinePresentationLifecycleKind {
    ROUND_INTRO,
    MOVE,
    PASS,
    ROUND_SUMMARY,
}

internal data class OnlinePresentationLifecycleContract(
    val kind: OnlinePresentationLifecycleKind,
    val blocksSnapshotPromotionUntilUiCompletion: Boolean,
    val watchdogMillis: Long,
)

internal fun resolveOnlinePresentationLifecycleContract(
    phase: DominoMatchPhase,
    gameplayWatchdogMillis: Long,
): OnlinePresentationLifecycleContract? {
    require(gameplayWatchdogMillis > 0L)

    return when (phase) {
        DominoMatchPhase.RoundIntro ->
            OnlinePresentationLifecycleContract(
                kind = OnlinePresentationLifecycleKind.ROUND_INTRO,
                blocksSnapshotPromotionUntilUiCompletion = true,
                watchdogMillis = maxOf(
                    gameplayWatchdogMillis,
                    DominoMatchTiming.RoundIntroServerFallbackMillis + 1_000L,
                ),
            )

        is DominoMatchPhase.PresentingMove ->
            OnlinePresentationLifecycleContract(
                kind = OnlinePresentationLifecycleKind.MOVE,
                blocksSnapshotPromotionUntilUiCompletion = true,
                watchdogMillis = gameplayWatchdogMillis,
            )

        is DominoMatchPhase.PresentingPass ->
            OnlinePresentationLifecycleContract(
                kind = OnlinePresentationLifecycleKind.PASS,
                blocksSnapshotPromotionUntilUiCompletion = true,
                watchdogMillis = gameplayWatchdogMillis,
            )

        DominoMatchPhase.RoundSummary ->
            OnlinePresentationLifecycleContract(
                kind = OnlinePresentationLifecycleKind.ROUND_SUMMARY,
                blocksSnapshotPromotionUntilUiCompletion = true,
                watchdogMillis = maxOf(
                    gameplayWatchdogMillis,
                    DominoMatchTiming.RoundSummaryAutoAdvanceMillis + 2_000L,
                ),
            )

        DominoMatchPhase.WaitingForLocalMove,
        DominoMatchPhase.MatchFinished -> null
    }
}