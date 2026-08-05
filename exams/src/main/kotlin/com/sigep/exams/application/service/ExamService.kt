package com.sigep.exams.application.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.service.CourseAccessInfo
import com.sigep.common.application.service.CourseAccessProvider
import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.exams.application.dto.*
import com.sigep.exams.domain.model.Exam
import com.sigep.exams.domain.model.ExamModality
import com.sigep.exams.domain.model.ExamStatus
import com.sigep.exams.domain.repository.ExamRepository
import com.sigep.exams.domain.repository.ExamSubmissionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ExamService(
    private val examRepository: ExamRepository,
    private val submissionRepository: ExamSubmissionRepository,
    private val objectMapper: ObjectMapper,
    private val teacherInfoProvider: TeacherInfoProvider,
    private val courseAccessProvider: CourseAccessProvider
) {

    private val longListType = object : TypeReference<List<Long>>() {}

    fun getExamById(id: UUID, actorUserId: Long, actorRole: String?): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }
        validateCourseAccess(exam.courseId, actorUserId, actorRole)
        return toDto(exam)
    }

    fun getExamsForActor(
        actorUserId: Long,
        actorRole: String?,
        status: ExamStatus?,
        page: Int = 0,
        size: Int = 100
    ): PageResponse<ExamSummaryDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "scheduledAt"))
        val examsPage = when (actorRole) {
            "ADMIN" -> if (status == null) {
                examRepository.findAll(pageable)
            } else {
                examRepository.findByStatus(status, pageable)
            }

            "TEACHER" -> {
                val courseIds = courseAccessProvider.getCourseIdsAssignedToTeacher(actorUserId)
                if (courseIds.isEmpty()) {
                    return PageResponse(emptyList(), page, size, 0, 0)
                }
                if (status == null) {
                    examRepository.findByCourseIdIn(courseIds, pageable)
                } else {
                    examRepository.findByCourseIdInAndStatus(courseIds, status, pageable)
                }
            }

            else -> throw ForbiddenException("No tiene permisos para consultar exámenes")
        }

        val courseInfo = courseAccessProvider.getCourseInfo(examsPage.content.map { it.courseId })
        val counts = if (examsPage.content.isEmpty()) {
            emptyMap()
        } else {
            submissionRepository.summarizeByExamIds(examsPage.content.map { it.id })
                .associateBy { it.examId }
        }

        return PageResponse(
            content = examsPage.content.map { exam ->
                val count = counts[exam.id]
                toSummaryDto(
                    exam = exam,
                    courseInfo = courseInfo[exam.courseId],
                    totalSubmissions = count?.totalSubmissions?.toInt() ?: 0,
                    gradedSubmissions = count?.gradedSubmissions?.toInt() ?: 0,
                    pendingSubmissions = count?.pendingSubmissions?.toInt() ?: 0
                )
            },
            page = examsPage.number,
            size = examsPage.size,
            totalElements = examsPage.totalElements,
            totalPages = examsPage.totalPages
        )
    }

    fun getExamsByCourse(
        courseId: Long,
        status: ExamStatus?,
        page: Int = 0,
        size: Int = 20,
        sort: String = "scheduledAt",
        order: String = "DESC"
    ): PageResponse<ExamDto> {
        val pageable = PageRequest.of(
            page, size,
            Sort.by(Sort.Direction.fromString(order), sort)
        )

        val examsPage = if (status != null) {
            examRepository.findByCourseIdAndStatus(courseId, status, pageable)
        } else {
            examRepository.findByCourseId(courseId, pageable)
        }

        return PageResponse(
            content = examsPage.content.map { toDto(it) },
            page = examsPage.number,
            size = examsPage.size,
            totalElements = examsPage.totalElements,
            totalPages = examsPage.totalPages
        )
    }

    fun getExamsByTeacher(
        teacherId: Long,
        courseIds: List<Long>,
        statuses: List<ExamStatus> = listOf(ExamStatus.DRAFT, ExamStatus.PUBLISHED),
        page: Int = 0,
        size: Int = 20
    ): PageResponse<ExamDto> {
        val pageable = PageRequest.of(page, size)
        val examsPage = examRepository.findByCoursesAndStatuses(courseIds, statuses, pageable)

        // Filtrar por docente asignado
        val filteredExams = examsPage.content.filter { exam ->
            exam.assignedTeachers?.contains(teacherId.toString()) == true
        }

        return PageResponse(
            content = filteredExams.map { toDto(it) },
            page = examsPage.number,
            size = examsPage.size,
            totalElements = filteredExams.size.toLong(),
            totalPages = examsPage.totalPages
        )
    }

    fun getVisibleExamsForStudent(
        page: Int = 0,
        size: Int = 20
    ): PageResponse<ExamDto> {
        val pageable = PageRequest.of(page, size)
        val examsPage = examRepository.findVisibleExams(LocalDateTime.now(), pageable)

        return PageResponse(
            content = examsPage.content.map { toDto(it) },
            page = examsPage.number,
            size = examsPage.size,
            totalElements = examsPage.totalElements,
            totalPages = examsPage.totalPages
        )
    }

    @Transactional
    @CacheEvict(value = ["exams"], allEntries = true)
    fun createExam(request: CreateExamRequest, createdBy: Long, actorRole: String?): ExamDto {
        validateCourseAccess(request.courseId, createdBy, actorRole)
        // Validar que no exista otro examen con el mismo título en el curso
        val exists = examRepository.existsByCourseIdAndTitleAndIdNot(
            request.courseId,
            request.title,
            UUID.randomUUID() // Para nuevo, usamos UUID random que nunca coincidirá
        )
        if (exists) {
            throw ValidationException("Ya existe un examen con el título '${request.title}' en este curso")
        }

        val exam = Exam(
            courseId = request.courseId,
            title = request.title,
            description = request.description,
            modality = request.modality,
            status = ExamStatus.DRAFT,
            totalPoints = request.totalPoints,
            weight = request.weight,
            timeLimitMinutes = request.timeLimitMinutes,
            scheduledAt = request.scheduledAt,
            visibilityStart = request.visibilityStart,
            visibilityEnd = request.visibilityEnd,
            assignedTeachers = request.assignedTeachers?.let { objectMapper.writeValueAsString(it) },
            notes = request.notes,
            roomInfo = request.roomInfo,
            createdBy = createdBy
        )

        val saved = examRepository.save(exam)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["exams"], key = "#id")
    fun updateExam(id: UUID, request: UpdateExamRequest, updatedBy: Long, actorRole: String?): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }
        validateCourseAccess(exam.courseId, updatedBy, actorRole)

        // Solo se puede editar si no está cerrado
        if (exam.status == ExamStatus.CLOSED) {
            throw ValidationException("No se puede editar un examen cerrado")
        }

        // Crear entidad actualizada
        val updated = exam.copy(
            title = request.title ?: exam.title,
            description = request.description ?: exam.description,
            totalPoints = request.totalPoints ?: exam.totalPoints,
            weight = request.weight ?: exam.weight,
            timeLimitMinutes = request.timeLimitMinutes ?: exam.timeLimitMinutes,
            scheduledAt = request.scheduledAt ?: exam.scheduledAt,
            visibilityStart = request.visibilityStart ?: exam.visibilityStart,
            visibilityEnd = request.visibilityEnd ?: exam.visibilityEnd,
            assignedTeachers = request.assignedTeachers?.let { objectMapper.writeValueAsString(it) }
                ?: exam.assignedTeachers,
            notes = request.notes ?: exam.notes,
            roomInfo = request.roomInfo ?: exam.roomInfo,
            updatedBy = updatedBy,
            updatedAt = LocalDateTime.now()
        )

        val saved = examRepository.save(updated)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["exams"], key = "#id")
    fun publishExam(id: UUID, updatedBy: Long, actorRole: String?): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }
        validateCourseAccess(exam.courseId, updatedBy, actorRole)

        exam.publish()
        val updated = exam.copy(updatedBy = updatedBy, updatedAt = LocalDateTime.now())
        val saved = examRepository.save(updated)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["exams"], key = "#id")
    fun closeExam(id: UUID, updatedBy: Long, actorRole: String?): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }
        validateCourseAccess(exam.courseId, updatedBy, actorRole)

        exam.close()
        val updated = exam.copy(updatedBy = updatedBy, updatedAt = LocalDateTime.now())
        val saved = examRepository.save(updated)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["exams"], key = "#id")
    fun cancelExam(id: UUID, updatedBy: Long): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }

        exam.cancel()
        val updated = exam.copy(updatedBy = updatedBy, updatedAt = LocalDateTime.now())
        val saved = examRepository.save(updated)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["exams"], key = "#id")
    fun deleteExam(id: UUID) {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }

        // Solo se puede eliminar si está en borrador y no tiene submissions
        if (exam.status != ExamStatus.DRAFT) {
            throw ValidationException("Solo se pueden eliminar exámenes en borrador")
        }

        val submissionCount = submissionRepository.findByExamId(id, PageRequest.of(0, 1)).totalElements
        if (submissionCount > 0) {
            throw ValidationException("No se puede eliminar un examen con registros de estudiantes")
        }

        examRepository.delete(exam)
    }

    // Métodos auxiliares
    fun toDto(exam: Exam): ExamDto {
        val assignedTeachers = parseAssignedTeachers(exam.assignedTeachers)
        val courseInfo = courseAccessProvider.getCourseInfo(exam.courseId)

        return ExamDto(
            id = exam.id,
            courseId = exam.courseId,
            courseCode = courseInfo?.code,
            courseName = courseInfo?.name,
            title = exam.title,
            description = exam.description,
            modality = exam.modality,
            status = exam.status,
            totalPoints = exam.totalPoints,
            weight = exam.weight,
            timeLimitMinutes = exam.timeLimitMinutes,
            scheduledAt = exam.scheduledAt,
            visibilityStart = exam.visibilityStart,
            visibilityEnd = exam.visibilityEnd,
            assignedTeachers = assignedTeachers,
            teacherNames = resolveTeacherNames(assignedTeachers),
            notes = exam.notes,
            roomInfo = exam.roomInfo,
            version = exam.version,
            createdAt = exam.createdAt,
            createdBy = exam.createdBy,
            updatedAt = exam.updatedAt,
            updatedBy = exam.updatedBy
        )
    }

    private fun toSummaryDto(
        exam: Exam,
        courseInfo: CourseAccessInfo?,
        totalSubmissions: Int,
        gradedSubmissions: Int,
        pendingSubmissions: Int
    ): ExamSummaryDto {
        val assignedTeachers = parseAssignedTeachers(exam.assignedTeachers)
        return ExamSummaryDto(
            id = exam.id,
            courseId = exam.courseId,
            courseCode = courseInfo?.code,
            courseName = courseInfo?.name,
            title = exam.title,
            modality = exam.modality,
            status = exam.status,
            scheduledAt = exam.scheduledAt,
            totalPoints = exam.totalPoints,
            weight = exam.weight,
            assignedTeachers = assignedTeachers,
            teacherNames = resolveTeacherNames(assignedTeachers),
            totalSubmissions = totalSubmissions,
            gradedSubmissions = gradedSubmissions,
            pendingSubmissions = pendingSubmissions
        )
    }

    fun parseAssignedTeachers(json: String?): List<Long>? =
        json?.let { objectMapper.readValue(it, longListType) }

    fun resolveTeacherNames(assignedTeachers: List<Long>?): List<String>? {
        if (assignedTeachers.isNullOrEmpty()) {
            return null
        }

        val namesById = teacherInfoProvider.getTeacherNamesByIds(assignedTeachers)
        return assignedTeachers.map { teacherId -> namesById[teacherId] ?: teacherId.toString() }
    }

    fun validateExamAccess(examId: UUID, actorUserId: Long, actorRole: String?) {
        val exam = examRepository.findById(examId)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $examId") }
        validateCourseAccess(exam.courseId, actorUserId, actorRole)
    }

    private fun validateCourseAccess(courseId: Long, actorUserId: Long, actorRole: String?) {
        when (actorRole) {
            "ADMIN" -> return
            "TEACHER" -> if (courseAccessProvider.isTeacherAssignedToCourse(courseId, actorUserId)) return
        }
        throw ForbiddenException("Los docentes solo pueden gestionar exámenes de sus cursos asignados")
    }
}
