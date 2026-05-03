package com.sigep.exams.application.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sigep.common.application.dto.PageResponse
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
import org.springframework.cache.annotation.Cacheable
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
    private val teacherInfoProvider: TeacherInfoProvider
) {

    private val longListType = object : TypeReference<List<Long>>() {}

    @Cacheable(value = ["exams"], key = "#id")
    fun getExamById(id: UUID): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }
        return toDto(exam)
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
    fun createExam(request: CreateExamRequest, createdBy: Long): ExamDto {
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
            modality = ExamModality.OFFLINE, // Fase 1: solo offline
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
    fun updateExam(id: UUID, request: UpdateExamRequest, updatedBy: Long): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }

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
    fun publishExam(id: UUID, updatedBy: Long): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }

        exam.publish()
        val updated = exam.copy(updatedBy = updatedBy, updatedAt = LocalDateTime.now())
        val saved = examRepository.save(updated)
        return toDto(saved)
    }

    @Transactional
    @CacheEvict(value = ["exams"], key = "#id")
    fun closeExam(id: UUID, updatedBy: Long): ExamDto {
        val exam = examRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Examen no encontrado con ID: $id") }

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

        return ExamDto(
            id = exam.id,
            courseId = exam.courseId,
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

    fun parseAssignedTeachers(json: String?): List<Long>? =
        json?.let { objectMapper.readValue(it, longListType) }

    fun resolveTeacherNames(assignedTeachers: List<Long>?): List<String>? {
        if (assignedTeachers.isNullOrEmpty()) {
            return null
        }

        val namesById = teacherInfoProvider.getTeacherNamesByIds(assignedTeachers)
        return assignedTeachers.map { teacherId -> namesById[teacherId] ?: teacherId.toString() }
    }
}
