package com.ahtohiofilho.dominopernambucano.online

import kotlinx.serialization.Serializable

@Serializable
data class OnlineAccountProfileUpdateRequestDto(
    val publicDisplayName: String,
    val tableName: String? = null,
)

@Serializable
data class OnlineAccountProfileResponseDto(
    val publicDisplayName: String,
    val tableName: String,
    val updatedAtEpochMillis: Long,
)
