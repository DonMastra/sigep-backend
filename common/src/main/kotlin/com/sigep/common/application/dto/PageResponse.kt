package com.sigep.common.application.dto

/**
 * Paginated response wrapper
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

