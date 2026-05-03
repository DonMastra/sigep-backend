package com.sigep.exams.application.service

import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.exams.application.dto.*
import com.sigep.exams.domain.model.ExamStatus
import com.sigep.exams.domain.model.SubmissionStatus
import com.sigep.exams.domain.repository.ExamRepository
import com.sigep.exams.domain.repository.ExamSubmissionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Servicio especializado para análisis de rendimiento de docentes
 * Permite obtener métricas sobre la performance de los docentes basándose en:
 * - Exámenes creados y gestionados
 * - Resultados obtenidos por los estudiantes
 * - Tasas de aprobación en sus cursos
 */
@Service
@Transactional(readOnly = true)
class TeacherPerformanceService(
    private val examRepository: ExamRepository,
    private val submissionRepository: ExamSubmissionRepository,
    private val examService: ExamService,
    private val teacherInfoProvider: TeacherInfoProvider
) {

    /**
     * Obtiene estadísticas completas de rendimiento para un docente
     */
    fun getTeacherPerformance(teacherId: Long): TeacherPerformanceDto {
        // Obtener todos los exámenes del docente
        val allExamsPage = examRepository.findByTeacherId(
            teacherId,
            PageRequest.of(0, Int.MAX_VALUE)
        )
        val allExams = allExamsPage.content

        // Estadísticas por estado
        val examsByStatus = allExams.groupBy { it.status }
            .mapValues { it.value.size }

        val totalExamCount = allExams.size
        val publishedExamCount = examsByStatus[ExamStatus.PUBLISHED] ?: 0

        // Obtener todas las calificaciones de los exámenes del docente
        val examIds = allExams.map { it.id }
        val allSubmissions = examIds.flatMap { examId ->
            submissionRepository.findByExamId(examId, PageRequest.of(0, Int.MAX_VALUE)).content
        }

        val totalStudentsEvaluated = allSubmissions.size
        val gradedSubmissions = allSubmissions.filter { it.status == SubmissionStatus.GRADED }

        // Calcular promedio general
        val scores = gradedSubmissions.mapNotNull { it.score }
        val averageScore = if (scores.isNotEmpty()) {
            scores.reduce { acc, score -> acc.add(score) }
                .divide(BigDecimal(scores.size), 2, RoundingMode.HALF_UP)
        } else null

        // Calcular tasa de aprobación general (60% como aprobado)
        val totalPoints = allExams.firstOrNull()?.totalPoints ?: BigDecimal("100.00")
        val passingScore = totalPoints.multiply(BigDecimal("0.60"))
        val passedCount = scores.count { it >= passingScore }
        val passRate = if (scores.isNotEmpty()) {
            BigDecimal(passedCount)
                .divide(BigDecimal(scores.size), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
        } else null

        // Agrupar por curso
        val courseExams: List<CourseExamSummaryDto> = allExams.groupBy { it.courseId }
            .map { (courseId, exams) ->
                val courseExamIds = exams.map { it.id }
                val courseSubmissions = allSubmissions.filter { it.examId in courseExamIds }
                val courseGradedSubmissions = courseSubmissions.filter { it.status == SubmissionStatus.GRADED }
                val courseScores = courseGradedSubmissions.mapNotNull { it.score }

                val courseAverage = if (courseScores.isNotEmpty()) {
                    courseScores.reduce { acc, score -> acc.add(score) }
                        .divide(BigDecimal(courseScores.size), 2, RoundingMode.HALF_UP)
                } else null

                val coursePassed = courseScores.count { it >= passingScore }
                val coursePassRate = if (courseScores.isNotEmpty()) {
                    BigDecimal(coursePassed)
                        .divide(BigDecimal(courseScores.size), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal("100"))
                } else null

                CourseExamSummaryDto(
                    courseId = courseId,
                    totalExams = exams.size,
                    averageScore = courseAverage,
                    passRate = coursePassRate,
                    totalStudents = courseSubmissions.size
                )
            }
            .sortedBy { it.courseId }

        // Exámenes recientes (últimos 10)
        val recentExams = allExams
            .sortedByDescending { it.scheduledAt ?: it.createdAt }
            .take(10)
            .map { exam ->
                val examSubmissions = allSubmissions.filter { it.examId == exam.id }
                val assignedTeachers = examService.parseAssignedTeachers(exam.assignedTeachers)
                ExamSummaryDto(
                    id = exam.id,
                    courseId = exam.courseId,
                    title = exam.title,
                    status = exam.status,
                    scheduledAt = exam.scheduledAt,
                    totalPoints = exam.totalPoints,
                    weight = exam.weight,
                    assignedTeachers = assignedTeachers,
                    teacherNames = examService.resolveTeacherNames(assignedTeachers),
                    totalSubmissions = examSubmissions.size,
                    gradedSubmissions = examSubmissions.count { it.status == SubmissionStatus.GRADED },
                    pendingSubmissions = examSubmissions.count { it.status == SubmissionStatus.PENDING }
                )
            }

        return TeacherPerformanceDto(
            teacherId = teacherId,
            fullName = teacherInfoProvider.getTeacherNameById(teacherId) ?: teacherId.toString(),
            totalExamCount = totalExamCount,
            publishedExamCount = publishedExamCount,
            totalStudentsEvaluated = totalStudentsEvaluated,
            averageScore = averageScore,
            passRate = passRate,
            courseExams = courseExams,
            recentExams = recentExams
        )
    }

    /**
     * Obtiene los exámenes de un docente con paginación
     */
    fun getTeacherExams(
        teacherId: Long,
        statuses: List<ExamStatus>? = null,
        page: Int = 0,
        size: Int = 20,
        sort: String = "scheduledAt",
        order: String = "DESC"
    ): List<ExamDto> {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.fromString(order), sort)
        )

        val examsPage = if (statuses != null) {
            examRepository.findByTeacherIdAndStatusIn(teacherId, statuses, pageable)
        } else {
            examRepository.findByTeacherId(teacherId, pageable)
        }

        return examsPage.content.map { examService.toDto(it) }
    }

    /**
     * Compara el rendimiento de múltiples docentes
     */
    fun compareTeachersPerformance(teacherIds: List<Long>): Map<Long, TeacherPerformanceDto> {
        return teacherIds.associateWith { teacherId ->
            getTeacherPerformance(teacherId)
        }
    }
}

