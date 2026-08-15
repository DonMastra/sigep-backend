package com.sigep.courses.application.service

import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.model.CourseLevel
import com.sigep.courses.domain.model.CourseStatus
import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.math.BigDecimal
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals

class CourseEnrollmentCommandProviderImplTest {

    private val courseRepository = mockk<CourseRepository>()
    private val enrollmentRepository = mockk<EnrollmentRepository>()
    private val reservationProvider = mockk<ObjectProvider<ReservationInfoProvider>>(relaxed = true)
    private val service = CourseEnrollmentCommandProviderImpl(
        courseRepository,
        enrollmentRepository,
        reservationProvider
    )

    @Test
    fun `student can hold active enrollments in two different courses`() {
        val studentId = 77L
        val firstCourse = course(10L, "Adults Starter")
        val secondCourse = course(11L, "Conversation")
        val ids = AtomicLong(100L)

        every { courseRepository.findById(10L) } returns Optional.of(firstCourse)
        every { courseRepository.findById(11L) } returns Optional.of(secondCourse)
        every { enrollmentRepository.findByStudentIdAndCourseId(studentId, 10L) } returns Optional.empty()
        every { enrollmentRepository.findByStudentIdAndCourseId(studentId, 11L) } returns Optional.empty()
        every { enrollmentRepository.countActiveEnrollmentsByCourse(any()) } returns 0L
        every { enrollmentRepository.save(any()) } answers {
            firstArg<Enrollment>().copy(id = ids.incrementAndGet())
        }

        val first = service.createActiveEnrollment(studentId, 10L, null)
        val second = service.createActiveEnrollment(studentId, 11L, null)

        assertEquals(setOf(10L, 11L), setOf(first.courseId, second.courseId))
        verify(exactly = 1) { enrollmentRepository.findByStudentIdAndCourseId(studentId, 10L) }
        verify(exactly = 1) { enrollmentRepository.findByStudentIdAndCourseId(studentId, 11L) }
        verify(exactly = 2) { enrollmentRepository.save(match { it.studentId == studentId }) }
    }

    private fun course(id: Long, name: String) = Course(
        id = id,
        code = "COURSE-$id",
        name = name,
        description = "Test course",
        level = CourseLevel.BEGINNER,
        duration = 60,
        maxStudents = 20,
        price = BigDecimal("90000.00"),
        status = CourseStatus.ACTIVE,
        isPublished = true
    )
}
