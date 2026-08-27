package com.sigep.students.application.service

import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import com.sigep.students.domain.model.Student
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

class StudentServiceGuardianRelationshipsTest {
    private val studentRepository = mockk<StudentRepository>()
    private val enrollmentServiceProvider = mockk<EnrollmentServiceProvider>()
    private val userRepository = mockk<UserRepository>()
    private val guardianLinkEventRepository = mockk<StudentGuardianLinkEventRepository>(relaxed = true)
    private val guardianRelationshipRepository = mockk<StudentGuardianRelationshipRepository>()
    private val identityNormalizer = mockk<StudentIdentityNormalizer>()
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

    private fun relationship(id: Long, guardianId: Long) = StudentGuardianRelationship(
        id = id,
        studentId = 42L,
        guardianUserId = guardianId,
        primary = false,
        active = true,
        canViewAcademic = true
    )

    private fun guardian(id: Long, name: String) = User(
        id = id,
        username = "guardian$id",
        email = "guardian$id@example.com",
        password = "hash",
        firstName = name,
        lastName = "Guardian",
        role = UserRole.GUARDIAN,
        status = AccountStatus.PENDING_APPROVAL,
        active = false
    )
}
