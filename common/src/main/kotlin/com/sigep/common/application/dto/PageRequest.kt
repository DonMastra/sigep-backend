package com.sigep.common.application.dto

/**
 * Pagination request parameters
 */
data class PageRequest(
    val page: Int = 0,
    val limit: Int = 10,
    val sort: String? = null,
    val order: SortOrder = SortOrder.ASC
)

enum class SortOrder {
    ASC, DESC
}

