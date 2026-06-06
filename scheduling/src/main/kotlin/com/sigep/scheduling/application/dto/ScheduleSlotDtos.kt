package com.sigep.scheduling.application.dto

import com.sigep.scheduling.domain.model.SlotDayOfWeek
import jakarta.validation.constraints.*
import java.time.LocalDateTime

data class ScheduleSlotDto(
    val id: Long,
    val classroomId: Long,
    val classroomName: String,
    val building: String?,
    val floor: String?,
    val classroomCapacity: Int,
    val dayOfWeek: SlotDayOfWeek,
    val startTime: String,
    val endTime: String,
    val active: Boolean,
    val notes: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateScheduleSlotRequest(
    @field:NotNull(message = "Classroom ID is required")
    val classroomId: Long,

    @field:NotNull(message = "Day of week is required")
    val dayOfWeek: SlotDayOfWeek,

    @field:NotBlank(message = "Start time is required")
    @field:Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Start time must be in HH:mm format")
    val startTime: String,

    @field:NotBlank(message = "End time is required")
    @field:Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "End time must be in HH:mm format")
    val endTime: String,

    val notes: String? = null
)

data class UpdateScheduleSlotRequest(
    val dayOfWeek: SlotDayOfWeek? = null,

    @field:Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Start time must be in HH:mm format")
    val startTime: String? = null,

    @field:Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "End time must be in HH:mm format")
    val endTime: String? = null,

    val notes: String? = null,
    val active: Boolean? = null
)
