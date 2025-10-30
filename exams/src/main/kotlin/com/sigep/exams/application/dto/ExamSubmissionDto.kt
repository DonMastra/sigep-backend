package com.sigep.exams.application.dto

import com.sigep.exams.domain.model.SubmissionStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * DTO de respuesta para ExamSubmission
 */
data class ExamSubmissionDto(
    val id: UUID,
    val examId: UUID,
    val studentId: UUID,
    val attemptNumber: Int,
    val status: SubmissionStatus,
    val startedAt: LocalDateTime?,
    val submittedAt: LocalDateTime?,
    val score: BigDecimal?,
    val gradedBy: UUID?,
    val gradedAt: LocalDateTime?,
    val feedback: String?,
    val scannedFilePath: String?,
    val notes: String?,
    val version: Int,
    val createdAt: LocalDateTime,
    val createdBy: UUID
)

/**
 * DTO para crear un submission (registro de estudiante que rindió)
 */
data class CreateSubmissionRequest(
    val examId: UUID,
    val studentId: UUID,
    val notes: String? = null
)

/**
 * DTO para cargar calificación
 */
data class GradeSubmissionRequest(
    val score: BigDecimal,
    val feedback: String? = null,
    val notes: String? = null
)

/**
 * DTO para actualizar calificación existente
 */
data class UpdateGradeRequest(
    val score: BigDecimal,
    val feedback: String? = null,
    val reason: String
)

/**
 * DTO de respuesta para submission con información del estudiante
 */
data class SubmissionWithStudentDto(
    val id: UUID,
    val examId: UUID,
    val examTitle: String,
    val studentId: UUID,
    val studentName: String,
    val studentEmail: String,
    val attemptNumber: Int,
    val status: SubmissionStatus,
    val score: BigDecimal?,
    val gradedBy: UUID?,
    val gradedAt: LocalDateTime?,
    val feedback: String?,
    val scannedFilePath: String?
)

/**
 * DTO de historial de calificaciones del estudiante
 */
data class StudentExamHistoryDto(
    val studentId: UUID,
    val courseId: UUID,
    val courseName: String,
    val exams: List<ExamResultSummary>
)

data class ExamResultSummary(
    val examId: UUID,
    val examTitle: String,
    val scheduledAt: LocalDateTime?,
    val totalPoints: BigDecimal,
    val score: BigDecimal?,
    val status: SubmissionStatus,
    val gradedAt: LocalDateTime?,
    val feedback: String?
)

