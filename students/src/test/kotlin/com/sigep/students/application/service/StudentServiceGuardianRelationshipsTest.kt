package com.sigep.students.application.service

import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.UserRoleMembershipProvider
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.model.StudentGuardianLinkEvent
import com.sigep.students.domain.model.StudentGuardianRelationship
import com.sigep.students.domain.repository.StudentGuardianLinkEventRepository
import com.sigep.students.domain.repository.StudentGuardianRelationshipRepository
import com.sigep.students.domain.repository.StudentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StudentServiceGuardianRelationshipsTest {
    private val studentRepository = mockk<StudentRepository>()
    private val enrollmentServiceProvider = mockk<EnrollmentServiceProvider>()
    private val userRepository = mockk<UserRepository>()
    private val guardianLinkEventRepository = mockk<StudentGuardianLinkEventRepository>(relaxed = true)
    private val guardianRelationshipRepository = mockk<StudentGuardianRelationshipRepository>()
    private val identityNormalizer = mockk<StudentIdentityNormalizer>()
    private val roleMembershipProvider = mockk<UserRoleMembershipProvider>()
    private lateinit var service: StudentService

    @BeforeEach
    fun setUp() {
        service = StudentService(
            studentRepository,
            enrollmentServiceProvider,
            userRepository,
            guardianLinkEventRepository,
            guardianRelationshipRepository,
            identityNormalizer
        )
    }

    @Test
    fun `secondary academic guardian can access the student`() {
        every { studentRepository.findById(42L) } returns Optional.of(student(guardianId = 10L))
        every {
            guardianRelationshipRepository
                .existsByStudentIdAndGuardianUserIdAndActiveTrueAndCanViewAcademicTrue(42L, 11L)
        } returns true

        service.assertCanAccessStudent(42L, 11L, UserRole.GUARDIAN.name)
    }

    @Test
    fun `replacing two guardians without primary keeps compatibility pointer empty`() {
        val student = student(guardianId = null)
        val relationships = listOf(
            relationship(1L, 10L),
            relationship(2L, 11L)
        )
        every { studentRepository.findById(42L) } returns Optional.of(student)
        every { userRepository.findById(10L) } returns Optional.of(guardian(10L, "Ana"))
        every { userRepository.findById(11L) } returns Optional.of(guardian(11L, "Beto"))
        every { guardianRelationshipRepository.findByStudentId(42L) } returns relationships
        every { guardianRelationshipRepository.saveAll(any<List<StudentGuardianRelationship>>()) } answers { firstArg() }
        every { guardianRelationshipRepository.flush() } returns Unit
        every { guardianRelationshipRepository.findByStudentIdInAndActiveTrue(listOf(42L)) } returns relationships
        every { userRepository.findAllById(any()) } returns listOf(guardian(10L, "Ana"), guardian(11L, "Beto"))
        every { enrollmentServiceProvider.getEnrollmentsByStudentAndStatus(42L, "ACTIVE") } returns emptyList()

        val result = service.replaceGuardians(
            studentId = 42L,
            guardianIds = linkedSetOf(10L, 11L),
            primaryGuardianId = null,
            actorUserId = 99L,
            reason = "Administrative review"
        )

        assertEquals(null, result.guardianId)
        assertEquals(listOf(10L, 11L), result.guardianIds)
        verify(exactly = 0) { studentRepository.save(any()) }
    }

    @Test
    fun `pending inactive guardian is assignable through multirole membership`() {
        val student = student(guardianId = null)
        val guardian = guardian(10L, "Ana", role = UserRole.ADMIN)
        val activeRelationship = relationship(1L, 10L, primary = true)
        service = serviceWithRoleMembership()
        every { studentRepository.findById(42L) } returns Optional.of(student)
        every { userRepository.findById(10L) } returns Optional.of(guardian)
        every { roleMembershipProvider.hasActiveRole(10L, UserRole.GUARDIAN.name) } returns true
        every { guardianRelationshipRepository.findByStudentId(42L) } returns emptyList()
        every { guardianRelationshipRepository.saveAll(any<List<StudentGuardianRelationship>>()) } answers { firstArg() }
        every { guardianRelationshipRepository.flush() } returns Unit
        every { guardianLinkEventRepository.save(any<StudentGuardianLinkEvent>()) } answers { firstArg() }
        every { studentRepository.save(any()) } answers { firstArg() }
        every {
            guardianRelationshipRepository.findByStudentIdInAndActiveTrue(listOf(42L))
        } returns listOf(activeRelationship)
        every { userRepository.findAllById(any()) } returns listOf(guardian)
        every { enrollmentServiceProvider.getEnrollmentsByStudentAndStatus(42L, "ACTIVE") } returns emptyList()

        val result = service.replaceGuardians(
            studentId = 42L,
            guardianIds = setOf(10L),
            primaryGuardianId = 10L,
            actorUserId = 99L,
            reason = "Administrative review"
        )

        assertEquals(10L, result.guardianId)
        assertEquals(listOf(10L), result.guardianIds)
    }

    @Test
    fun `rejected guardian is not assignable even with multirole membership`() {
        service = serviceWithRoleMembership()
        every { studentRepository.findById(42L) } returns Optional.of(student(guardianId = null))
        every {
            userRepository.findById(10L)
        } returns Optional.of(guardian(10L, "Ana", role = UserRole.ADMIN, status = AccountStatus.REJECTED))
        every { roleMembershipProvider.hasActiveRole(10L, UserRole.GUARDIAN.name) } returns true

        val exception = assertFailsWith<ValidationException> {
            service.replaceGuardians(
                studentId = 42L,
                guardianIds = setOf(10L),
                primaryGuardianId = 10L,
                actorUserId = 99L,
                reason = "Administrative review"
            )
        }

        assertEquals("GUARDIAN_NOT_ASSIGNABLE", exception.code)
        verify(exactly = 0) { guardianRelationshipRepository.findByStudentId(any()) }
    }

    private fun serviceWithRoleMembership() = StudentService(
        studentRepository,
        enrollmentServiceProvider,
        userRepository,
        guardianLinkEventRepository,
        guardianRelationshipRepository,
        identityNormalizer,
        listOf(roleMembershipProvider)
    )

    private fun student(guardianId: Long?) = Student(
        id = 42L,
        studentNumber = "1000",
        firstName = "Student",
        lastName = "Test",
        email = "student@example.com",
        dateOfBirth = LocalDate.of(2010, 1, 1),
        address = "Address",
        phoneNumber = "123",
        emergencyContact = "Contact",
        guardianId = guardianId,
        enrollmentDate = LocalDate.of(2026, 4, 1),
        currentLevel = "BEGINNER"
    )

    private fun relationship(id: Long, guardianId: Long, primary: Boolean = false) = StudentGuardianRelationship(
        id = id,
        studentId = 42L,
        guardianUserId = guardianId,
        primary = primary,
        active = true,
        canViewAcademic = true
    )

    private fun guardian(
        id: Long,
        name: String,
        role: UserRole = UserRole.GUARDIAN,
        status: AccountStatus = AccountStatus.PENDING_APPROVAL
    ) = User(
        id = id,
        username = "guardian$id",
        email = "guardian$id@example.com",
        password = "hash",
        firstName = name,
        lastName = "Guardian",
        role = role,
        status = status,
        active = false
    )
}
