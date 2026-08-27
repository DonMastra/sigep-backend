package com.sigep.scheduling.infrastructure.provider

import com.sigep.common.application.service.ReservationInfo
import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.scheduling.domain.model.Reservation
import com.sigep.scheduling.domain.model.ReservationStatus
import com.sigep.scheduling.domain.model.ReservationTargetType
import com.sigep.scheduling.domain.repository.ReservationRepository
import org.springframework.stereotype.Component

/**
 * Implements the cross-module ReservationInfoProvider interface.
 * Declared in common, implemented here in scheduling.
 * Injected into courses module via Spring DI — no direct module dependency.
 */
@Component
class ReservationInfoProviderImpl(
    private val reservationRepository: ReservationRepository
) : ReservationInfoProvider {

    override fun getReservationsByCourse(courseId: Long): List<ReservationInfo> {
        return reservationRepository
            .findAllByTargetTypeAndTargetIdAndStatus(
                ReservationTargetType.COURSE,
                courseId,
                ReservationStatus.ASSIGNED
            )
            .sortedWith(
                compareBy<Reservation> { it.slot.dayOfWeek.ordinal }
                    .thenBy { it.slot.startTime }
                    .thenBy { it.slot.endTime }
                    .thenBy { it.id }
            )
            .map { reservation ->
                val slot = reservation.slot
                val classroom = slot.classroom
                ReservationInfo(
                    reservationId = reservation.id!!,
                    status = reservation.status.name,
                    slotId = slot.id!!,
                    dayOfWeek = slot.dayOfWeek.name,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    classroomId = classroom.id!!,
                    classroomName = classroom.name,
                    building = classroom.building,
                    floor = classroom.floor,
                    classroomCapacity = classroom.capacity
                )
            }
    }

    override fun hasReservationAssigned(courseId: Long): Boolean {
        return reservationRepository.existsByTargetTypeAndTargetIdAndStatus(
            ReservationTargetType.COURSE,
            courseId,
            ReservationStatus.ASSIGNED
        )
    }
}
