package com.sigep.exams.application.dto

import com.sigep.exams.domain.model.ExamModality
import com.sigep.exams.domain.model.ExamStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class ExamDto(
    val id: UUID,
    val courseId: UUID,
    val title: String,
    val description: String?,
    val modality: ExamModality,
    val status: ExamStatus,
    val totalPoints: BigDecimal,
    val weight: BigDecimal,
    val timeLimitMinutes: Int?,
    val scheduledAt: LocalDateTime?,
    val visibilityStart: LocalDateTime?,
    val visibilityEnd: LocalDateTime?,
    val assignedTeachers: List<UUID>?,
    val notes: String?,
    val roomInfo: String?,
    val version: Int,
    val createdAt: LocalDateTime,
    val createdBy: UUID,
    val updatedAt: LocalDateTime?,
    val updatedBy: UUID?
)

data class CreateExamRequest(
    val courseId: UUID,
    val title: String,
    val description: String? = null,
    val totalPoints: BigDecimal = BigDecimal("100.00"),
    val weight: BigDecimal = BigDecimal("1.00"),
    val timeLimitMinutes: Int? = null,
    val scheduledAt: LocalDateTime? = null,
    val visibilityStart: LocalDateTime? = null,
    val visibilityEnd: LocalDateTime? = null,
    val assignedTeachers: List<UUID>? = null,
    val notes: String? = null,
    val roomInfo: String? = null
)

data class UpdateExamRequest(
    val title: String?,
    val description: String?,
    val totalPoints: BigDecimal?,
    val weight: BigDecimal?,
    val timeLimitMinutes: Int?,
    val scheduledAt: LocalDateTime?,
    val visibilityStart: LocalDateTime?,
    val visibilityEnd: LocalDateTime?,
    val assignedTeachers: List<UUID>?,
    val notes: String?,
    val roomInfo: String?
)

data class ExamSummaryDto(
    val id: UUID,
    val courseId: UUID,
    val title: String,
    val status: ExamStatus,
    val scheduledAt: LocalDateTime?,
    val totalPoints: BigDecimal,
    val weight: BigDecimal,
    val assignedTeachers: List<UUID>? = null,
    val totalSubmissions: Int = 0,
    val gradedSubmissions: Int = 0,
    val pendingSubmissions: Int = 0
)

data class TeacherPerformanceDto(
    val teacherId: UUID,
    val totalExamsCreated: Int,
    val totalExamsPublished: Int,
    val totalExamsClosed: Int,
    val totalStudentsEvaluated: Int,
    val averageScore: BigDecimal?,
    val passRate: BigDecimal?,
    val examsByStatus: Map<ExamStatus, Int>,
    val examsByCourse: Map<UUID, CourseExamSummaryDto>,
    val recentExams: List<ExamSummaryDto>
)

data class CourseExamSummaryDto(
    val courseId: UUID,
    val totalExams: Int,
    val averageScore: BigDecimal?,
    val passRate: BigDecimal?,
    val totalStudents: Int
)

