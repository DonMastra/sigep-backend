package com.sigep.exams.presentation.controller

import com.sigep.common.application.dto.PageResponse
import com.sigep.exams.application.dto.*
import com.sigep.exams.application.service.ExamService
import com.sigep.exams.application.service.ExamSubmissionService
import com.sigep.exams.application.service.ExamStatisticsService
import com.sigep.exams.domain.model.ExamStatus
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/exams")
@Tag(name = "Exams", description = "Gestión de exámenes presenciales - Fase 1")
@SecurityRequirement(name = "bearerAuth")
class ExamController(
    private val examService: ExamService,
    private val statisticsService: ExamStatisticsService,
    private val submissionService: ExamSubmissionService
) {

    @GetMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Listar exámenes disponibles para el usuario autenticado")
    fun getExams(
        @RequestParam(required = false) status: ExamStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        httpRequest: HttpServletRequest
    ): PageResponse<ExamSummaryDto> = examService.getExamsForActor(
        actorUserId = httpRequest.getAttribute("userId") as Long,
        actorRole = httpRequest.getAttribute("userRole") as? String,
        status = status,
        page = page,
        size = size
    )

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Obtener examen por ID")
    fun getExamById(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): ExamDto {
        return examService.getExamById(
            id,
            httpRequest.getAttribute("userId") as Long,
            httpRequest.getAttribute("userRole") as? String
        )
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "Listar exámenes de un curso")
    fun getExamsByCourse(
        @PathVariable courseId: Long,
        @RequestParam(required = false) status: ExamStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "scheduledAt") sort: String,
        @RequestParam(defaultValue = "DESC") order: String
    ): PageResponse<ExamDto> {
        return examService.getExamsByCourse(courseId, status, page, size, sort, order)
    }

    @GetMapping("/my-exams")
    @Operation(summary = "Obtener exámenes del docente autenticado")
    @RequireAdminOrTeacher
    fun getMyExams(
        @RequestParam courseIds: List<Long>,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        httpRequest: HttpServletRequest
    ): PageResponse<ExamDto> {
        val teacherId = httpRequest.getAttribute("userId") as Long
        return examService.getExamsByTeacher(teacherId, courseIds, page = page, size = size)
    }

    @GetMapping("/visible")
    @Operation(summary = "Obtener exámenes visibles para estudiantes")
    fun getVisibleExams(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<ExamDto> {
        return examService.getVisibleExamsForStudent(page, size)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireAdminOrTeacher
    @Operation(summary = "Crear nuevo examen")
    fun createExam(
        @Valid @RequestBody request: CreateExamRequest,
        httpRequest: HttpServletRequest
    ): ExamDto {
        val createdBy = httpRequest.getAttribute("userId") as Long
        return examService.createExam(request, createdBy, httpRequest.getAttribute("userRole") as? String)
    }

    @PutMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Actualizar examen existente")
    fun updateExam(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateExamRequest,
        httpRequest: HttpServletRequest
    ): ExamDto {
        val updatedBy = httpRequest.getAttribute("userId") as Long
        return examService.updateExam(id, request, updatedBy, httpRequest.getAttribute("userRole") as? String)
    }

    @PostMapping("/{id}/publish")
    @RequireAdminOrTeacher
    @Operation(summary = "Publicar examen (hacerlo visible)")
    fun publishExam(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): ExamDto {
        val updatedBy = httpRequest.getAttribute("userId") as Long
        return examService.publishExam(id, updatedBy, httpRequest.getAttribute("userRole") as? String)
    }

    @PostMapping("/{id}/close")
    @RequireAdminOrTeacher
    @Operation(summary = "Cerrar examen (finalizar calificaciones)")
    fun closeExam(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): ExamDto {
        val updatedBy = httpRequest.getAttribute("userId") as Long
        return examService.closeExam(id, updatedBy, httpRequest.getAttribute("userRole") as? String)
    }

    @PostMapping("/{id}/cancel")
    @RequireAdmin
    @Operation(summary = "Cancelar examen")
    fun cancelExam(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): ExamDto {
        val updatedBy = httpRequest.getAttribute("userId") as Long
        return examService.cancelExam(id, updatedBy)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireAdmin
    @Operation(summary = "Eliminar examen (solo borradores sin submissions)")
    fun deleteExam(
        @PathVariable id: UUID
    ) {
        examService.deleteExam(id)
    }

    @GetMapping("/{id}/statistics")
    @RequireAdminOrTeacher
    @Operation(summary = "Obtener estadísticas del examen")
    fun getExamStatistics(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): ExamStatisticsDto {
        examService.validateExamAccess(
            id,
            httpRequest.getAttribute("userId") as Long,
            httpRequest.getAttribute("userRole") as? String
        )
        return statisticsService.getExamStatistics(id)
    }

    @GetMapping("/{id}/gradebook")
    @RequireAdminOrTeacher
    @Operation(summary = "Obtener libro de calificaciones por categorías")
    fun getGradebook(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): ExamGradebookDto = submissionService.getGradebook(
        examId = id,
        actorUserId = httpRequest.getAttribute("userId") as Long,
        actorRole = httpRequest.getAttribute("userRole") as? String
    )

    @PatchMapping("/{id}/grades")
    @RequireAdminOrTeacher
    @Operation(summary = "Guardar calificaciones por categorías en lote")
    fun updateGrades(
        @PathVariable id: UUID,
        @Valid @RequestBody request: BatchGradeRequest,
        httpRequest: HttpServletRequest
    ): ExamGradebookDto = submissionService.updateGradesBatch(
        examId = id,
        request = request,
        actorUserId = httpRequest.getAttribute("userId") as Long,
        actorRole = httpRequest.getAttribute("userRole") as? String
    )

    @GetMapping("/course/{courseId}/statistics")
    @RequireAdminOrTeacher
    @Operation(summary = "Obtener estadísticas de todos los exámenes del curso")
    fun getCourseStatistics(
        @PathVariable courseId: Long
    ): CourseExamStatisticsDto {
        return statisticsService.getCourseExamStatistics(courseId)
    }
}

