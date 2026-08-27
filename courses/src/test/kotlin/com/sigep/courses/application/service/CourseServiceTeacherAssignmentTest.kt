package com.sigep.courses.application.service

import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.service.ReservationAssignmentProvider
import com.sigep.common.application.service.ReservationInfo
import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.courses.application.dto.CreateCourseRequest
import com.sigep.courses.application.dto.UpdateCourseRequest
import com.sigep.courses.application.event.CourseEventPublisher
import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.model.CourseLevel
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.math.BigDecimal
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CourseServiceTeacherAssignmentTest {
    private val courseRepository = mockk<CourseRepository>()
    private val enrollmentRepository = mockk<EnrollmentRepository>()
    private val eventPublisher = mockk<CourseEventPublisher>()
    private val teacherInfoProvider = mockk<TeacherInfoProvider>()
    private val reservationInfoProvider = mockk<ReservationInfoProvider>()
    private val reservationAssignmentProvider = mockk<ObjectProvider<ReservationAssignmentProvider>>()
    private val studentProfileProvider = mockk<ObjectProvider<StudentProfileProvider>>()
    private val service = CourseService(
        courseRepository,
        enrollmentRepository,
        eventPublisher,
        teacherInfoProvider,
        reservationInfoProvider,
        reservationAssignmentProvider,
        studentProfileProvider
    )

    @Test
    fun `does not expose a user name when the stored id is not an assignable teacher`() {
        val course = course(teacherId = 7)
        every { courseRepository.findById(1) } returns Optional.of(course)
        every { enrollmentRepository.countActiveEnrollmentsByCourse(1) } returns 0
        every { enrollmentRepository.countByCourseId(1) } returns 0
        every { reservationInfoProvider.getReservationsByCourse(1) } returns emptyList()
        every { teacherInfoProvider.getTeacherNameById(7) } returns null

        val result = service.getCourseById(1, actorUserId = 99, actorRole = "ADMIN")

        assertEquals(7, result.teacherId)
        assertNull(result.teacherName)
    }

    @Test
    fun `rejects a new course linked to a non teaching account`() {
        every { courseRepository.existsByCodeIgnoreCase("2026-TEST") } returns false
        every { teacherInfoProvider.getTeacherNameById(7) } returns null

        val error = assertFailsWith<BusinessException> {
            service.createCourse(
                CreateCourseRequest(
                    code = "2026-TEST",
                    name = "Curso de prueba",
                    description = "Descripcion valida del curso",
                    level = CourseLevel.BEGINNER,
                    duration = 60,
                    maxStudents = 20,
                    minStudents = 1,
                    teacherId = 7,
                    price = BigDecimal("90000")
                )
            )
        }

        assertEquals("INVALID_TEACHER_ASSIGNMENT", error.code)
        assertEquals("teacherId", error.field)
    }

    @Test
    fun `creates a course with every selected reservation`() {
        val assignmentProvider = mockk<ReservationAssignmentProvider>(relaxed = true)
        every { courseRepository.existsByCodeIgnoreCase("2026-MULTI") } returns false
        every { courseRepository.save(any()) } answers { firstArg<Course>().copy(id = 1) }
        every { reservationAssignmentProvider.getIfAvailable() } returns assignmentProvider
        every { enrollmentRepository.countActiveEnrollmentsByCourse(1) } returns 0
        every { enrollmentRepository.countByCourseId(1) } returns 0
        every { reservationInfoProvider.getReservationsByCourse(1) } returns listOf(
            reservationInfo(9, "MONDAY"),
            reservationInfo(10, "WEDNESDAY")
        )

        val result = service.createCourse(
            CreateCourseRequest(
                code = "2026-MULTI",
                name = "Curso con dos horarios",
                description = "Descripcion valida del curso",
                level = CourseLevel.ELEMENTARY,
                duration = 60,
                maxStudents = 20,
                price = BigDecimal("90000"),
                reservationIds = setOf(9, 10)
            )
        )

        verify(exactly = 1) { assignmentProvider.syncCourseReservations(1, setOf(9, 10)) }
        assertEquals(listOf(9L, 10L), result.reservationSummaries.map { it.reservationId })
    }

    @Test
    fun `updates the complete reservation selection`() {
        val assignmentProvider = mockk<ReservationAssignmentProvider>(relaxed = true)
        every { courseRepository.findById(1) } returns Optional.of(course(teacherId = 7))
        every { teacherInfoProvider.getTeacherNameById(7) } returns "Docente"
        every { courseRepository.save(any()) } answers { firstArg() }
        every { reservationAssignmentProvider.getIfAvailable() } returns assignmentProvider
        every { enrollmentRepository.countActiveEnrollmentsByCourse(1) } returns 0
        every { enrollmentRepository.countByCourseId(1) } returns 0
        every { reservationInfoProvider.getReservationsByCourse(1) } returns listOf(
            reservationInfo(9, "MONDAY"),
            reservationInfo(10, "WEDNESDAY")
        )

        service.updateCourse(
            1,
            UpdateCourseRequest(
                code = null,
                name = null,
                description = null,
                level = null,
                duration = null,
                maxStudents = null,
                minStudents = null,
                teacherId = 7,
                price = null,
                startDate = null,
                endDate = null,
                reservationIds = setOf(9, 10),
                status = null,
                isPublished = null
            )
        )

        verify(exactly = 1) { assignmentProvider.syncCourseReservations(1, setOf(9, 10)) }
    }

    private fun course(teacherId: Long) = Course(
        id = 1,
        code = "2026-TEST",
        name = "Curso de prueba",
        description = "Descripcion valida del curso",
        level = CourseLevel.BEGINNER,
        duration = 60,
        maxStudents = 20,
        minStudents = 1,
        teacherId = teacherId,
        price = BigDecimal("90000")
    )

    private fun reservationInfo(id: Long, dayOfWeek: String) = ReservationInfo(
        reservationId = id,
        status = "ASSIGNED",
        slotId = id,
        dayOfWeek = dayOfWeek,
        startTime = "20:30",
        endTime = "22:00",
        classroomId = 3,
        classroomName = "Aula 3",
        building = null,
        floor = null,
        classroomCapacity = 20
    )
}
