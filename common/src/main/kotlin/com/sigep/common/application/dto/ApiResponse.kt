package com.sigep.common.application.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun <T> success(data: T, message: String? = null): ApiResponse<T> {
            return ApiResponse(success = true, data = data, message = message)
        }

        fun successNoContent(message: String? = null): ApiResponse<Unit> {
            return ApiResponse(success = true, data = Unit, message = message)
        }

        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(success = false, message = message)
        }
    }
}
