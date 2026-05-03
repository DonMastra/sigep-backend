package com.sigep.exams.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.exams.application.dto.ExamDto
import com.sigep.exams.application.dto.TeacherPerformanceDto
import com.sigep.exams.application.service.TeacherPerformanceService
import com.sigep.exams.domain.model.ExamStatus
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controlador para análisis de rendimiento de docentes
 * Permite a los administradores obtener métricas sobre la gestión de exámenes
 * y resultados académicos de los docentes
 */
@RestController
@RequestMapping("/api/v1/teachers")
@Tag(name = "Teacher Performance", description = "APIs para análisis de rendimiento de docentes")
@SecurityRequirement(name = "Bearer Authentication")
class TeacherPerformanceController(
    private val teacherPerformanceService: TeacherPerformanceService
) {

    @GetMapping("/{teacherId}/performance")
    @RequireAdmin
    @Operation(
        summary = "Obtener estadísticas de rendimiento de un docente",
        description = "Retorna métricas completas sobre los exámenes creados, estudiantes evaluados y tasas de aprobación"
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente"),
        SwaggerApiResponse(responseCode = "403", description = "No autorizado - Solo administradores"),
        SwaggerApiResponse(responseCode = "404", description = "Docente no encontrado")
    )
    fun getTeacherPerformance(
        @Parameter(description = "ID del docente (teaching_staff.id)")
        @PathVariable teacherId: Long
    ): ResponseEntity<ApiResponse<TeacherPerformanceDto>> {
        val performance = teacherPerformanceService.getTeacherPerformance(teacherId)
        return ResponseEntity.ok(
            ApiResponse.success(
                data = performance,
                message = "Estadísticas de rendimiento obtenidas exitosamente"
            )
        )
    }

    @GetMapping("/{teacherId}/exams")
    @RequireAdminOrTeacher
    @Operation(
        summary = "Obtener exámenes de un docente",
        description = "Retorna la lista de exámenes creados o asignados a un docente específico"
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Exámenes obtenidos exitosamente"),
        SwaggerApiResponse(responseCode = "403", description = "No autorizado")
    )
    fun getTeacherExams(
        @Parameter(description = "ID del docente (teaching_staff.id)")
        @PathVariable teacherId: Long,

        @Parameter(description = "Filtrar por estados del examen")
        @RequestParam(required = false) statuses: List<ExamStatus>?,

        @Parameter(description = "Número de página")
        @RequestParam(defaultValue = "0") page: Int,

        @Parameter(description = "Tamaño de página")
        @RequestParam(defaultValue = "20") size: Int,

        @Parameter(description = "Campo para ordenar")
        @RequestParam(defaultValue = "scheduledAt") sort: String,

        @Parameter(description = "Dirección del ordenamiento (ASC/DESC)")
        @RequestParam(defaultValue = "DESC") order: String
    ): ResponseEntity<ApiResponse<List<ExamDto>>> {
        val exams = teacherPerformanceService.getTeacherExams(
            teacherId = teacherId,
            statuses = statuses,
            page = page,
            size = size,
            sort = sort,
            order = order
        )
        return ResponseEntity.ok(
            ApiResponse.success(
                data = exams,
                message = "Exámenes del docente obtenidos exitosamente"
            )
        )
    }

    @PostMapping("/compare")
    @RequireAdmin
    @Operation(
        summary = "Comparar rendimiento de múltiples docentes",
        description = "Permite comparar las métricas de rendimiento entre varios docentes"
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Comparación realizada exitosamente"),
        SwaggerApiResponse(responseCode = "403", description = "No autorizado - Solo administradores")
    )
    fun compareTeachersPerformance(
        @Parameter(description = "Lista de IDs de docentes a comparar (teaching_staff.id)")
        @RequestBody teacherIds: List<Long>
    ): ResponseEntity<ApiResponse<Map<Long, TeacherPerformanceDto>>> {
        val comparison = teacherPerformanceService.compareTeachersPerformance(teacherIds)
        return ResponseEntity.ok(
            ApiResponse.success(
                data = comparison,
                message = "Comparación de docentes realizada exitosamente"
            )
        )
    }
}

