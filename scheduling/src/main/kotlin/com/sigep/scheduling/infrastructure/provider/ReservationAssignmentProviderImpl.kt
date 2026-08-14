package com.sigep.scheduling.infrastructure.provider

import com.sigep.common.application.service.ReservationAssignmentProvider
import com.sigep.scheduling.application.dto.AssignReservationRequest
import com.sigep.scheduling.application.service.ReservationService
import com.sigep.scheduling.domain.model.ReservationTargetType
import org.springframework.stereotype.Component

@Component
class ReservationAssignmentProviderImpl(
    private val reservationService: ReservationService
) : ReservationAssignmentProvider {

    override fun assignReservationToCourse(reservationId: Long, courseId: Long) {
        reservationService.assignReservation(
            reservationId,
            AssignReservationRequest(
                targetType = ReservationTargetType.COURSE,
                targetId = courseId
            )
        )
    }
}

