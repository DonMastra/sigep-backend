package com.sigep.common.application.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class ErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
    val field: String? = null,
    val details: String? = null,
    val path: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val timestamp: LocalDateTime = LocalDateTime.now()
)

