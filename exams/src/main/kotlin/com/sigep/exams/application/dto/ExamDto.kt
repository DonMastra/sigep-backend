package com.sigep.exams.application.dto

import com.sigep.exams.domain.model.ExamModality
import com.sigep.exams.domain.model.ExamStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class ExamDto(
    val id: UUID,
    val courseId: Long,
    val courseCode: String? = null,
    val courseName: String? = null,
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
    val assignedTeachers: List<Long>?,
    val teacherNames: List<String>? = null,
    val notes: String?,
    val roomInfo: String?,
    val version: Int,
    val createdAt: LocalDateTime,
    val createdBy: Long,
    val updatedAt: LocalDateTime?,
    val updatedBy: Long?
)

data class CreateExamRequest(
    val courseId: Long,
    val title: String,
    val description: String? = null,
    val modality: ExamModality = ExamModality.OFFLINE,
    val totalPoints: BigDecimal = BigDecimal("100.00"),
    val weight: BigDecimal = BigDecimal("1.00"),
    val timeLimitMinutes: Int? = null,
    val scheduledAt: LocalDateTime? = null,
    val visibilityStart: LocalDateTime? = null,
    val visibilityEnd: LocalDateTime? = null,
    val assignedTeachers: List<Long>? = null,
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
    val assignedTeachers: List<Long>?,
    val notes: String?,
    val roomInfo: String?
)

data class ExamSummaryDto(
    val id: UUID,
    val courseId: Long,
    val courseCode: String? = null,
    val courseName: String? = null,
    val title: String,
    val modality: ExamModality,
    val status: ExamStatus,
    val scheduledAt: LocalDateTime?,
    val totalPoints: BigDecimal,
    val weight: BigDecimal,
    val assignedTeachers: List<Long>? = null,
    val teacherNames: List<String>? = null,
    val totalSubmissions: Int = 0,
    val gradedSubmissions: Int = 0,
    val pendingSubmissions: Int = 0
)

data class TeacherPerformanceDto(
    val teacherId: Long,
    val fullName: String?,
    val totalExamCount: Int,
    val publishedExamCount: Int,
    val totalStudentsEvaluated: Int,
    val averageScore: BigDecimal?,
    val passRate: BigDecimal?,
    val courseExams: List<CourseExamSummaryDto>,
    val recentExams: List<ExamSummaryDto>
)

data class CourseExamSummaryDto(
    val courseId: Long,
    val totalExams: Int,
    val averageScore: BigDecimal?,
    val passRate: BigDecimal?,
    val totalStudents: Int
)

data class CompareTeachersRequest(
    val teacherIds: List<Long>
)

