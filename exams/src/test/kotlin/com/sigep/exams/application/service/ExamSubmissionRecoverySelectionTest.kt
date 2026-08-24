package com.sigep.exams.application.service

import com.sigep.common.application.service.CourseAccessProvider
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.exams.domain.model.Exam
import com.sigep.exams.domain.model.ExamStatus
import com.sigep.exams.domain.model.ExamSubmission
import com.sigep.exams.domain.model.RecoverySkill
import com.sigep.exams.domain.model.SubmissionStatus
import com.sigep.exams.domain.repository.ExamGradeHistoryRepository
import com.sigep.exams.domain.repository.ExamRepository
import com.sigep.exams.domain.repository.ExamSubmissionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class ExamSubmissionRecoverySelectionTest {

    @Test
    fun `sincroniza solo desaprobados y conserva bloqueadas las categorias aprobadas`() {
        val sourceExamId = UUID.randomUUID()
        val recoveryExam = Exam(
            courseId = 7,
            sourceExamId = sourceExamId,
            title = "Recuperatorio final",
            createdBy = 1
        )
        val passed = sourceSubmission(sourceExamId, 10, 70, 70, 70, 70, BigDecimal("70"))
        val failedWriting = sourceSubmission(sourceExamId, 11, 65, 50, 80, 90, BigDecimal("71"))
        val legacyWithoutCategories = ExamSubmission(
            examId = sourceExamId,
            studentId = 12,
            status = SubmissionStatus.GRADED,
            score = BigDecimal("50"),
            createdBy = 1
        )
        val submissionRepository = mockk<ExamSubmissionRepository>()
        val examRepository = mockk<ExamRepository>()
        val gradeHistoryRepository = mockk<ExamGradeHistoryRepository>()
        val examService = mockk<ExamService>()
        val teacherInfoProvider = mockk<TeacherInfoProvider>()
        val studentProfileProvider = mockk<StudentProfileProvider>()
        val courseAccessProvider = mockk<CourseAccessProvider>()

        every { examRepository.findById(recoveryExam.id) } returns Optional.of(recoveryExam)
        every { examRepository.findById(sourceExamId) } returns Optional.of(
            Exam(id = sourceExamId, courseId = 7, title = "Final", status = ExamStatus.CLOSED, createdBy = 1)
        )
        every { submissionRepository.findAllByExamIdOrderByStudentIdAscAttemptNumberAsc(sourceExamId) } returns
            listOf(passed, failedWriting, legacyWithoutCategories)
        every { submissionRepository.findAllByExamIdOrderByStudentIdAscAttemptNumberAsc(recoveryExam.id) } returns emptyList()
        every { submissionRepository.saveAllAndFlush(any<List<ExamSubmission>>()) } answers { firstArg() }
        every { studentProfileProvider.getStudentProfiles(emptyList()) } returns emptyMap()
        every { courseAccessProvider.getCourseInfo(7) } returns null

        val service = ExamSubmissionService(
            submissionRepository,
            examRepository,
            gradeHistoryRepository,
            examService,
            teacherInfoProvider,
            studentProfileProvider,
            courseAccessProvider
        )

        service.getGradebook(recoveryExam.id, actorUserId = 1, actorRole = "ADMIN")

        verify(exactly = 1) {
            submissionRepository.saveAllAndFlush(match<List<ExamSubmission>> { rows ->
                rows.size == 1 &&
                    rows.single().studentId == 11L &&
                    rows.single().writingScore == null &&
                    rows.single().readingScore == 65 &&
                    rows.single().speakingScore == 90 &&
                    rows.single().recoverySkillSet() == setOf(RecoverySkill.WRITING)
            })
        }
    }

    private fun sourceSubmission(
        examId: UUID,
        studentId: Long,
        reading: Int,
        writing: Int,
        listening: Int,
        speaking: Int,
        finalScore: BigDecimal
    ) = ExamSubmission(
        examId = examId,
        studentId = studentId,
        status = SubmissionStatus.GRADED,
        score = finalScore,
        readingScore = reading,
        writingScore = writing,
        listeningScore = listening,
        speakingScore = speaking,
        createdBy = 1
    )
}
