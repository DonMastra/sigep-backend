package com.sigep.staff.application.service

import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.staff.application.dto.CreateAttendanceRequest
import com.sigep.staff.domain.model.AttendanceStatus
import com.sigep.staff.domain.model.StaffAttendance
import com.sigep.staff.domain.model.TeachingStaff
import com.sigep.staff.infrastructure.repository.NonTeachingStaffRepository
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional

class StaffAttendanceServiceTest {
    private val attendanceRepository = mockk<StaffAttendanceRepository>()
    private val teachingStaffRepository = mockk<TeachingStaffRepository>()
    private val nonTeachingStaffRepository = mockk<NonTeachingStaffRepository>()
    private val service = StaffAttendanceService(attendanceRepository, teachingStaffRepository, nonTeachingStaffRepository)

    @Test
    fun `rejects a second teaching attendance record for the same date`() {
        val date = LocalDate.of(2026, 8, 20)
        every { attendanceRepository.findByTeachingStaffIdAndAttendanceDate(7, date) } returns
            Optional.of(mockk<StaffAttendance>())

        assertThrows(DuplicateResourceException::class.java) {
            service.createAttendance(
                CreateAttendanceRequest(
                    teachingStaffId = 7,
                    attendanceDate = date,
                    status = AttendanceStatus.PRESENT
                )
            )
        }
    }

    @Test
    fun `rejects a teacher reading another teachers attendance`() {
        val staff = mockk<TeachingStaff>()
        every { staff.linkedUserId } returns 20
        every { teachingStaffRepository.findById(7) } returns Optional.of(staff)

        assertThrows(ForbiddenException::class.java) {
            service.getTeachingStaffAttendance(
                staffId = 7,
                startDate = LocalDate.of(2026, 8, 1),
                endDate = LocalDate.of(2026, 8, 31),
                page = 0,
                limit = 31,
                actorUserId = 21,
                actorRole = "TEACHER"
            )
        }
    }
}
