package com.sigep.exams.application.service

import com.sigep.exams.application.dto.CourseExamStatisticsDto
import com.sigep.exams.application.dto.ExamStatisticsDto
import com.sigep.exams.domain.model.ExamStatus
import com.sigep.exams.domain.model.SubmissionStatus
import com.sigep.exams.domain.repository.ExamRepository
import com.sigep.exams.domain.repository.ExamSubmissionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ExamStatisticsService(
    private val examRepository: ExamRepository,
    private val submissionRepository: ExamSubmissionRepository
) {

    fun getExamStatistics(examId: UUID): ExamStatisticsDto {
        val exam = examRepository.findById(examId).orElseThrow()

        val allSubmissions = submissionRepository.findByExamId(examId, PageRequest.of(0, Int.MAX_VALUE))
        val submissions = allSubmissions.content

        val submittedCount = submissions.size
        val gradedSubmissions = submissions.filter { it.status == SubmissionStatus.GRADED }
        val gradedCount = gradedSubmissions.size
        val pendingCount = submissions.filter { it.status == SubmissionStatus.PENDING }.size

        val scores = gradedSubmissions.mapNotNull { it.score }
        val averageScore = if (scores.isNotEmpty()) {
            scores.reduce { acc, score -> acc.add(score) }
                .divide(BigDecimal(scores.size), 2, RoundingMode.HALF_UP)
        } else null

        val highestScore = scores.maxOrNull()
        val lowestScore = scores.minOrNull()

        // Calcular tasa de aprobación (asumiendo 60% como aprobado)
        val passingScore = exam.totalPoints.multiply(BigDecimal("0.60"))
        val passedCount = scores.count { it >= passingScore }
        val passRate = if (scores.isNotEmpty()) {
            BigDecimal(passedCount)
                .divide(BigDecimal(scores.size), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else null

        // Distribución de notas
        val distribution = mutableMapOf<String, Int>()
        scores.forEach { score ->
            val percentage = score.divide(exam.totalPoints, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
            val range = when {
                percentage >= BigDecimal("90") -> "90-100%"
                percentage >= BigDecimal("80") -> "80-89%"
                percentage >= BigDecimal("70") -> "70-79%"
                percentage >= BigDecimal("60") -> "60-69%"
                else -> "0-59%"
            }
            distribution[range] = distribution.getOrDefault(range, 0) + 1
        }

        return ExamStatisticsDto(
            examId = exam.id,
            examTitle = exam.title,
            totalStudents = submittedCount,
            submittedCount = submittedCount,
            gradedCount = gradedCount,
            pendingCount = pendingCount,
            averageScore = averageScore,
            highestScore = highestScore,
            lowestScore = lowestScore,
            passRate = passRate,
            scoreDistribution = distribution
        )
    }

    fun getCourseExamStatistics(courseId: UUID): CourseExamStatisticsDto {
        val exams = examRepository.findByCourseId(courseId, PageRequest.of(0, Int.MAX_VALUE)).content

        val totalExams = exams.size
        val publishedExams = exams.count { it.status == ExamStatus.PUBLISHED }
        val closedExams = exams.count { it.status == ExamStatus.CLOSED }

        val examStats = exams.map { getExamStatistics(it.id) }

        val allAverages = examStats.mapNotNull { it.averageScore }
        val averageGrade = if (allAverages.isNotEmpty()) {
            allAverages.reduce { acc, score -> acc.add(score) }
                .divide(BigDecimal(allAverages.size), 2, RoundingMode.HALF_UP)
        } else null

        return CourseExamStatisticsDto(
            courseId = courseId,
            totalExams = totalExams,
            publishedExams = publishedExams,
            closedExams = closedExams,
            averageGrade = averageGrade,
            examStats = examStats
        )
    }
}

