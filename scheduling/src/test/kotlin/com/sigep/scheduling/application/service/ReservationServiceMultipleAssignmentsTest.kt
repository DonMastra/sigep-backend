package com.sigep.scheduling.application.service

import com.sigep.common.application.service.SchedulingTargetValidationProvider
import com.sigep.scheduling.application.dto.AssignReservationRequest
import com.sigep.scheduling.domain.model.Classroom
import com.sigep.scheduling.domain.model.Reservation
import com.sigep.scheduling.domain.model.ReservationStatus
import com.sigep.scheduling.domain.model.ReservationTargetType
import com.sigep.scheduling.domain.model.ScheduleSlot
import com.sigep.scheduling.domain.model.SlotDayOfWeek
import com.sigep.scheduling.domain.repository.ClassroomRepository
import com.sigep.scheduling.domain.repository.ReservationRepository
import com.sigep.scheduling.domain.repository.ScheduleSlotRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional

class ReservationServiceMultipleAssignmentsTest {
    private val reservationRepository = mockk<ReservationRepository>()
    private val slotRepository = mockk<ScheduleSlotRepository>()
    private val classroomRepository = mockk<ClassroomRepository>()
    private val targetValidator = mockk<SchedulingTargetValidationProvider>()
    private val targetValidatorProvider = mockk<ObjectProvider<SchedulingTargetValidationProvider>>()
    private val scheduleSlotService = ScheduleSlotService(slotRepository, classroomRepository, reservationRepository)
    private val service = ReservationService(
        reservationRepository,
        slotRepository,
        scheduleSlotService,
        targetValidatorProvider
    )

    @Test
    fun `assigns another reservation when the course already has one`() {
        val classroom = Classroom(id = 3, name = "Aula 3", capacity = 20)
        val slot = ScheduleSlot(
            id = 12,
            classroom = classroom,
            dayOfWeek = SlotDayOfWeek.WEDNESDAY,
            startTime = "20:30",
            endTime = "22:00"
        )
        val available = Reservation(id = 12, slot = slot)

        every { reservationRepository.findById(12) } returns Optional.of(available)
        every { targetValidatorProvider.getIfAvailable() } returns targetValidator
        every { targetValidator.courseExists(21) } returns true
        every {
            reservationRepository.existsByTargetTypeAndTargetIdAndStatus(
                ReservationTargetType.COURSE,
                21,
                ReservationStatus.ASSIGNED
            )
        } returns true
        every { reservationRepository.save(any()) } answers { firstArg() }

        val result = service.assignReservation(
            12,
            AssignReservationRequest(ReservationTargetType.COURSE, 21)
        )

        assertEquals(ReservationStatus.ASSIGNED, result.status)
        assertEquals(ReservationTargetType.COURSE, result.targetType)
        assertEquals(21, result.targetId)
    }
}
