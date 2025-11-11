package com.sigep.students.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.students.application.dto.CreateStudentRequest
import com.sigep.students.application.dto.StudentDto
import com.sigep.students.application.dto.UpdateStudentRequest
import com.sigep.students.application.service.StudentService
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.security.application.annotation.RequireStaffOrGuardian
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/students")
@Tag(name = "Students", description = "API for managing students")
@SecurityRequirement(name = "Bearer Authentication")
class StudentController(
    private val studentService: StudentService
) {

    @GetMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Get all students", description = "Retrieve a paginated list of all students")
    fun getAllStudents(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "id") sort: String,
        @RequestParam(defaultValue = "ASC") order: String
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val students = studentService.getAllStudents(page, limit, sort, order)
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @GetMapping("/{id}")
    @RequireStaffOrGuardian
    @Operation(summary = "Get student by ID", description = "Retrieve a specific student with full details and course history")
    fun getStudentById(@PathVariable id: Long): ResponseEntity<ApiResponse<com.sigep.students.application.dto.StudentDetailDto>> {
        val student = studentService.getStudentDetailById(id)
        return ResponseEntity.ok(ApiResponse.success(student))
    }

    @GetMapping("/search")
    @RequireAdminOrTeacher
    @Operation(summary = "Search students", description = "Search students by name, email or document number")
    fun searchStudents(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val students = studentService.searchStudents(query, page, limit)
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @GetMapping("/guardian/{guardianId}")
    @RequireStaffOrGuardian
    @Operation(summary = "Get students by guardian", description = "Retrieve all students assigned to a specific guardian")
    fun getStudentsByGuardian(
        @PathVariable guardianId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val students = studentService.getStudentsByGuardian(guardianId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @PostMapping
    @RequireAdmin
    @Operation(summary = "Create student", description = "Create a new student (Admin only)")
    fun createStudent(@Valid @RequestBody request: CreateStudentRequest): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.createStudent(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(student, "Student created successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Update student", description = "Update student information (Admin only)")
    fun updateStudent(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateStudentRequest
    ): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.updateStudent(id, request)
        return ResponseEntity.ok(ApiResponse.success(student, "Student updated successfully"))
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Delete student", description = "Delete a student (Admin only)")
    fun deleteStudent(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        studentService.deleteStudent(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Student deleted successfully"))
    }

    @GetMapping("/{id}/courses")
    @RequireStaffOrGuardian
    @Operation(summary = "Get student courses", description = "Redirect to enrollment history endpoint")
    fun getStudentCourses(@PathVariable id: Long): ResponseEntity<ApiResponse<Any>> {
        // Esta funcionalidad se implementa a través de /api/v1/enrollments/student/{id}/history
        return ResponseEntity.ok(ApiResponse.success(
            mapOf("redirectTo" to "/api/v1/enrollments/student/$id/history"),
            "Use enrollments endpoint for course history"
        ))
    }
}
