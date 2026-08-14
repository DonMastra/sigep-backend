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

    fun findAllByExamIdOrderByStudentIdAscAttemptNumberAsc(examId: UUID): List<ExamSubmission>

    fun findByStudentId(studentId: Long, pageable: Pageable): Page<ExamSubmission>

    fun findByExamIdAndStudentId(examId: UUID, studentId: Long): List<ExamSubmission>

    fun findByExamIdAndStudentIdAndAttemptNumber(
        examId: UUID,
        studentId: Long,
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
        @Param("studentId") studentId: Long
    ): Int

    @Query("""
        SELECT s FROM ExamSubmission s
        WHERE s.examId IN (
            SELECT e.id FROM Exam e WHERE e.courseId = :courseId
        )
        AND s.studentId = :studentId
        ORDER BY s.createdAt DESC
    """)
    fun findStudentSubmissionsByCourse(
        @Param("studentId") studentId: Long,
        @Param("courseId") courseId: Long
    ): List<ExamSubmission>

    @Query("""
        SELECT s FROM ExamSubmission s
        WHERE s.examId IN :examIds
        AND s.status = 'GRADED'
    """)
    fun findGradedSubmissionsByExams(@Param("examIds") examIds: List<UUID>): List<ExamSubmission>

    @Query("""
        SELECT s.examId AS examId,
               COUNT(s) AS totalSubmissions,
               SUM(CASE WHEN s.status = 'GRADED' THEN 1 ELSE 0 END) AS gradedSubmissions,
               SUM(CASE WHEN s.status = 'PENDING' THEN 1 ELSE 0 END) AS pendingSubmissions
        FROM ExamSubmission s
        WHERE s.examId IN :examIds
        GROUP BY s.examId
    """)
    fun summarizeByExamIds(@Param("examIds") examIds: Collection<UUID>): List<ExamSubmissionCountProjection>
}

interface ExamSubmissionCountProjection {
    val examId: UUID
    val totalSubmissions: Long
    val gradedSubmissions: Long
    val pendingSubmissions: Long
}
