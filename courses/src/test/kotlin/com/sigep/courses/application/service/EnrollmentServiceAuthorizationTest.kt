package com.sigep.courses.application.service

import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.model.CourseLevel
import com.sigep.courses.domain.model.CourseStatus
import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnrollmentServiceAuthorizationTest {

    private lateinit var enrollmentRepository: EnrollmentRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var studentProfileProvider: StudentProfileProvider
    private lateinit var service: EnrollmentService

    @BeforeEach
    fun setUp() {
        enrollmentRepository = mockk()
        courseRepository = mockk()
        studentProfileProvider = mockk()
        service = EnrollmentService(enrollmentRepository, courseRepository, studentProfileProvider)
    }

    @Test
    fun `teacher cannot read an enrollment from another teacher`() {
        every { enrollmentRepository.findById(7L) } returns Optional.of(enrollment(teacherId = 22L))

        assertFailsWith<ForbiddenException> {
            service.getEnrollmentById(7L, actorUserId = 11L, actorRole = "TEACHER")
        }
    }

    @Test
    fun `teacher history is restricted to courses assigned to that teacher`() {
        every {
            enrollmentRepository.findByStudentIdAndCourseTeacherId(5L, 11L, any<Pageable>())
        } returns PageImpl(listOf(enrollment(teacherId = 11L)))
        every { studentProfileProvider.getStudentProfile(5L) } returns studentProfile(guardianId = 31L)

        val result = service.getStudentEnrollmentHistory(5L, actorUserId = 11L, actorRole = "TEACHER")

        assertEquals(1, result.totalCourses)
        assertEquals(11L, result.enrollments.single().courseId)
        verify(exactly = 0) { enrollmentRepository.findByStudentId(5L, any<Pageable>()) }
    }

    @Test
    fun `guardian cannot read enrollments for a student owned by another guardian`() {
        every { studentProfileProvider.getStudentProfile(5L) } returns studentProfile(guardianId = 32L)

        assertFailsWith<ForbiddenException> {
            service.getStudentEnrollments(5L, 0, 10, actorUserId = 31L, actorRole = "GUARDIAN")
        }
    }

    private fun enrollment(teacherId: Long) = Enrollment(
        id = 7L,
        studentId = 5L,
        course = Course(
            id = teacherId,
            code = "QA-$teacherId",
            name = "Course $teacherId",
            description = "Test course",
            level = CourseLevel.BEGINNER,
            duration = 24,
            maxStudents = 12,
            teacherId = teacherId,
            price = BigDecimal("1000.00"),
            status = CourseStatus.ACTIVE,
            isPublished = true
        )
    )

    private fun studentProfile(guardianId: Long) = StudentProfileInfo(
        id = 5L,
        guardianId = guardianId,
        firstName = "Test",
        lastName = "Student",
        email = "student@example.com",
        documentType = "DNI",
        documentCountry = "AR",
        documentNumber = "12345678",
        dateOfBirth = LocalDate.of(2012, 1, 1),
        address = "Street 123",
        phoneNumber = "1111-2222",
        emergencyContact = "Guardian",
        currentLevel = "BEGINNER",
        active = true
    )
}
