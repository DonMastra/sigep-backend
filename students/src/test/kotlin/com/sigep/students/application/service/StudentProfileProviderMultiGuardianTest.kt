package com.sigep.students.application.service

import com.sigep.students.domain.model.Student
import com.sigep.students.domain.model.StudentGuardianLinkEvent
import com.sigep.students.domain.model.StudentGuardianRelationship
import com.sigep.students.domain.repository.StudentGuardianLinkEventRepository
import com.sigep.students.domain.repository.StudentGuardianRelationshipRepository
import com.sigep.students.domain.repository.StudentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals

class StudentProfileProviderMultiGuardianTest {
    private val studentRepository = mockk<StudentRepository>()
    private val eventRepository = mockk<StudentGuardianLinkEventRepository>()
    private val relationshipRepository = mockk<StudentGuardianRelationshipRepository>()
    private val identityNormalizer = mockk<StudentIdentityNormalizer>()
    private val provider = StudentProfileProviderImpl(
        studentRepository,
        eventRepository,
        relationshipRepository,
        identityNormalizer
    )

    @Test
    fun `admin tuition flow adds a secondary guardian without changing primary`() {
        val student = student()
        val primary = relationship(1L, 10L, primary = true)
        val secondary = relationship(2L, 11L, primary = false)
        every { studentRepository.findById(42L) } returns Optional.of(student)
        every {
            relationshipRepository.existsByStudentIdAndGuardianUserIdAndActiveTrueAndCanViewAcademicTrue(42L, 11L)
        } returns false
        every {
            relationshipRepository.findByStudentIdAndActiveTrueOrderByPrimaryDescIdAsc(42L)
        } returnsMany listOf(listOf(primary), listOf(primary, secondary))
        every { relationshipRepository.findByStudentIdAndGuardianUserId(42L, 11L) } returns null
        every { relationshipRepository.save(any<StudentGuardianRelationship>()) } answers { firstArg() }
        every { eventRepository.save(any<StudentGuardianLinkEvent>()) } answers { firstArg() }

        val resolution = provider.resolveStudentForTuition(
            guardianUserId = 11L,
            actorUserId = 99L,
            actorIsAdmin = true,
            existingStudentId = 42L,
            request = null
        )

        assertEquals(10L, resolution.profile.guardianId)
        assertEquals(setOf(10L, 11L), resolution.profile.guardianIds)
        verify(exactly = 0) { studentRepository.save(any()) }
    }

    private fun student() = Student(
        id = 42L,
        studentNumber = "1000",
        firstName = "Student",
        lastName = "Test",
        email = "student@example.com",
        dateOfBirth = LocalDate.of(2010, 1, 1),
        address = "Address",
        phoneNumber = "123",
        emergencyContact = "Contact",
        guardianId = 10L,
        enrollmentDate = LocalDate.of(2026, 4, 1),
        currentLevel = "BEGINNER"
    )

    private fun relationship(id: Long, guardianId: Long, primary: Boolean) = StudentGuardianRelationship(
        id = id,
        studentId = 42L,
        guardianUserId = guardianId,
        primary = primary,
        active = true,
        canViewAcademic = true
    )
}
