package com.sigep.staff.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.staff.application.dto.CreateTeachingStaffRequest
import com.sigep.staff.application.dto.TeachingStaffDto
import com.sigep.staff.application.dto.UpdateTeachingStaffRequest
import com.sigep.staff.application.service.TeachingStaffService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/staff/teaching")
@Tag(name = "Teaching Staff", description = "API for managing teaching staff (docentes)")
@SecurityRequirement(name = "Bearer Authentication")
class TeachingStaffController(
    private val teachingStaffService: TeachingStaffService
) {

    @GetMapping
    @RequireAdmin
    @Operation(summary = "Get all teaching staff", description = "Retrieve a paginated list of all teaching staff")
    fun getAllTeachingStaff(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "lastName") sort: String,
        @RequestParam(defaultValue = "ASC") order: String
    ): ResponseEntity<ApiResponse<PageResponse<TeachingStaffDto>>> {
        val staff = teachingStaffService.getAllTeachingStaff(page, limit, sort, order)
        return ResponseEntity.ok(ApiResponse.success(staff))
    }

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get teaching staff by ID", description = "Retrieve detailed information of a teaching staff member including students, courses and attendance")
    fun getTeachingStaffById(@PathVariable id: Long): ResponseEntity<ApiResponse<TeachingStaffDto>> {
        val staff = teachingStaffService.getTeachingStaffById(id)
        return ResponseEntity.ok(ApiResponse.success(staff))
    }

    @GetMapping("/search")
    @RequireAdmin
    @Operation(summary = "Search teaching staff", description = "Search teaching staff by name, email or document number")
    fun searchTeachingStaff(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TeachingStaffDto>>> {
        val staff = teachingStaffService.searchTeachingStaff(query, page, limit)
        return ResponseEntity.ok(ApiResponse.success(staff))
    }

    @PostMapping
    @RequireAdmin
    @Operation(summary = "Create teaching staff", description = "Create a new teaching staff member")
    fun createTeachingStaff(
        @Valid @RequestBody request: CreateTeachingStaffRequest
    ): ResponseEntity<ApiResponse<TeachingStaffDto>> {
        val staff = teachingStaffService.createTeachingStaff(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(staff, "Teaching staff created successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Update teaching staff", description = "Update teaching staff information including salary, payment status and notes")
    fun updateTeachingStaff(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTeachingStaffRequest
    ): ResponseEntity<ApiResponse<TeachingStaffDto>> {
        val staff = teachingStaffService.updateTeachingStaff(id, request)
        return ResponseEntity.ok(ApiResponse.success(staff, "Teaching staff updated successfully"))
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Delete teaching staff", description = "Soft delete a teaching staff member")
    fun deleteTeachingStaff(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        teachingStaffService.deleteTeachingStaff(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Teaching staff deleted successfully"))
    }
}

