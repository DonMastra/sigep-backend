package com.sigep.exams.presentation.controller

import com.sigep.common.application.dto.PageResponse
import com.sigep.exams.application.dto.*
import com.sigep.exams.application.service.ExamSubmissionService
import com.sigep.exams.domain.model.SubmissionStatus
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/exam-submissions")
@Tag(name = "Exam Submissions", description = "Gestión de calificaciones de exámenes")
@SecurityRequirement(name = "bearerAuth")
class ExamSubmissionController(
    private val submissionService: ExamSubmissionService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Obtener submission por ID")
    fun getSubmissionById(
        @PathVariable id: UUID
    ): ExamSubmissionDto {
        return submissionService.getSubmissionById(id)
    }

    @GetMapping("/exam/{examId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Listar submissions de un examen")
    fun getSubmissionsByExam(
        @PathVariable examId: UUID,
        @RequestParam(required = false) status: SubmissionStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "createdAt") sort: String,
        @RequestParam(defaultValue = "ASC") order: String,
        httpRequest: HttpServletRequest
    ): PageResponse<ExamSubmissionDto> {
        return submissionService.getSubmissionsByExam(
            examId = examId,
            status = status,
            page = page,
            size = size,
            sort = sort,
            order = order,
            actorUserId = httpRequest.getAttribute("userId") as Long,
            actorRole = httpRequest.getAttribute("userRole") as? String
        )
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Listar submissions de un estudiante")
    fun getSubmissionsByStudent(
        @PathVariable studentId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        authentication: Authentication
    ): PageResponse<ExamSubmissionDto> {
        // TODO: Validar que sea el mismo estudiante o admin/teacher
        return submissionService.getSubmissionsByStudent(studentId, page, size)
    }

    @GetMapping("/student/{studentId}/course/{courseId}/history")
    @Operation(summary = "Obtener historial de exámenes de un estudiante en un curso")
    fun getStudentExamHistory(
        @PathVariable studentId: Long,
        @PathVariable courseId: Long
    ): List<ExamResultSummary> {
        return submissionService.getStudentExamHistory(studentId, courseId)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireAdminOrTeacher
    @Operation(summary = "Registrar que un estudiante rindió el examen")
    fun createSubmission(
        @Valid @RequestBody request: CreateSubmissionRequest,
        httpRequest: HttpServletRequest
    ): ExamSubmissionDto {
        val createdBy = httpRequest.getAttribute("userId") as Long
        return submissionService.createSubmission(
            request,
            createdBy,
            httpRequest.getAttribute("userRole") as? String
        )
    }

    @PostMapping("/{id}/grade")
    @RequireAdminOrTeacher
    @Operation(summary = "Calificar un submission")
    fun gradeSubmission(
        @PathVariable id: UUID,
        @Valid @RequestBody request: GradeSubmissionRequest,
        httpRequest: HttpServletRequest
    ): ExamSubmissionDto {
        val gradedBy = httpRequest.getAttribute("userId") as Long
        return submissionService.gradeSubmission(
            id,
            request,
            gradedBy,
            httpRequest.getAttribute("userRole") as? String
        )
    }

    @PutMapping("/{id}/grade")
    @RequireAdminOrTeacher
    @Operation(summary = "Actualizar calificación existente")
    fun updateGrade(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateGradeRequest,
        httpRequest: HttpServletRequest
    ): ExamSubmissionDto {
        val updatedBy = httpRequest.getAttribute("userId") as Long
        return submissionService.updateGrade(
            id,
            request,
            updatedBy,
            httpRequest.getAttribute("userRole") as? String
        )
    }

    @PostMapping("/{id}/attach-file")
    @RequireAdminOrTeacher
    @Operation(summary = "Adjuntar archivo escaneado del examen")
    fun attachScannedFile(
        @PathVariable id: UUID,
        @RequestParam filePath: String
    ): ExamSubmissionDto {
        return submissionService.attachScannedFile(id, filePath)
    }

    @PostMapping("/{id}/cancel")
    @RequireAdmin
    @Operation(summary = "Cancelar un submission")
    fun cancelSubmission(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): ExamSubmissionDto {
        val updatedBy = httpRequest.getAttribute("userId") as Long
        return submissionService.cancelSubmission(id, updatedBy)
    }

    @GetMapping("/{id}/grade-history")
    @RequireAdminOrTeacher
    @Operation(summary = "Obtener historial de cambios de calificación")
    fun getGradeHistory(
        @PathVariable id: UUID,
        httpRequest: HttpServletRequest
    ): List<GradeHistoryDto> {
        return submissionService.getGradeHistory(
            id,
            httpRequest.getAttribute("userId") as Long,
            httpRequest.getAttribute("userRole") as? String
        )
    }
}
