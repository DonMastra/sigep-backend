package com.sigep.staff.application.service

import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ValidationException
import com.sigep.staff.application.dto.CreateAttendanceRequest
import com.sigep.staff.domain.model.AttendanceStatus
import com.sigep.staff.domain.model.NonTeachingStaff
import com.sigep.staff.domain.model.StaffCurrency
import com.sigep.staff.domain.model.StaffAttendance
import com.sigep.staff.domain.model.TeachingStaff
import com.sigep.staff.infrastructure.repository.NonTeachingStaffRepository
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
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

    @Test
    fun `requires worked hours for non teaching presence`() {
        val date = LocalDate.now().minusDays(1)
        val staff = mockk<NonTeachingStaff>()
        every { attendanceRepository.findByNonTeachingStaffIdAndAttendanceDate(9, date) } returns Optional.empty()
        every { nonTeachingStaffRepository.findById(9) } returns Optional.of(staff)
        every { staff.hireDate } returns date.minusYears(1)

        assertThrows(ValidationException::class.java) {
            service.createAttendance(
                CreateAttendanceRequest(
                    nonTeachingStaffId = 9,
                    attendanceDate = date,
                    status = AttendanceStatus.PRESENT
                )
            )
        }
    }

    @Test
    fun `requires both check in and check out times`() {
        val date = LocalDate.now().minusDays(1)
        val staff = mockk<TeachingStaff>()
        every { attendanceRepository.findByTeachingStaffIdAndAttendanceDate(7, date) } returns Optional.empty()
        every { teachingStaffRepository.findById(7) } returns Optional.of(staff)
        every { staff.hireDate } returns date.minusYears(1)

        assertThrows(ValidationException::class.java) {
            service.createAttendance(
                CreateAttendanceRequest(
                    teachingStaffId = 7,
                    attendanceDate = date,
                    checkInTime = LocalTime.of(9, 0),
                    status = AttendanceStatus.PRESENT
                )
            )
        }
    }

    @Test
    fun `requires currency before recording non teaching hours`() {
        val date = LocalDate.now().minusDays(1)
        val staff = mockk<NonTeachingStaff>()
        every { attendanceRepository.findByNonTeachingStaffIdAndAttendanceDate(9, date) } returns Optional.empty()
        every { nonTeachingStaffRepository.findById(9) } returns Optional.of(staff)
        every { staff.hireDate } returns date.minusYears(1)
        every { staff.currency } returns null

        assertThrows(ValidationException::class.java) {
            service.createAttendance(
                CreateAttendanceRequest(
                    nonTeachingStaffId = 9,
                    attendanceDate = date,
                    status = AttendanceStatus.PRESENT,
                    hoursWorked = 8.0
                )
            )
        }
    }

    @Test
    fun `builds a historical non teaching summary with the recorded rate snapshot`() {
        val staff = mockk<NonTeachingStaff>()
        every { staff.id } returns 9
        every { staff.fullName } returns "Ana Operativa"
        every { staff.hireDate } returns LocalDate.of(2026, 1, 1)
        every { staff.hourlyRate } returns 999.0
        every { staff.currency } returns StaffCurrency.ARS
        every { nonTeachingStaffRepository.findById(9) } returns Optional.of(staff)
        every {
            attendanceRepository.findAllByNonTeachingStaffIdAndAttendanceDateBetween(
                9,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
            )
        } returns listOf(
            StaffAttendance(
                id = 1,
                nonTeachingStaff = staff,
                attendanceDate = LocalDate.of(2026, 8, 3),
                status = AttendanceStatus.PRESENT,
                hoursWorked = 8.0,
                hourlyRateSnapshot = BigDecimal("100.00"),
                currencySnapshot = StaffCurrency.ARS
            ),
            StaffAttendance(
                id = 2,
                nonTeachingStaff = staff,
                attendanceDate = LocalDate.of(2026, 8, 4),
                status = AttendanceStatus.ABSENT,
                hoursWorked = 0.0,
                hourlyRateSnapshot = BigDecimal("100.00"),
                currencySnapshot = StaffCurrency.ARS
            )
        )

        val summary = service.getNonTeachingStaffMonthlySummary(9, java.time.YearMonth.of(2026, 8))

        assertEquals("2026-08", summary.period)
        assertEquals(21, summary.businessDaysInMonth)
        assertEquals(2, summary.registeredDays)
        assertEquals(1, summary.attendedDays)
        assertEquals(8.0, summary.hoursWorked)
        assertEquals(BigDecimal("800.00"), summary.estimatedAmount)
        assertEquals(StaffCurrency.ARS, summary.currency)
        assertEquals(false, summary.usesCurrentRateFallback)
    }
}
