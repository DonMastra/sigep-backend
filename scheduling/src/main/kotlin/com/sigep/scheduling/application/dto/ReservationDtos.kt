package com.sigep.scheduling.application.dto

import com.sigep.scheduling.domain.model.ReservationStatus
import com.sigep.scheduling.domain.model.ReservationTargetType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class ReservationDto(
    val id: Long,
    val slot: ScheduleSlotDto,
    val targetType: ReservationTargetType,
    val targetId: Long?,
    val status: ReservationStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateReservationRequest(
    @field:NotNull(message = "Slot ID is required")
    @field:Min(value = 1, message = "Slot ID must be greater than 0")
    val slotId: Long
)

data class AssignReservationRequest(
    @field:NotNull(message = "Target type is required")
    val targetType: ReservationTargetType,

    @field:NotNull(message = "Target ID is required")
    @field:Min(value = 1, message = "Target ID must be greater than 0")
    val targetId: Long
)
