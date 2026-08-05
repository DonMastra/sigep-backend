package com.sigep.exams.application.dto

import com.sigep.exams.domain.model.SubmissionStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
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
    val readingScore: Int?,
    val writingScore: Int?,
    val listeningScore: Int?,
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
    val readingScore: Int? = null,
    val writingScore: Int? = null,
    val listeningScore: Int? = null,
    val gradedBy: Long?,
    val gradedByName: String? = null,
    val gradedAt: LocalDateTime?,
    val feedback: String?,
    val scannedFilePath: String?,
    val version: Int = 1
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
    val readingScore: Int? = null,
    val writingScore: Int? = null,
    val listeningScore: Int? = null,
    val status: SubmissionStatus,
    val gradedBy: Long?,
    val gradedByName: String? = null,
    val gradedAt: LocalDateTime?,
    val feedback: String?
)

enum class GradeCompletionStatus {
    NOT_STARTED,
    INCOMPLETE,
    COMPLETE,
    LEGACY_FINAL_ONLY
}

data class ExamGradebookDto(
    val examId: UUID,
    val examTitle: String,
    val courseId: Long,
    val courseCode: String?,
    val courseName: String?,
    val totalStudents: Int,
    val completedCount: Int,
    val incompleteCount: Int,
    val pendingCount: Int,
    val averageFinalScore: BigDecimal?,
    val rows: List<GradebookRowDto>
)

data class GradebookRowDto(
    val submissionId: UUID,
    val studentId: Long,
    val studentName: String,
    val studentEmail: String?,
    val attemptNumber: Int,
    val status: SubmissionStatus,
    val completionStatus: GradeCompletionStatus,
    val readingScore: Int?,
    val writingScore: Int?,
    val listeningScore: Int?,
    val finalScore: BigDecimal?,
    val passed: Boolean?,
    val feedback: String?,
    val gradedBy: Long?,
    val gradedByName: String?,
    val gradedAt: LocalDateTime?,
    val version: Int
)

data class BatchGradeRequest(
    @field:NotEmpty(message = "Debe enviar al menos una calificación")
    @field:Size(max = 200, message = "No se pueden guardar más de 200 filas por lote")
    @field:Valid
    val changes: List<BatchGradeItemRequest>
)

data class BatchGradeItemRequest(
    val submissionId: UUID,

    @field:Min(value = 1, message = "La versión debe ser mayor o igual a 1")
    val expectedVersion: Int,

    @field:Min(value = 0, message = "Reading debe estar entre 0 y 100")
    @field:Max(value = 100, message = "Reading debe estar entre 0 y 100")
    val readingScore: Int?,

    @field:Min(value = 0, message = "Writing debe estar entre 0 y 100")
    @field:Max(value = 100, message = "Writing debe estar entre 0 y 100")
    val writingScore: Int?,

    @field:Min(value = 0, message = "Listening debe estar entre 0 y 100")
    @field:Max(value = 100, message = "Listening debe estar entre 0 y 100")
    val listeningScore: Int?,

    @field:Size(max = 4000, message = "La devolución no puede superar los 4000 caracteres")
    val feedback: String? = null,

    @field:Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
    val reason: String? = null
)

