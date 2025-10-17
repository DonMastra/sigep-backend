package com.sigep.common.domain

import java.time.LocalDateTime

/**
 * Base class for all domain entities
 */
abstract class BaseEntity {
    abstract val id: Long?
    abstract val createdAt: LocalDateTime
    abstract val updatedAt: LocalDateTime
}

