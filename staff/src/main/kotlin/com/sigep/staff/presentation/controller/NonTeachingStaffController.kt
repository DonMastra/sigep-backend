package com.sigep.staff.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.staff.application.dto.CreateNonTeachingStaffRequest
import com.sigep.staff.application.dto.NonTeachingStaffDto
import com.sigep.staff.application.dto.UpdateNonTeachingStaffRequest
import com.sigep.staff.application.service.NonTeachingStaffService
import com.sigep.staff.domain.model.NonTeachingRole
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/staff/non-teaching")
@Tag(name = "Non-Teaching Staff", description = "API for managing non-teaching staff (personal no docente)")
@SecurityRequirement(name = "Bearer Authentication")
class NonTeachingStaffController(
    private val nonTeachingStaffService: NonTeachingStaffService
) {

    @GetMapping
    @RequireAdmin
    @Operation(summary = "Get all non-teaching staff", description = "Retrieve a paginated list of all non-teaching staff")
    fun getAllNonTeachingStaff(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "lastName") sort: String,
        @RequestParam(defaultValue = "ASC") order: String
    ): ResponseEntity<ApiResponse<PageResponse<NonTeachingStaffDto>>> {
        val staff = nonTeachingStaffService.getAllNonTeachingStaff(page, limit, sort, order)
        return ResponseEntity.ok(ApiResponse.success(staff))
    }

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get non-teaching staff by ID", description = "Retrieve detailed information of a non-teaching staff member including hours worked and earnings")
    fun getNonTeachingStaffById(@PathVariable id: Long): ResponseEntity<ApiResponse<NonTeachingStaffDto>> {
        val staff = nonTeachingStaffService.getNonTeachingStaffById(id)
        return ResponseEntity.ok(ApiResponse.success(staff))
    }

    @GetMapping("/by-role/{role}")
    @RequireAdmin
    @Operation(summary = "Get non-teaching staff by role", description = "Filter non-teaching staff by their role (cleaning, maintenance, IT, etc.)")
    fun getNonTeachingStaffByRole(
        @PathVariable role: NonTeachingRole,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<NonTeachingStaffDto>>> {
        val staff = nonTeachingStaffService.getNonTeachingStaffByRole(role, page, limit)
        return ResponseEntity.ok(ApiResponse.success(staff))
    }

    @GetMapping("/search")
    @RequireAdmin
    @Operation(summary = "Search non-teaching staff", description = "Search non-teaching staff by name, email, document number or company")
    fun searchNonTeachingStaff(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<NonTeachingStaffDto>>> {
        val staff = nonTeachingStaffService.searchNonTeachingStaff(query, page, limit)
        return ResponseEntity.ok(ApiResponse.success(staff))
    }

    @PostMapping
    @RequireAdmin
    @Operation(summary = "Create non-teaching staff", description = "Create a new non-teaching staff member")
    fun createNonTeachingStaff(
        @Valid @RequestBody request: CreateNonTeachingStaffRequest
    ): ResponseEntity<ApiResponse<NonTeachingStaffDto>> {
        val staff = nonTeachingStaffService.createNonTeachingStaff(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(staff, "Non-teaching staff created successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Update non-teaching staff", description = "Update non-teaching staff information including hourly rate, tasks and company")
    fun updateNonTeachingStaff(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateNonTeachingStaffRequest
    ): ResponseEntity<ApiResponse<NonTeachingStaffDto>> {
        val staff = nonTeachingStaffService.updateNonTeachingStaff(id, request)
        return ResponseEntity.ok(ApiResponse.success(staff, "Non-teaching staff updated successfully"))
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Delete non-teaching staff", description = "Soft delete a non-teaching staff member")
    fun deleteNonTeachingStaff(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        nonTeachingStaffService.deleteNonTeachingStaff(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Non-teaching staff deleted successfully"))
    }
}

