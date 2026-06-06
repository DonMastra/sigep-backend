package com.sigep.scheduling.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.scheduling.application.dto.*
import com.sigep.scheduling.domain.model.ReservationStatus
import com.sigep.scheduling.domain.model.ScheduleSlot
import com.sigep.scheduling.domain.model.SlotDayOfWeek
import com.sigep.scheduling.domain.repository.ClassroomRepository
import com.sigep.scheduling.domain.repository.ReservationRepository
import com.sigep.scheduling.domain.repository.ScheduleSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime
import java.time.LocalDateTime

@Service
@Transactional
class ScheduleSlotService(
    private val slotRepository: ScheduleSlotRepository,
    private val classroomRepository: ClassroomRepository,
    private val reservationRepository: ReservationRepository
) {

    private val logger = LoggerFactory.getLogger(ScheduleSlotService::class.java)

    fun getAllSlots(page: Int, size: Int, classroomId: Long?): PageResponse<ScheduleSlotDto> {
        val pageable = PageRequest.of(page, size)
        val result = if (classroomId != null)
            slotRepository.findByClassroomIdAndActiveTrue(classroomId, pageable)
        else
            slotRepository.findByActiveTrue(pageable)
        return PageResponse(
            content = result.content.map { it.toDto() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getSlotById(id: Long): ScheduleSlotDto =
        slotRepository.findById(id)
            .map { it.toDto() }
            .orElseThrow { ResourceNotFoundException("Schedule slot not found with id: $id") }

    fun createSlot(request: CreateScheduleSlotRequest): ScheduleSlotDto {
        val classroom = classroomRepository.findById(request.classroomId)
            .orElseThrow { ResourceNotFoundException("Classroom not found with id: ${request.classroomId}") }

        if (!classroom.active) {
            throw BusinessException("Cannot create slot for an inactive classroom")
        }

        validateTimeOrder(request.startTime, request.endTime)
        checkOverlap(request.classroomId, request.dayOfWeek, request.startTime, request.endTime, excludeSlotId = null)

        val slot = ScheduleSlot(
            classroom = classroom,
            dayOfWeek = request.dayOfWeek,
            startTime = request.startTime,
            endTime = request.endTime,
            notes = request.notes
        )
        val saved = slotRepository.save(slot)
        logger.info("ScheduleSlot created: classroom={} day={} {}-{}", classroom.name, request.dayOfWeek, request.startTime, request.endTime)
        return saved.toDto()
    }

    fun updateSlot(id: Long, request: UpdateScheduleSlotRequest): ScheduleSlotDto {
        val slot = slotRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Schedule slot not found with id: $id") }

        val newDay = request.dayOfWeek ?: slot.dayOfWeek
        val newStart = request.startTime ?: slot.startTime
        val newEnd = request.endTime ?: slot.endTime

        validateTimeOrder(newStart, newEnd)
        if (request.dayOfWeek != null || request.startTime != null || request.endTime != null) {
            checkOverlap(slot.classroom.id!!, newDay, newStart, newEnd, excludeSlotId = id)
        }

        if (request.active == false && slot.active) {
            ensureNoAssignedReservations(id)
        }

        val updated = slot.copy(
            dayOfWeek = newDay,
            startTime = newStart,
            endTime = newEnd,
            notes = request.notes ?: slot.notes,
            active = request.active ?: slot.active,
            updatedAt = LocalDateTime.now()
        )
        return slotRepository.save(updated).toDto()
    }

    fun softDeleteSlot(id: Long): ScheduleSlotDto {
        val slot = slotRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Schedule slot not found with id: $id") }
        ensureNoAssignedReservations(id)
        val deactivated = slot.copy(active = false, updatedAt = LocalDateTime.now())
        return slotRepository.save(deactivated).toDto()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun validateTimeOrder(startTime: String, endTime: String) {
        val s = LocalTime.parse(startTime)
        val e = LocalTime.parse(endTime)
        if (!s.isBefore(e)) {
            throw BusinessException(
                message = "Start time must be before end time",
                code = "VALIDATION_ERROR",
                field = "startTime",
                details = "startTime=$startTime must be before endTime=$endTime"
            )
        }
    }

    private fun checkOverlap(classroomId: Long, day: SlotDayOfWeek, startTime: String, endTime: String, excludeSlotId: Long?) {
        val existingSlots = slotRepository.findByClassroomIdAndDayOfWeekAndActiveTrue(classroomId, day)
        val newStart = LocalTime.parse(startTime)
        val newEnd = LocalTime.parse(endTime)

        val conflict = existingSlots
            .filter { it.id != excludeSlotId }
            .any { existing ->
                val s = LocalTime.parse(existing.startTime)
                val e = LocalTime.parse(existing.endTime)
                newStart < e && s < newEnd
            }

        if (conflict) {
            throw ResourceConflictException(
                message = "El aula ya está ocupada en ese horario",
                field = "startTime",
                details = "Overlap detected for classroom=$classroomId day=$day $startTime-$endTime"
            )
        }
    }

    private fun ensureNoAssignedReservations(slotId: Long) {
        val hasAssignedReservations = reservationRepository.existsBySlotIdAndStatus(slotId, ReservationStatus.ASSIGNED)
        if (hasAssignedReservations) {
            throw ResourceConflictException(
                message = "No se puede desactivar un slot con reservas asignadas",
                field = "slotId",
                details = "Unassign all ASSIGNED reservations for slot id=$slotId before deactivation"
            )
        }
    }

    fun ScheduleSlot.toDto() = ScheduleSlotDto(
        id = id!!,
        classroomId = classroom.id!!,
        classroomName = classroom.name,
        building = classroom.building,
        floor = classroom.floor,
        classroomCapacity = classroom.capacity,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        active = active,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
