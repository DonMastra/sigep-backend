package com.sigep.courses.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.service.CourseMaterialService
import com.sigep.courses.domain.model.MaterialType
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/materials")
@Tag(name = "Course Materials", description = "Course materials management")
class CourseMaterialController(
    private val courseMaterialService: CourseMaterialService
) {

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get material by ID")
    fun getMaterialById(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseMaterialDto>> {
        val material = courseMaterialService.getMaterialById(id)
        return ResponseEntity.ok(ApiResponse.success(material))
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "Get materials by course")
    fun getMaterialsByCourse(
        @PathVariable courseId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "false") visibleOnly: Boolean
    ): ResponseEntity<ApiResponse<PageResponse<CourseMaterialDto>>> {
        val materials = courseMaterialService.getMaterialsByCourse(courseId, page, limit, visibleOnly)
        return ResponseEntity.ok(ApiResponse.success(materials))
    }

    @GetMapping("/course/{courseId}/type/{type}")
    @Operation(summary = "Get materials by course and type")
    fun getMaterialsByCourseAndType(
        @PathVariable courseId: Long,
        @PathVariable type: MaterialType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CourseMaterialDto>>> {
        val materials = courseMaterialService.getMaterialsByCourseAndType(courseId, type, page, limit)
        return ResponseEntity.ok(ApiResponse.success(materials))
    }

    @PostMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Create course material")
    fun createMaterial(
        @Valid @RequestBody request: CreateCourseMaterialRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<CourseMaterialDto>> {
        val uploadedBy = authentication.name.toLongOrNull() ?: 1L
        val material = courseMaterialService.createMaterial(request, uploadedBy)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(material, "Material created successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Update course material")
    fun updateMaterial(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCourseMaterialRequest
    ): ResponseEntity<ApiResponse<CourseMaterialDto>> {
        val material = courseMaterialService.updateMaterial(id, request)
        return ResponseEntity.ok(ApiResponse.success(material, "Material updated successfully"))
    }

    @DeleteMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Delete course material")
    fun deleteMaterial(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        courseMaterialService.deleteMaterial(id)
        return ResponseEntity.ok(ApiResponse.success(Unit, "Material deleted successfully"))
    }

    @PutMapping("/course/{courseId}/reorder")
    @RequireAdminOrTeacher
    @Operation(summary = "Reorder course materials")
    fun reorderMaterials(
        @PathVariable courseId: Long,
        @Valid @RequestBody request: ReorderMaterialsRequest
    ): ResponseEntity<ApiResponse<List<CourseMaterialDto>>> {
        val materials = courseMaterialService.reorderMaterials(courseId, request)
        return ResponseEntity.ok(ApiResponse.success(materials, "Materials reordered successfully"))
    }

    @PutMapping("/{id}/toggle-visibility")
    @RequireAdminOrTeacher
    @Operation(summary = "Toggle material visibility")
    fun toggleVisibility(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseMaterialDto>> {
        val material = courseMaterialService.toggleVisibility(id)
        return ResponseEntity.ok(ApiResponse.success(material, "Visibility toggled successfully"))
    }

    @GetMapping("/course/{courseId}/statistics")
    @RequireAdminOrTeacher
    @Operation(summary = "Get materials statistics for a course")
    fun getMaterialsStatistics(@PathVariable courseId: Long): ResponseEntity<ApiResponse<CourseMaterialsStatisticsDto>> {
        val statistics = courseMaterialService.getMaterialsStatistics(courseId)
        return ResponseEntity.ok(ApiResponse.success(statistics))
    }
}

