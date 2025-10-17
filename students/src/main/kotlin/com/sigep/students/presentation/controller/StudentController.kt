package com.sigep.students.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.students.application.dto.CreateStudentRequest
import com.sigep.students.application.dto.StudentDto
import com.sigep.students.application.dto.UpdateStudentRequest
import com.sigep.students.application.service.StudentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/students")
class StudentController(
    private val studentService: StudentService
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'GUARDIAN')")
    fun getStudentById(@PathVariable id: Long): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.getStudentById(id)
        return ResponseEntity.ok(ApiResponse.success(student))
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun searchStudents(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val students = studentService.searchStudents(query, page, limit)
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @GetMapping("/guardian/{guardianId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'GUARDIAN')")
    fun getStudentsByGuardian(
        @PathVariable guardianId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val students = studentService.getStudentsByGuardian(guardianId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createStudent(@Valid @RequestBody request: CreateStudentRequest): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.createStudent(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(student, "Student created successfully"))
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateStudent(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateStudentRequest
    ): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.updateStudent(id, request)
        return ResponseEntity.ok(ApiResponse.success(student, "Student updated successfully"))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteStudent(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        studentService.deleteStudent(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Student deleted successfully"))
    }
}
