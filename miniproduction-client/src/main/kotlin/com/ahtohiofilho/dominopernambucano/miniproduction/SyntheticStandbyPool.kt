package com.ahtohiofilho.dominopernambucano.miniproduction

internal fun selectSyntheticStandbyProfileIndexes(
    profileIndexesInPriorityOrder: List<Int>,
    matchedProfileIndexes: Set<Int>,
    currentStandbyProfileIndexes: Set<Int>,
    targetSize: Int,
): Set<Int> {
    require(profileIndexesInPriorityOrder.isNotEmpty())
    require(
        profileIndexesInPriorityOrder.distinct().size ==
            profileIndexesInPriorityOrder.size,
    )
    require(targetSize > 0)

    val availableProfileIndexes =
        profileIndexesInPriorityOrder.filterNot { profileIndex ->
            profileIndex in matchedProfileIndexes
        }
    val resolvedTargetSize =
        targetSize.coerceAtMost(availableProfileIndexes.size)
    val retainedStandby = availableProfileIndexes
        .filter { profileIndex ->
            profileIndex in currentStandbyProfileIndexes
        }
        .take(resolvedTargetSize)
    val retainedSet = retainedStandby.toSet()
    val additions = availableProfileIndexes
        .filterNot { profileIndex -> profileIndex in retainedSet }
        .take(resolvedTargetSize - retainedStandby.size)

    return (retainedStandby + additions).toSet()
}
