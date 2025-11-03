package com.sigep.exams.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.exams.application.dto.*
import com.sigep.exams.domain.model.ExamGradeHistory
import com.sigep.exams.domain.model.ExamSubmission
import com.sigep.exams.domain.model.SubmissionStatus
import com.sigep.exams.domain.repository.ExamGradeHistoryRepository
import com.sigep.exams.domain.repository.ExamRepository
import com.sigep.exams.domain.repository.ExamSubmissionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ExamSubmissionService(
    private val submissionRepository: ExamSubmissionRepository,
    private val examRepository: ExamRepository,
    private val gradeHistoryRepository: ExamGradeHistoryRepository
) {

    @Cacheable(value = ["submissions"], key = "#id")
    fun getSubmissionById(id: UUID): ExamSubmissionDto {
        val submission = submissionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $id") }
        return toDto(submission)
    }

    fun getSubmissionsByExam(
        examId: UUID,
        status: SubmissionStatus?,
        page: Int = 0,
        size: Int = 50,
        sort: String = "auditMetadata.createdAt",
        order: String = "ASC"
    ): PageResponse<ExamSubmissionDto> {
        val pageable = PageRequest.of(
            page, size,
            Sort.by(Sort.Direction.fromString(order), sort)
        )

        val submissionsPage = if (status != null) {
            submissionRepository.findByExamIdAndStatus(examId, status, pageable)
        } else {
            submissionRepository.findByExamId(examId, pageable)
        }

        return PageResponse(
            content = submissionsPage.content.map { toDto(it) },
            page = submissionsPage.number,
            size = submissionsPage.size,
            totalElements = submissionsPage.totalElements,
            totalPages = submissionsPage.totalPages
        )
    }

    fun getSubmissionsByStudent(
        studentId: UUID,
        page: Int = 0,
        size: Int = 50
    ): PageResponse<ExamSubmissionDto> {
        val pageable = PageRequest.of(page, size)
        val submissionsPage = submissionRepository.findByStudentId(studentId, pageable)

        return PageResponse(
            content = submissionsPage.content.map { toDto(it) },
            page = submissionsPage.number,
            size = submissionsPage.size,
            totalElements = submissionsPage.totalElements,
            totalPages = submissionsPage.totalPages
        )
    }

    fun getStudentExamHistory(studentId: UUID, courseId: UUID): List<ExamResultSummary> {
        val submissions = submissionRepository.findStudentSubmissionsByCourse(studentId, courseId)

        return submissions.map { submission ->
            val exam = examRepository.findById(submission.examId).orElse(null)
            ExamResultSummary(
                examId = submission.examId,
                examTitle = exam?.title ?: "Examen no encontrado",
                scheduledAt = exam?.scheduledAt,
                totalPoints = exam?.totalPoints ?: java.math.BigDecimal.ZERO,
                assignedTeachers = exam?.assignedTeachers?.let {
                    it.split(",").map { id -> UUID.fromString(id.trim()) }
                },
                score = submission.score,
                status = submission.status,
                gradedBy = submission.gradedBy,
                gradedAt = submission.gradedAt,
                feedback = submission.feedback
            )
        }
    }

    @Transactional
    @CacheEvict(value = ["submissions"], allEntries = true)
    fun createSubmission(request: CreateSubmissionRequest, createdBy: UUID): ExamSubmissionDto {
        // Validar que el examen existe
        examRepository.findById(request.examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: ${request.examId}") }

        // Calcular el número de intento
        val attemptCount = submissionRepository.countAttemptsByExamAndStudent(
            request.examId,
            request.studentId
        )

        val submission = ExamSubmission(
            examId = request.examId,
            studentId = request.studentId,
            attemptNumber = attemptCount + 1,
            status = SubmissionStatus.PENDING,
            startedAt = LocalDateTime.now(),
            notes = request.notes,
            createdBy = createdBy
        )

        val saved = submissionRepository.save(submission)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["submissions"], key = "#submissionId")
    fun gradeSubmission(
        submissionId: UUID,
        request: GradeSubmissionRequest,
        gradedBy: UUID
    ): ExamSubmissionDto {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $submissionId") }

        val exam = examRepository.findById(submission.examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: ${submission.examId}") }

        // Validar que el puntaje no supere el total
        if (request.score > exam.totalPoints) {
            throw ValidationException(
                "El puntaje ${request.score} supera el total de puntos del examen ${exam.totalPoints}"
            )
        }

        // Guardar en historial si ya estaba calificado
        if (submission.status == SubmissionStatus.GRADED) {
            val history = ExamGradeHistory(
                submissionId = submission.id,
                changedBy = gradedBy,
                previousScore = submission.score,
                newScore = request.score,
                reason = "Actualización de calificación",
                createdBy = gradedBy
            )
            gradeHistoryRepository.save(history)
        }

        // Calificar
        submission.grade(request.score, gradedBy, request.feedback)

        // Actualizar notas si se proporcionan
        if (request.notes != null) {
            val updated = submission.copy(notes = request.notes)
            val saved = submissionRepository.save(updated)
            return toDto(saved)
        }

        val saved = submissionRepository.save(submission)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["submissions"], key = "#submissionId")
    fun updateGrade(
        submissionId: UUID,
        request: UpdateGradeRequest,
        updatedBy: UUID
    ): ExamSubmissionDto {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $submissionId") }

        val exam = examRepository.findById(submission.examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: ${submission.examId}") }

        // Validar que el puntaje no supere el total
        if (request.score > exam.totalPoints) {
            throw ValidationException(
                "El puntaje ${request.score} supera el total de puntos del examen ${exam.totalPoints}"
            )
        }

        // Guardar en historial
        val history = ExamGradeHistory(
            submissionId = submission.id,
            changedBy = updatedBy,
            previousScore = submission.score,
            newScore = request.score,
            reason = request.reason,
            createdBy = updatedBy
        )
        gradeHistoryRepository.save(history)

        // Actualizar nota
        submission.updateScore(request.score, updatedBy, request.feedback)

        val saved = submissionRepository.save(submission)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["submissions"], key = "#submissionId")
    fun attachScannedFile(submissionId: UUID, filePath: String): ExamSubmissionDto {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $submissionId") }

        submission.attachScannedFile(filePath)

        val saved = submissionRepository.save(submission)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["submissions"], key = "#submissionId")
    fun cancelSubmission(submissionId: UUID, updatedBy: UUID): ExamSubmissionDto {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $submissionId") }

        submission.cancel()

        val updated = submission.copy(
            updatedBy = updatedBy,
            updatedAt = LocalDateTime.now()
        )

        val saved = submissionRepository.save(updated)
        return toDto(saved)
    }

    fun getGradeHistory(submissionId: UUID): List<GradeHistoryDto> {
        val history = gradeHistoryRepository.findBySubmissionIdOrderByChangedAtDesc(submissionId)
        return history.map { toHistoryDto(it) }
    }

    // Métodos auxiliares
    private fun toDto(submission: ExamSubmission): ExamSubmissionDto = ExamSubmissionDto(
        id = submission.id,
        examId = submission.examId,
        studentId = submission.studentId,
        attemptNumber = submission.attemptNumber,
        status = submission.status,
        startedAt = submission.startedAt,
        submittedAt = submission.submittedAt,
        score = submission.score,
        gradedBy = submission.gradedBy,
        gradedAt = submission.gradedAt,
        feedback = submission.feedback,
        scannedFilePath = submission.scannedFilePath,
        notes = submission.notes,
        version = submission.version,
        createdAt = submission.createdAt,
        createdBy = submission.createdBy
    )

    private fun toHistoryDto(history: ExamGradeHistory): GradeHistoryDto = GradeHistoryDto(
        id = history.id,
        submissionId = history.submissionId,
        changedAt = history.changedAt,
        changedBy = history.changedBy,
        changedByName = null, // Se podría enriquecer con info del usuario
        previousScore = history.previousScore,
        newScore = history.newScore,
        reason = history.reason
    )
}

