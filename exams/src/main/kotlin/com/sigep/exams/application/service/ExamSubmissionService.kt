package com.sigep.exams.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.service.CourseAccessProvider
import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.TeacherInfoProvider
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ExamSubmissionService(
    private val submissionRepository: ExamSubmissionRepository,
    private val examRepository: ExamRepository,
    private val gradeHistoryRepository: ExamGradeHistoryRepository,
    private val examService: ExamService,
    private val teacherInfoProvider: TeacherInfoProvider,
    private val studentProfileProvider: StudentProfileProvider,
    private val courseAccessProvider: CourseAccessProvider
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
        sort: String = "createdAt",
        order: String = "ASC",
        actorUserId: Long,
        actorRole: String?
    ): PageResponse<ExamSubmissionDto> {
        val exam = examRepository.findById(examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $examId") }
        validateCourseAccess(exam.courseId, actorUserId, actorRole)
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
        studentId: Long,
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

    @Transactional
    fun getGradebook(examId: UUID, actorUserId: Long, actorRole: String?): ExamGradebookDto {
        val exam = examRepository.findById(examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $examId") }
        validateCourseAccess(exam.courseId, actorUserId, actorRole)
        synchronizeActiveStudents(exam, actorUserId)
        return buildGradebook(exam)
    }

    @Transactional
    @CacheEvict(value = ["submissions"], allEntries = true)
    fun updateGradesBatch(
        examId: UUID,
        request: BatchGradeRequest,
        actorUserId: Long,
        actorRole: String?
    ): ExamGradebookDto {
        val exam = examRepository.findById(examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $examId") }
        validateCourseAccess(exam.courseId, actorUserId, actorRole)

        val duplicateIds = request.changes.groupingBy { it.submissionId }.eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            throw ValidationException("El lote contiene filas de estudiantes duplicadas")
        }

        val submissionsById = submissionRepository.findAllById(request.changes.map { it.submissionId })
            .associateBy { it.id }
        val missingIds = request.changes.map { it.submissionId }.filterNot(submissionsById::containsKey)
        if (missingIds.isNotEmpty()) {
            throw ResourceNotFoundException("No se encontraron submissions del lote: ${missingIds.joinToString()}")
        }

        val histories = mutableListOf<ExamGradeHistory>()
        val changedSubmissions = mutableListOf<ExamSubmission>()

        request.changes.forEach { change ->
            val submission = submissionsById.getValue(change.submissionId)
            if (submission.examId != examId) {
                throw ValidationException("La fila ${submission.id} no pertenece al examen solicitado")
            }

            val sameValues = submission.readingScore == change.readingScore &&
                submission.writingScore == change.writingScore &&
                submission.listeningScore == change.listeningScore &&
                submission.feedback.orEmpty() == change.feedback.orEmpty()
            if (submission.version != change.expectedVersion) {
                if (sameValues) return@forEach
                throw ResourceConflictException(
                    message = "La calificación de un estudiante fue modificada por otro usuario",
                    code = "GRADE_VERSION_CONFLICT",
                    field = "version",
                    details = submission.id.toString()
                )
            }
            if (sameValues) return@forEach

            val gradeChanged = submission.readingScore != change.readingScore ||
                submission.writingScore != change.writingScore ||
                submission.listeningScore != change.listeningScore
            val hadPreviousGrade = submission.score != null ||
                submission.readingScore != null ||
                submission.writingScore != null ||
                submission.listeningScore != null
            if (gradeChanged && hadPreviousGrade && change.reason.isNullOrBlank()) {
                throw ValidationException(
                    message = "Debe indicar el motivo al modificar una calificación existente",
                    field = "reason"
                )
            }

            val newFinalScore = calculateFinalScore(
                change.readingScore,
                change.writingScore,
                change.listeningScore
            )
            histories += ExamGradeHistory(
                submissionId = submission.id,
                changedBy = actorUserId,
                previousScore = submission.score,
                newScore = newFinalScore,
                previousReadingScore = submission.readingScore,
                newReadingScore = change.readingScore,
                previousWritingScore = submission.writingScore,
                newWritingScore = change.writingScore,
                previousListeningScore = submission.listeningScore,
                newListeningScore = change.listeningScore,
                reason = change.reason?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (hadPreviousGrade) "Actualización de calificación por categorías" else "Carga inicial por categorías",
                createdBy = actorUserId
            )

            try {
                submission.updateSkillGrades(
                    readingScore = change.readingScore,
                    writingScore = change.writingScore,
                    listeningScore = change.listeningScore,
                    updatedBy = actorUserId,
                    feedback = change.feedback?.trim()?.takeIf { it.isNotEmpty() }
                )
            } catch (ex: IllegalArgumentException) {
                throw ValidationException(ex.message ?: "Calificación inválida")
            }
            changedSubmissions += submission
        }

        if (histories.isNotEmpty()) gradeHistoryRepository.saveAll(histories)
        if (changedSubmissions.isNotEmpty()) submissionRepository.saveAllAndFlush(changedSubmissions)

        return buildGradebook(exam)
    }

    fun getStudentExamHistory(studentId: Long, courseId: Long): List<ExamResultSummary> {
        val submissions = submissionRepository.findStudentSubmissionsByCourse(studentId, courseId)

        return submissions.map { submission ->
            val exam = examRepository.findById(submission.examId).orElse(null)
            val assignedTeachers = exam?.let { examService.parseAssignedTeachers(it.assignedTeachers) }
            ExamResultSummary(
                examId = submission.examId,
                examTitle = exam?.title ?: "Examen no encontrado",
                scheduledAt = exam?.scheduledAt,
                totalPoints = exam?.totalPoints ?: java.math.BigDecimal.ZERO,
                assignedTeachers = assignedTeachers,
                teacherNames = examService.resolveTeacherNames(assignedTeachers),
                score = submission.score,
                readingScore = submission.readingScore,
                writingScore = submission.writingScore,
                listeningScore = submission.listeningScore,
                status = submission.status,
                gradedBy = submission.gradedBy,
                gradedByName = submission.gradedBy?.let { teacherInfoProvider.getTeacherNameById(it) ?: it.toString() },
                gradedAt = submission.gradedAt,
                feedback = submission.feedback
            )
        }
    }

    @Transactional
    @CacheEvict(value = ["submissions"], allEntries = true)
    fun createSubmission(
        request: CreateSubmissionRequest,
        createdBy: Long,
        actorRole: String?
    ): ExamSubmissionDto {
        // Validar que el examen existe
        val exam = examRepository.findById(request.examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: ${request.examId}") }
        validateCourseAccess(exam.courseId, createdBy, actorRole)

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
        gradedBy: Long,
        actorRole: String?
    ): ExamSubmissionDto {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $submissionId") }

        val exam = examRepository.findById(submission.examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: ${submission.examId}") }
        validateCourseAccess(exam.courseId, gradedBy, actorRole)

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
        updatedBy: Long,
        actorRole: String?
    ): ExamSubmissionDto {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $submissionId") }

        val exam = examRepository.findById(submission.examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: ${submission.examId}") }
        validateCourseAccess(exam.courseId, updatedBy, actorRole)

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
    fun cancelSubmission(submissionId: UUID, updatedBy: Long): ExamSubmissionDto {
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

    fun getGradeHistory(
        submissionId: UUID,
        actorUserId: Long,
        actorRole: String?
    ): List<GradeHistoryDto> {
        val submission = submissionRepository.findById(submissionId)
            .orElseThrow { ResourceNotFoundException("Submission no encontrado con ID: $submissionId") }
        val exam = examRepository.findById(submission.examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: ${submission.examId}") }
        validateCourseAccess(exam.courseId, actorUserId, actorRole)
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
        readingScore = submission.readingScore,
        writingScore = submission.writingScore,
        listeningScore = submission.listeningScore,
        gradedBy = submission.gradedBy,
        gradedByName = submission.gradedBy?.let { teacherInfoProvider.getTeacherNameById(it) ?: it.toString() },
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
        changedByName = null, // TODO: enriquecer con nombre del usuario vía UserServiceProvider en common
        previousScore = history.previousScore,
        newScore = history.newScore,
        previousReadingScore = history.previousReadingScore,
        newReadingScore = history.newReadingScore,
        previousWritingScore = history.previousWritingScore,
        newWritingScore = history.newWritingScore,
        previousListeningScore = history.previousListeningScore,
        newListeningScore = history.newListeningScore,
        reason = history.reason
    )

    private fun buildGradebook(exam: com.sigep.exams.domain.model.Exam): ExamGradebookDto {
        val submissions = submissionRepository.findAllByExamIdOrderByStudentIdAscAttemptNumberAsc(exam.id)
        val studentProfiles = studentProfileProvider.getStudentProfiles(submissions.map { it.studentId })
        val rows = submissions.map { submission ->
            toGradebookRow(submission, studentProfiles[submission.studentId])
        }.sortedBy { it.studentName.lowercase() }
        val average = rows.mapNotNull { it.finalScore }
            .takeIf { it.isNotEmpty() }
            ?.let { scores ->
                scores.reduce(BigDecimal::add)
                    .divide(BigDecimal(scores.size), 0, RoundingMode.HALF_UP)
            }
        val courseInfo = courseAccessProvider.getCourseInfo(exam.courseId)

        return ExamGradebookDto(
            examId = exam.id,
            examTitle = exam.title,
            courseId = exam.courseId,
            courseCode = courseInfo?.code,
            courseName = courseInfo?.name,
            totalStudents = rows.size,
            completedCount = rows.count {
                it.completionStatus == GradeCompletionStatus.COMPLETE ||
                    it.completionStatus == GradeCompletionStatus.LEGACY_FINAL_ONLY
            },
            incompleteCount = rows.count { it.completionStatus == GradeCompletionStatus.INCOMPLETE },
            pendingCount = rows.count { it.completionStatus == GradeCompletionStatus.NOT_STARTED },
            averageFinalScore = average,
            rows = rows
        )
    }

    private fun synchronizeActiveStudents(exam: com.sigep.exams.domain.model.Exam, actorUserId: Long) {
        val activeStudentIds = courseAccessProvider.getActiveStudentIds(exam.courseId)
        if (activeStudentIds.isEmpty()) return

        val existingStudentIds = submissionRepository
            .findAllByExamIdOrderByStudentIdAscAttemptNumberAsc(exam.id)
            .mapTo(mutableSetOf()) { it.studentId }
        val missingSubmissions = (activeStudentIds - existingStudentIds).map { studentId ->
            ExamSubmission(
                examId = exam.id,
                studentId = studentId,
                status = SubmissionStatus.PENDING,
                createdBy = actorUserId
            )
        }
        if (missingSubmissions.isNotEmpty()) submissionRepository.saveAllAndFlush(missingSubmissions)
    }

    private fun toGradebookRow(
        submission: ExamSubmission,
        studentProfile: StudentProfileInfo?
    ): GradebookRowDto {
        val completionStatus = when {
            submission.readingScore != null &&
                submission.writingScore != null &&
                submission.listeningScore != null -> GradeCompletionStatus.COMPLETE
            submission.score != null -> GradeCompletionStatus.LEGACY_FINAL_ONLY
            submission.readingScore != null ||
                submission.writingScore != null ||
                submission.listeningScore != null -> GradeCompletionStatus.INCOMPLETE
            else -> GradeCompletionStatus.NOT_STARTED
        }
        return GradebookRowDto(
            submissionId = submission.id,
            studentId = submission.studentId,
            studentName = studentProfile?.let { "${it.firstName} ${it.lastName}" }
                ?: "Estudiante #${submission.studentId}",
            studentEmail = studentProfile?.email,
            attemptNumber = submission.attemptNumber,
            status = submission.status,
            completionStatus = completionStatus,
            readingScore = submission.readingScore,
            writingScore = submission.writingScore,
            listeningScore = submission.listeningScore,
            finalScore = submission.score,
            passed = submission.score?.let { it >= BigDecimal("60") },
            feedback = submission.feedback,
            gradedBy = submission.gradedBy,
            gradedByName = submission.gradedBy?.let {
                teacherInfoProvider.getTeacherNameById(it) ?: it.toString()
            },
            gradedAt = submission.gradedAt,
            version = submission.version
        )
    }

    private fun calculateFinalScore(readingScore: Int?, writingScore: Int?, listeningScore: Int?): BigDecimal? {
        if (readingScore == null || writingScore == null || listeningScore == null) return null
        return ExamSubmission.calculateFinalScore(readingScore, writingScore, listeningScore)
    }

    private fun validateCourseAccess(courseId: Long, actorUserId: Long, actorRole: String?) {
        when (actorRole) {
            "ADMIN" -> return
            "TEACHER" -> if (courseAccessProvider.isTeacherAssignedToCourse(courseId, actorUserId)) return
        }
        throw ForbiddenException("Los docentes solo pueden calificar exámenes de sus cursos asignados")
    }
}

