package com.sigep.scheduling.application.dto

import jakarta.validation.constraints.*
import java.time.LocalDateTime

data class ClassroomDto(
    val id: Long,
    val name: String,
    val building: String?,
    val floor: String?,
    val capacity: Int,
    val active: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateClassroomRequest(
    @field:NotBlank(message = "Classroom name is required")
    @field:Size(max = 100)
    val name: String,

    @field:Size(max = 100)
    val building: String? = null,

    @field:Size(max = 20)
    val floor: String? = null,

    @field:NotNull(message = "Capacity is required")
    @field:Min(value = 1, message = "Capacity must be at least 1")
    val capacity: Int
)

data class UpdateClassroomRequest(
    @field:Size(max = 100)
    val name: String? = null,

    @field:Size(max = 100)
    val building: String? = null,

    @field:Size(max = 20)
    val floor: String? = null,

    @field:Min(value = 1)
    val capacity: Int? = null,

    val active: Boolean? = null
)
