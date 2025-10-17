package com.sigep.common.application.dto

/**
 * Paginated response wrapper
 */
data class PageResponse<T>(
    val items: List<T>,
    val pagination: PaginationInfo
)

data class PaginationInfo(
    val page: Int,
    val limit: Int,
    val total: Long,
    val totalPages: Int
)

