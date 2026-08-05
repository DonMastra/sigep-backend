package com.sigep.exams.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.service.CourseAccessInfo
import com.sigep.common.application.service.CourseAccessProvider
import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.exams.domain.model.Exam
import com.sigep.exams.domain.repository.ExamRepository
import com.sigep.exams.domain.repository.ExamSubmissionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class ExamServiceAuthorizationTest {
    private val examRepository = mockk<ExamRepository>()
    private val submissionRepository = mockk<ExamSubmissionRepository>()
    private val teacherInfoProvider = mockk<TeacherInfoProvider>()
    private val courseAccessProvider = mockk<CourseAccessProvider>()
    private lateinit var service: ExamService

    @BeforeEach
    fun setUp() {
        service = ExamService(
            examRepository = examRepository,
            submissionRepository = submissionRepository,
            objectMapper = ObjectMapper(),
            teacherInfoProvider = teacherInfoProvider,
            courseAccessProvider = courseAccessProvider
        )
    }

    @Test
    fun `permite al docente asignado obtener el examen`() {
        val exam = exam()
        every { examRepository.findById(exam.id) } returns Optional.of(exam)
        every { courseAccessProvider.isTeacherAssignedToCourse(exam.courseId, 20) } returns true
        every { courseAccessProvider.getCourseInfo(exam.courseId) } returns courseInfo(exam.courseId, 20)

        val result = service.getExamById(exam.id, actorUserId = 20, actorRole = "TEACHER")

        assertEquals(exam.id, result.id)
    }

    @Test
    fun `rechaza al docente no asignado aunque conozca el id`() {
        val exam = exam()
        every { examRepository.findById(exam.id) } returns Optional.of(exam)
        every { courseAccessProvider.isTeacherAssignedToCourse(exam.courseId, 21) } returns false

        assertThrows(ForbiddenException::class.java) {
            service.getExamById(exam.id, actorUserId = 21, actorRole = "TEACHER")
        }
    }

    @Test
    fun `permite al administrador obtener examenes de cualquier curso`() {
        val exam = exam()
        every { examRepository.findById(exam.id) } returns Optional.of(exam)
        every { courseAccessProvider.getCourseInfo(exam.courseId) } returns courseInfo(exam.courseId, 20)

        val result = service.getExamById(exam.id, actorUserId = 1, actorRole = "ADMIN")

        assertEquals(exam.courseId, result.courseId)
    }

    private fun exam() = Exam(
        id = UUID.randomUUID(),
        courseId = 7,
        title = "Final",
        createdBy = 1
    )

    private fun courseInfo(courseId: Long, teacherUserId: Long) = CourseAccessInfo(
        id = courseId,
        code = "ENG-7",
        name = "Ingles 7",
        teacherUserId = teacherUserId
    )
}
