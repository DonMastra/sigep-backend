package com.sigep.students.application.service

import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.common.application.service.StudentTuitionBenefitInfo
import com.sigep.common.application.service.StudentTuitionBenefitProvider
import com.sigep.security.domain.repository.UserRepository
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.repository.StudentGuardianLinkEventRepository
import com.sigep.students.domain.repository.StudentGuardianRelationshipRepository
import com.sigep.students.domain.repository.StudentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class StudentServiceTuitionBenefitsTest {
    private val studentRepository = mockk<StudentRepository>()
    private val enrollmentServiceProvider = mockk<EnrollmentServiceProvider>()
    private val userRepository = mockk<UserRepository>()
    private val guardianLinkEventRepository = mockk<StudentGuardianLinkEventRepository>()
    private val guardianRelationshipRepository = mockk<StudentGuardianRelationshipRepository>()
    private val identityNormalizer = mockk<StudentIdentityNormalizer>()
    private val tuitionBenefitProvider = mockk<StudentTuitionBenefitProvider>()

    @Test
    fun `student list includes assigned tuition benefits from one batch lookup`() {
        val student = student()
        every { studentRepository.findAll(any<org.springframework.data.domain.Pageable>()) } returns PageImpl(listOf(student))
        every { guardianRelationshipRepository.findByStudentIdInAndActiveTrue(listOf(42L)) } returns emptyList()
        every { enrollmentServiceProvider.getEnrollmentsByStudentAndStatus(42L, "ACTIVE") } returns emptyList()
        every { tuitionBenefitProvider.getBenefitsByStudentIds(listOf(42L)) } returns mapOf(
            42L to listOf(
                StudentTuitionBenefitInfo(
                    id = 7L,
                    studentId = 42L,
                    type = "SCHOLARSHIP",
                    percentage = BigDecimal("50.00"),
                    amount = BigDecimal.ZERO,
                    validFrom = LocalDate.of(2026, 3, 1),
                    validTo = null,
                    reason = "Beca institucional",
                    active = true
                )
            )
        )
        val service = StudentService(
            studentRepository,
            enrollmentServiceProvider,
            userRepository,
            guardianLinkEventRepository,
            guardianRelationshipRepository,
            identityNormalizer,
            emptyList(),
            listOf(tuitionBenefitProvider)
        )

        val result = service.getAllStudents(0, 10, "id", "ASC")

        assertEquals("SCHOLARSHIP", result.content.single().tuitionBenefits.single().type)
        assertEquals(BigDecimal("50.00"), result.content.single().tuitionBenefits.single().percentage)
    }

    private fun student() = Student(
        id = 42L,
        studentNumber = "1000",
        firstName = "Juana",
        lastName = "Perez",
        email = "juana@example.com",
        dateOfBirth = LocalDate.of(2010, 1, 1),
        address = "Address",
        phoneNumber = "123",
        emergencyContact = "Contact",
        enrollmentDate = LocalDate.of(2026, 3, 1),
        currentLevel = "BEGINNER"
    )
}
