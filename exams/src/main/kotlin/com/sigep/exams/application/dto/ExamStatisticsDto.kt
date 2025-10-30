package com.sigep.exams.application.dto

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * DTO de historial de cambios de calificación
 */
data class GradeHistoryDto(
    val id: UUID,
    val submissionId: UUID,
    val changedAt: LocalDateTime,
    val changedBy: UUID,
    val changedByName: String?,
    val previousScore: BigDecimal?,
    val newScore: BigDecimal,
    val reason: String?
)

/**
 * DTO de estadísticas de un examen
 */
data class ExamStatisticsDto(
    val examId: UUID,
    val examTitle: String,
    val totalStudents: Int,
    val submittedCount: Int,
    val gradedCount: Int,
    val pendingCount: Int,
    val averageScore: BigDecimal?,
    val highestScore: BigDecimal?,
    val lowestScore: BigDecimal?,
    val passRate: BigDecimal?,
    val scoreDistribution: Map<String, Int> // Rangos de notas
)

/**
 * DTO de estadísticas de un curso
 */
data class CourseExamStatisticsDto(
    val courseId: UUID,
    val totalExams: Int,
    val publishedExams: Int,
    val closedExams: Int,
    val averageGrade: BigDecimal?,
    val examStats: List<ExamStatisticsDto>
)

