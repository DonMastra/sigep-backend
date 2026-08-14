package com.sigep.exams.application.dto

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class GradeHistoryDto(
    val id: UUID,
    val submissionId: UUID,
    val changedAt: LocalDateTime,
    val changedBy: Long,
    val changedByName: String?,
    val previousScore: BigDecimal?,
    val newScore: BigDecimal?,
    val previousReadingScore: Int? = null,
    val newReadingScore: Int? = null,
    val previousWritingScore: Int? = null,
    val newWritingScore: Int? = null,
    val previousListeningScore: Int? = null,
    val newListeningScore: Int? = null,
    val reason: String?
)

data class ExamStatisticsDto(
    val examId: UUID,
    val examTitle: String,
    val assignedTeachers: List<Long>? = null,
    val teacherNames: List<String>? = null,
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

data class CourseExamStatisticsDto(
    val courseId: Long,
    val totalExams: Int,
    val publishedExams: Int,
    val closedExams: Int,
    val averageGrade: BigDecimal?,
    val examStats: List<ExamStatisticsDto>
)
