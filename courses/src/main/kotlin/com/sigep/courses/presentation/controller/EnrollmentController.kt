package com.sigep.courses.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.courses.application.dto.EnrollmentDto
import com.sigep.courses.application.dto.StudentEnrollmentHistoryDto
import com.sigep.courses.application.dto.UpdateEnrollmentRequest
import com.sigep.courses.application.dto.BulkEnrollmentRequest
import com.sigep.courses.application.service.EnrollmentService
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.security.application.annotation.RequireStaffOrGuardian
import com.sigep.security.application.annotation.RequireAdmin
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "API for managing student enrollments in courses")
@SecurityRequirement(name = "Bearer Authentication")
class EnrollmentController(
    private val enrollmentService: EnrollmentService
) {

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get enrollment by ID", description = "Retrieve a specific enrollment by its ID")
    fun getEnrollmentById(@PathVariable id: Long): ResponseEntity<ApiResponse<EnrollmentDto>> {
        val enrollment = enrollmentService.getEnrollmentById(id)
        return ResponseEntity.ok(ApiResponse.success(enrollment))
    }

    @GetMapping("/student/{studentId}")
    @RequireStaffOrGuardian
    @Operation(summary = "Get student enrollments", description = "Retrieve all enrollments for a specific student")
    fun getStudentEnrollments(
        @PathVariable studentId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<EnrollmentDto>>> {
        val enrollments = enrollmentService.getStudentEnrollments(studentId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(enrollments))
    }

    @GetMapping("/student/{studentId}/history")
    @RequireStaffOrGuardian
    @Operation(summary = "Get student enrollment history", description = "Retrieve complete enrollment history for a student with grades")
    fun getStudentEnrollmentHistory(
        @PathVariable studentId: Long
    ): ResponseEntity<ApiResponse<StudentEnrollmentHistoryDto>> {
        val history = enrollmentService.getStudentEnrollmentHistory(studentId)
        return ResponseEntity.ok(ApiResponse.success(history))
    }

    @GetMapping("/course/{courseId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get course enrollments", description = "Retrieve all enrollments for a specific course")
    fun getCourseEnrollments(
        @PathVariable courseId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<EnrollmentDto>>> {
        val enrollments = enrollmentService.getCourseEnrollments(courseId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(enrollments))
    }

    @PutMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Update enrollment", description = "Update enrollment details including status and grades")
    fun updateEnrollment(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateEnrollmentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<EnrollmentDto>> {
        val userId = httpRequest.getAttribute("userId") as? Long
        val userRole = httpRequest.getAttribute("userRole") as? String

        val enrollment = enrollmentService.updateEnrollment(id, request, userId, userRole)
        return ResponseEntity.ok(ApiResponse.success(enrollment, "Enrollment updated successfully"))
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Delete enrollment", description = "Delete an enrollment (Admin only)")
    fun deleteEnrollment(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        enrollmentService.deleteEnrollment(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Enrollment deleted successfully"))
    }

    @PostMapping("/bulk")
    @RequireAdminOrTeacher
    @Operation(summary = "Bulk create enrollments", description = "Enroll multiple students in a course")
    fun createBulkEnrollments(
        @Valid @RequestBody request: BulkEnrollmentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<EnrollmentDto>>> {
        val userId = httpRequest.getAttribute("userId") as? Long
        val userRole = httpRequest.getAttribute("userRole") as? String
        val enrollments = enrollmentService.createBulkEnrollments(request, userId, userRole)
        return ResponseEntity
            .status(org.springframework.http.HttpStatus.CREATED)
            .body(ApiResponse.success(enrollments, "Bulk enrollments created successfully"))
    }
}

