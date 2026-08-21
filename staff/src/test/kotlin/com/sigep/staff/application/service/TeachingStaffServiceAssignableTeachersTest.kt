package com.sigep.staff.application.service

import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import com.sigep.staff.domain.model.PaymentStatus
import com.sigep.staff.domain.model.TeachingStaff
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate
import kotlin.test.assertEquals

class TeachingStaffServiceAssignableTeachersTest {
    private val teachingStaffRepository = mockk<TeachingStaffRepository>()
    private val attendanceRepository = mockk<StaffAttendanceRepository>()
    private val courseRepository = mockk<CourseRepository>()
    private val enrollmentRepository = mockk<EnrollmentRepository>()
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val service = TeachingStaffService(
        teachingStaffRepository,
        attendanceRepository,
        courseRepository,
        enrollmentRepository,
        userRepository,
        passwordEncoder
    )

    @Test
    fun `lists only active staff linked to eligible active teacher or admin accounts`() {
        val teacherStaff = staff(11, 101, "Agustin", "Rosado")
        val adminStaff = staff(12, 102, "Andres", "Mastracchio")
        val inactiveAccountStaff = staff(13, 103, "Docente", "Inactivo")
        val unlinkedStaff = staff(14, null, "Sin", "Cuenta")

        every { teachingStaffRepository.findAllByIsActiveTrueOrderByLastNameAscFirstNameAsc() } returns
            listOf(adminStaff, inactiveAccountStaff, teacherStaff, unlinkedStaff)
        every { userRepository.findAllById(listOf(102, 103, 101)) } returns listOf(
            user(102, "amastracchio", UserRole.ADMIN),
            user(103, "inactive", UserRole.TEACHER, active = false),
            user(101, "arosado", UserRole.TEACHER)
        )

        val result = service.getAssignableTeachers()

        assertEquals(listOf("amastracchio", "arosado"), result.map { it.username })
        assertEquals(listOf(102L, 101L), result.map { it.id })
        assertEquals(listOf(12L, 11L), result.map { it.staffId })
    }

    private fun staff(id: Long, linkedUserId: Long?, firstName: String, lastName: String) = TeachingStaff(
        id = id,
        linkedUserId = linkedUserId,
        firstName = firstName,
        lastName = lastName,
        email = "$id@example.com",
        phoneNumber = "0000000000",
        documentNumber = "DOC-$id",
        birthDate = LocalDate.of(1990, 1, 1),
        address = "Test",
        hireDate = LocalDate.of(2026, 4, 1),
        monthlySalary = 0.0,
        paymentStatus = PaymentStatus.UP_TO_DATE,
        emergencyContactName = "",
        emergencyContactPhone = ""
    )

    private fun user(id: Long, username: String, role: UserRole, active: Boolean = true) = User(
        id = id,
        username = username,
        email = "$username@example.com",
        password = "hash",
        firstName = username,
        lastName = "Test",
        role = role,
        status = AccountStatus.ACTIVE,
        active = active
    )
}
