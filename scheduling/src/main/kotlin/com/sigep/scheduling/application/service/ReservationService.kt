package com.sigep.scheduling.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ReservationAlreadyAssignedException
import com.sigep.common.application.exception.ReservationNotAvailableException
import com.sigep.common.application.service.SchedulingTargetValidationProvider
import com.sigep.scheduling.application.dto.*
import com.sigep.scheduling.domain.model.Reservation
import com.sigep.scheduling.domain.model.ReservationStatus
import com.sigep.scheduling.domain.model.ReservationTargetType
import com.sigep.scheduling.domain.model.SlotDayOfWeek
import com.sigep.scheduling.domain.repository.ReservationRepository
import com.sigep.scheduling.domain.repository.ScheduleSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val slotRepository: ScheduleSlotRepository,
    private val scheduleSlotService: ScheduleSlotService,
    private val schedulingTargetValidationProviderProvider: ObjectProvider<SchedulingTargetValidationProvider>
) {

    private val logger = LoggerFactory.getLogger(ReservationService::class.java)

    fun getReservations(
        page: Int,
        size: Int,
        status: ReservationStatus?,
        targetType: ReservationTargetType?,
        classroomId: Long?,
        dayOfWeek: SlotDayOfWeek?,
        startTimeFrom: String?,
        endTimeTo: String?,
        actorUserId: Long?,
        actorRole: String?
    ): PageResponse<ReservationDto> {
        val pageable = PageRequest.of(page, size)
        val result = if (actorRole == "TEACHER") {
            val teacherId = requireActorUserId(actorUserId)
            val validator = targetValidator()
            val courseIds = validator.getCourseIdsAssignedToTeacher(teacherId).ifEmpty { setOf(-1L) }
            val sessionIds = validator.getSessionIdsAssignedToTeacher(teacherId).ifEmpty { setOf(-1L) }
            reservationRepository.findByFiltersForTeacher(
                status = status,
                targetType = targetType,
                classroomId = classroomId,
                dayOfWeek = dayOfWeek,
                startTimeFrom = startTimeFrom,
                endTimeTo = endTimeTo,
                courseTargetType = ReservationTargetType.COURSE,
                sessionTargetType = ReservationTargetType.SESSION,
                courseIds = courseIds,
                sessionIds = sessionIds,
                pageable = pageable
            )
        } else {
            reservationRepository.findByFilters(
                status = status,
                targetType = targetType,
                classroomId = classroomId,
                dayOfWeek = dayOfWeek,
                startTimeFrom = startTimeFrom,
                endTimeTo = endTimeTo,
                pageable = pageable
            )
        }
        return PageResponse(
            content = result.content.map { it.toDto() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun getAvailableReservations(
        page: Int,
        size: Int,
        classroomId: Long?,
        dayOfWeek: SlotDayOfWeek?,
        startTimeFrom: String?,
        endTimeTo: String?,
        actorUserId: Long?,
        actorRole: String?
    ): PageResponse<ReservationDto> = getReservations(
        page = page,
        size = size,
        status = ReservationStatus.AVAILABLE,
        targetType = null,
        classroomId = classroomId,
        dayOfWeek = dayOfWeek,
        startTimeFrom = startTimeFrom,
        endTimeTo = endTimeTo,
        actorUserId = actorUserId,
        actorRole = actorRole
    )

    fun getReservationById(id: Long, actorUserId: Long?, actorRole: String?): ReservationDto {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found with id: $id") }
        if (actorRole == "TEACHER") {
            val teacherId = requireActorUserId(actorUserId)
            val validator = targetValidator()
            val allowed = when (reservation.targetType) {
                ReservationTargetType.COURSE -> reservation.targetId in validator.getCourseIdsAssignedToTeacher(teacherId)
                ReservationTargetType.SESSION -> reservation.targetId in validator.getSessionIdsAssignedToTeacher(teacherId)
                ReservationTargetType.NONE -> false
            }
            if (!allowed) {
                throw ForbiddenException("Teachers can only access reservations assigned to their courses")
            }
        }
        return reservation.toDto()
    }

    fun createReservation(request: CreateReservationRequest): ReservationDto {
        val slot = slotRepository.findById(request.slotId)
            .orElseThrow { ResourceNotFoundException("Schedule slot not found with id: ${request.slotId}") }

        if (!slot.active) {
            throw ReservationNotAvailableException(
                message = "Cannot create reservation on an inactive slot",
                field = "slotId",
                details = "Slot id=${slot.id} is inactive"
            )
        }

        val existing = reservationRepository.findBySlotIdAndStatusNot(slot.id!!, ReservationStatus.INACTIVE)
        if (existing.isNotEmpty()) {
            throw ReservationNotAvailableException(
                message = "This slot already has an active reservation",
                field = "slotId",
                details = "Slot id=${slot.id} already has a reservation with status=${existing.first().status}"
            )
        }

        val reservation = Reservation(slot = slot, status = ReservationStatus.AVAILABLE)
        val saved = reservationRepository.save(reservation)
        logger.info("Reservation created for slot id={}", slot.id)
        return saved.toDto()
    }

    fun assignReservation(id: Long, request: AssignReservationRequest): ReservationDto {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found with id: $id") }

        if (request.targetType == ReservationTargetType.NONE) {
            throw BusinessException(
                message = "targetType NONE is not allowed on assign; use /unassign instead",
                code = "VALIDATION_ERROR",
                field = "targetType",
                details = "Allowed values for assign are COURSE or SESSION"
            )
        }

        if (!reservation.slot.active) {
            throw ReservationNotAvailableException(
                message = "Cannot assign reservation on an inactive slot",
                field = "slotId",
                details = "Slot id=${reservation.slot.id} is inactive"
            )
        }

        validateAssignableTarget(request.targetType, request.targetId)

        if (reservation.status != ReservationStatus.AVAILABLE) {
            throw ReservationAlreadyAssignedException(
                message = "La reserva ya está asignada y debe desasignarse primero",
                field = "status",
                details = "Reservation id=$id has status=${reservation.status}. Call unassign first."
            )
        }

        val updated = reservation.copy(
            targetType = request.targetType,
            targetId = request.targetId,
            status = ReservationStatus.ASSIGNED,
            updatedAt = LocalDateTime.now()
        )
        val saved = reservationRepository.save(updated)
        logger.info("Reservation id={} assigned to {}={}", id, request.targetType, request.targetId)
        return saved.toDto()
    }

    fun syncCourseReservations(courseId: Long, reservationIds: Set<Long>) {
        validateAssignableTarget(ReservationTargetType.COURSE, courseId)

        val currentReservations = reservationRepository.findAllByTargetTypeAndTargetIdAndStatus(
            ReservationTargetType.COURSE,
            courseId,
            ReservationStatus.ASSIGNED
        )
        val currentIds = currentReservations.mapNotNull { it.id }.toSet()
        val idsToAdd = reservationIds - currentIds
        val reservationsToRemove = currentReservations.filter { it.id !in reservationIds }

        if (reservationsToRemove.isNotEmpty()) {
            ensureCourseNotOperationalForUnassign(courseId)
        }

        idsToAdd.forEach { reservationId ->
            assignReservation(
                reservationId,
                AssignReservationRequest(
                    targetType = ReservationTargetType.COURSE,
                    targetId = courseId
                )
            )
        }
        reservationsToRemove.forEach { reservation ->
            unassignReservation(reservation.id!!)
        }
    }

    fun unassignReservation(id: Long): ReservationDto {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found with id: $id") }

        if (reservation.status != ReservationStatus.ASSIGNED) {
            throw ReservationNotAvailableException(
                message = "Reservation is not currently assigned",
                field = "status",
                details = "Reservation id=$id has status=${reservation.status}"
            )
        }

        if (reservation.targetType == ReservationTargetType.COURSE && reservation.targetId != null) {
            ensureCourseNotOperationalForUnassign(reservation.targetId)
        }

        val updated = reservation.copy(
            targetType = ReservationTargetType.NONE,
            targetId = null,
            status = ReservationStatus.AVAILABLE,
            updatedAt = LocalDateTime.now()
        )
        val saved = reservationRepository.save(updated)
        logger.info("Reservation id={} unassigned, now AVAILABLE", id)
        return saved.toDto()
    }

    fun inactivateReservation(id: Long): ReservationDto {
        val reservation = reservationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Reservation not found with id: $id") }

        if (reservation.status == ReservationStatus.ASSIGNED) {
            throw ResourceConflictException(
                message = "No se puede inactivar una reserva asignada",
                field = "status",
                details = "Unassign reservation id=$id before inactivation"
            )
        }

        val updated = reservation.copy(
            status = ReservationStatus.INACTIVE,
            updatedAt = LocalDateTime.now()
        )
        return reservationRepository.save(updated).toDto()
    }

    private fun validateAssignableTarget(targetType: ReservationTargetType, targetId: Long) {
        val validator = targetValidator()

        when (targetType) {
            ReservationTargetType.COURSE -> {
                if (!validator.courseExists(targetId)) {
                    throw ResourceNotFoundException("Course not found with id: $targetId")
                }
            }

            ReservationTargetType.SESSION -> {
                if (!validator.sessionExists(targetId)) {
                    throw ResourceNotFoundException("Session not found with id: $targetId")
                }
            }

            ReservationTargetType.NONE -> {
                // Guarded earlier in assignReservation
            }
        }
    }

    private fun targetValidator(): SchedulingTargetValidationProvider =
        schedulingTargetValidationProviderProvider.getIfAvailable()
            ?: throw BusinessException(
                message = "Target validation provider not available",
                code = "INTEGRATION_PROVIDER_NOT_AVAILABLE",
                field = "targetType",
                details = "Courses module provider is required to validate COURSE/SESSION assignments"
            )

    private fun requireActorUserId(actorUserId: Long?): Long =
        actorUserId ?: throw ForbiddenException("Authenticated user id is required")

    private fun ensureCourseNotOperationalForUnassign(courseId: Long) {
        val validator = schedulingTargetValidationProviderProvider.getIfAvailable()
            ?: throw BusinessException(
                message = "Course operational state provider not available",
                code = "INTEGRATION_PROVIDER_NOT_AVAILABLE",
                field = "targetId",
                details = "Courses module provider is required to validate unassign restrictions"
            )
        if (validator.isCourseOperational(courseId)) {
            throw ResourceConflictException(
                message = "No se puede desasignar la reserva de un curso operativo",
                field = "targetId",
                details = "Unpublish and deactivate course id=$courseId before unassigning"
            )
        }
    }

    private fun Reservation.toDto() = ReservationDto(
        id = id!!,
        slot = with(scheduleSlotService) { slot.toDto() },
        targetType = targetType,
        targetId = targetId,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
