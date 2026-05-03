package com.sigep.exams.application.dto

import com.sigep.exams.domain.model.SubmissionStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class ExamSubmissionDto(
    val id: UUID,
    val examId: UUID,
    val studentId: Long,
    val attemptNumber: Int,
    val status: SubmissionStatus,
    val startedAt: LocalDateTime?,
    val submittedAt: LocalDateTime?,
    val score: BigDecimal?,
    val gradedBy: Long?,
    val gradedByName: String? = null,
    val gradedAt: LocalDateTime?,
    val feedback: String?,
    val scannedFilePath: String?,
    val notes: String?,
    val version: Int,
    val createdAt: LocalDateTime,
    val createdBy: Long
)

data class CreateSubmissionRequest(
    val examId: UUID,
    val studentId: Long,
    val notes: String? = null
)

data class GradeSubmissionRequest(
    val score: BigDecimal,
    val feedback: String? = null,
    val notes: String? = null
)

data class UpdateGradeRequest(
    val score: BigDecimal,
    val feedback: String? = null,
    val reason: String
)

data class SubmissionWithStudentDto(
    val id: UUID,
    val examId: UUID,
    val examTitle: String,
    val examAssignedTeachers: List<Long>? = null,
    val examTeacherNames: List<String>? = null,
    val studentId: Long,
    val studentName: String,
    val studentEmail: String,
    val attemptNumber: Int,
    val status: SubmissionStatus,
    val score: BigDecimal?,
    val gradedBy: Long?,
    val gradedByName: String? = null,
    val gradedAt: LocalDateTime?,
    val feedback: String?,
    val scannedFilePath: String?
)

data class StudentExamHistoryDto(
    val studentId: Long,
    val courseId: Long,
    val courseName: String,
    val exams: List<ExamResultSummary>
)

data class ExamResultSummary(
    val examId: UUID,
    val examTitle: String,
    val scheduledAt: LocalDateTime?,
    val totalPoints: BigDecimal,
    val assignedTeachers: List<Long>? = null,
    val teacherNames: List<String>? = null,
    val score: BigDecimal?,
    val status: SubmissionStatus,
    val gradedBy: Long?,
    val gradedByName: String? = null,
    val gradedAt: LocalDateTime?,
    val feedback: String?
)

