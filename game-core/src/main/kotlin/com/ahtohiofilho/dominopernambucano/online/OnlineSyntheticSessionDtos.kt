package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

@Serializable
data class OnlineSyntheticAccountRecoveryRequestDto(
    val accountId: String,
)
