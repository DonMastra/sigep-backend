package com.sigep.scheduling.application.service

import com.sigep.scheduling.application.dto.UpdateScheduleSlotRequest
import com.sigep.scheduling.domain.model.Classroom
import com.sigep.scheduling.domain.model.ScheduleSlot
import com.sigep.scheduling.domain.model.SlotDayOfWeek
import com.sigep.scheduling.domain.repository.ClassroomRepository
import com.sigep.scheduling.domain.repository.ReservationRepository
import com.sigep.scheduling.domain.repository.ScheduleSlotRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class ScheduleSlotServiceTest {
    private val slotRepository = mockk<ScheduleSlotRepository>()
    private val classroomRepository = mockk<ClassroomRepository>()
    private val reservationRepository = mockk<ReservationRepository>()
    private val service = ScheduleSlotService(slotRepository, classroomRepository, reservationRepository)

    @Test
    fun `moves an existing slot to another active classroom`() {
        val currentClassroom = Classroom(id = 1, name = "Aula 1", capacity = 12)
        val targetClassroom = Classroom(id = 2, name = "Aula 2", capacity = 20)
        val slot = ScheduleSlot(
            id = 10,
            classroom = currentClassroom,
            dayOfWeek = SlotDayOfWeek.MONDAY,
            startTime = "10:00",
            endTime = "11:30"
        )

        every { slotRepository.findById(10) } returns Optional.of(slot)
        every { classroomRepository.findById(2) } returns Optional.of(targetClassroom)
        every {
            slotRepository.findByClassroomIdAndDayOfWeekAndActiveTrue(2, SlotDayOfWeek.MONDAY)
        } returns emptyList()
        every { slotRepository.save(any()) } answers { firstArg() }

        val result = service.updateSlot(10, UpdateScheduleSlotRequest(classroomId = 2))

        assertEquals(2, result.classroomId)
        assertEquals("Aula 2", result.classroomName)
        assertEquals(20, result.classroomCapacity)
    }
}
