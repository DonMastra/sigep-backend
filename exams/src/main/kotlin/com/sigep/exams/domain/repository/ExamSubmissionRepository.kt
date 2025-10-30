package com.sigep.exams.domain.repository

import com.sigep.exams.domain.model.ExamSubmission
import com.sigep.exams.domain.model.SubmissionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ExamSubmissionRepository : JpaRepository<ExamSubmission, UUID> {

    fun findByExamId(examId: UUID, pageable: Pageable): Page<ExamSubmission>

    fun findByStudentId(studentId: UUID, pageable: Pageable): Page<ExamSubmission>

    fun findByExamIdAndStudentId(examId: UUID, studentId: UUID): List<ExamSubmission>

    fun findByExamIdAndStudentIdAndAttemptNumber(
        examId: UUID,
        studentId: UUID,
        attemptNumber: Int
    ): Optional<ExamSubmission>

    @Query("""
        SELECT s FROM ExamSubmission s
        WHERE s.examId = :examId
        AND s.status = :status
    """)
    fun findByExamIdAndStatus(
        @Param("examId") examId: UUID,
        @Param("status") status: SubmissionStatus,
        pageable: Pageable
    ): Page<ExamSubmission>

    @Query("""
        SELECT COUNT(s) FROM ExamSubmission s
        WHERE s.examId = :examId
        AND s.studentId = :studentId
    """)
    fun countAttemptsByExamAndStudent(
        @Param("examId") examId: UUID,
        @Param("studentId") studentId: UUID
    ): Int

    @Query("""
        SELECT s FROM ExamSubmission s
        WHERE s.examId IN (
            SELECT e.id FROM Exam e WHERE e.courseId = :courseId
        )
        AND s.studentId = :studentId
        ORDER BY s.auditMetadata.createdAt DESC
    """)
    fun findStudentSubmissionsByCourse(
        @Param("studentId") studentId: UUID,
        @Param("courseId") courseId: UUID
    ): List<ExamSubmission>

    @Query("""
        SELECT s FROM ExamSubmission s
        WHERE s.examId IN :examIds
        AND s.status = 'GRADED'
    """)
    fun findGradedSubmissionsByExams(@Param("examIds") examIds: List<UUID>): List<ExamSubmission>
}

