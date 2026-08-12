package com.sigep.students.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.students.application.dto.CreateStudentRequest
import com.sigep.students.application.dto.GuardianStudentRegistrationRequest
import com.sigep.students.application.dto.StudentDto
import com.sigep.students.application.dto.StudentIdentityMatchDto
import com.sigep.students.application.dto.StudentIdentityMatchRequest
import com.sigep.students.application.dto.LinkStudentGuardianRequest
import com.sigep.students.application.dto.UpdateStudentRequest
import com.sigep.students.application.service.StudentService
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.security.application.annotation.RequireAdminOrGuardian
import com.sigep.security.application.annotation.RequireGuardian
import com.sigep.security.application.annotation.RequireStaffOrGuardian
import com.sigep.students.application.dto.StudentDetailDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.multipart.MultipartFile
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
        @RequestParam(defaultValue = "ASC") order: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val actorUserId = httpRequest.getAttribute("userId") as? Long
        val actorRole = httpRequest.getAttribute("userRole") as? String
        val students = if (actorRole == "TEACHER") {
            studentService.getStudentsForTeacher(requireActorUserId(actorUserId), page, limit, sort, order)
        } else {
            studentService.getAllStudents(page, limit, sort, order)
        }
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @GetMapping("/{id}")
    @RequireStaffOrGuardian
    @Operation(summary = "Get student by ID", description = "Retrieve a specific student with full details and course history")
    fun getStudentById(
        @PathVariable id: Long,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StudentDetailDto>> {
        studentService.assertCanAccessStudent(
            id,
            httpRequest.getAttribute("userId") as? Long,
            httpRequest.getAttribute("userRole") as? String
        )
        val student = studentService.getStudentDetailById(id)
        return ResponseEntity.ok(ApiResponse.success(student))
    }

    @GetMapping("/search")
    @RequireAdminOrTeacher
    @Operation(summary = "Search students", description = "Search students by name, email or document number")
    fun searchStudents(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val actorUserId = httpRequest.getAttribute("userId") as? Long
        val actorRole = httpRequest.getAttribute("userRole") as? String
        val students = if (actorRole == "TEACHER") {
            studentService.searchStudentsForTeacher(requireActorUserId(actorUserId), query, page, limit)
        } else {
            studentService.searchStudents(query, page, limit)
        }
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @GetMapping("/guardian/{guardianId}")
    @RequireStaffOrGuardian
    @Operation(summary = "Get students by guardian", description = "Retrieve all students assigned to a specific guardian")
    fun getStudentsByGuardian(
        @PathVariable guardianId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val actorUserId = httpRequest.getAttribute("userId") as? Long
        val actorRole = httpRequest.getAttribute("userRole") as? String
        if (actorRole == "GUARDIAN" && actorUserId != guardianId) {
            throw ForbiddenException("No puede consultar estudiantes de otro tutor")
        }
        if (actorRole == "TEACHER") {
            throw ForbiddenException("Los docentes deben consultar sus estudiantes asignados")
        }
        val students = studentService.getStudentsByGuardian(guardianId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(students))
    }

    @PostMapping
    @RequireAdmin
    @Operation(summary = "Create student", description = "Create a new student (Admin only)")
    fun createStudent(
        @Valid @RequestBody request: CreateStudentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.createStudent(request, requireActorUserId(httpRequest.getAttribute("userId") as? Long))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(student, "Student created successfully"))
    }

    @PostMapping("/self-registration")
    @RequireGuardian
    @Operation(summary = "Create student as guardian", description = "Create a student linked to the authenticated guardian")
    fun createStudentAsGuardian(
        @Valid @RequestBody request: GuardianStudentRegistrationRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StudentDto>> {
        val guardianId = httpRequest.getAttribute("userId") as? Long
            ?: throw UnauthorizedException("Token inválido o sin userId")

        val student = studentService.createStudentForGuardian(guardianId, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(student, "Student self-registration created successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Update student", description = "Update student information (Admin only)")
    fun updateStudent(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateStudentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.updateStudent(id, request, requireActorUserId(httpRequest.getAttribute("userId") as? Long))
        return ResponseEntity.ok(ApiResponse.success(student, "Student updated successfully"))
    }

    @PostMapping("/identity-match")
    @RequireAdminOrGuardian
    @Operation(summary = "Match student identity", description = "Safely checks whether a student identity can be reused by the authenticated actor")
    fun matchStudentIdentity(
        @Valid @RequestBody request: StudentIdentityMatchRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StudentIdentityMatchDto>> {
        val actorUserId = requireActorUserId(httpRequest.getAttribute("userId") as? Long)
        val actorRole = httpRequest.getAttribute("userRole") as? String
            ?: throw UnauthorizedException("Token invalid or missing userRole")
        return ResponseEntity.ok(ApiResponse.success(studentService.matchIdentity(actorUserId, actorRole, request)))
    }

    @PutMapping("/{id}/guardian")
    @RequireAdmin
    @Operation(summary = "Link guardian", description = "Links or reassigns the single current guardian with an audit event")
    fun linkGuardian(
        @PathVariable id: Long,
        @Valid @RequestBody request: LinkStudentGuardianRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StudentDto>> {
        val actorUserId = requireActorUserId(httpRequest.getAttribute("userId") as? Long)
        return ResponseEntity.ok(
            ApiResponse.success(
                studentService.linkGuardian(id, request.guardianId, actorUserId, request.reason),
                "Guardian linked successfully"
            )
        )
    }

    @PostMapping("/{id}/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @RequireAdmin
    @Operation(summary = "Upload student profile photo", description = "Upload and persist student photo URL")
    fun uploadStudentPhoto(
        @PathVariable id: Long,
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.uploadStudentPhoto(id, file)
        return ResponseEntity.ok(ApiResponse.success(student, "Student photo uploaded successfully"))
    }

    @GetMapping("/{id}/photo")
    @RequireStaffOrGuardian
    @Operation(summary = "Get student profile photo", description = "Download student photo if available")
    fun getStudentPhoto(@PathVariable id: Long, httpRequest: HttpServletRequest): ResponseEntity<InputStreamResource> {
        studentService.assertCanAccessStudent(
            id,
            httpRequest.getAttribute("userId") as? Long,
            httpRequest.getAttribute("userRole") as? String
        )
        val photoFile = studentService.getStudentPhotoFile(id)
        val contentType = when (photoFile.extension.lowercase()) {
            "png" -> MediaType.IMAGE_PNG
            "webp" -> MediaType.parseMediaType("image/webp")
            else -> MediaType.IMAGE_JPEG
        }

        return ResponseEntity.ok()
            .contentType(contentType)
            .body(InputStreamResource(photoFile.inputStream()))
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
    fun getStudentCourses(@PathVariable id: Long, httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<Any>> {
        studentService.assertCanAccessStudent(
            id,
            httpRequest.getAttribute("userId") as? Long,
            httpRequest.getAttribute("userRole") as? String
        )
        // Esta funcionalidad se implementa a través de /api/v1/enrollments/student/{id}/history
        return ResponseEntity.ok(ApiResponse.success(
            mapOf("redirectTo" to "/api/v1/enrollments/student/$id/history"),
            "Use enrollments endpoint for course history"
        ))
    }

    @GetMapping("/{id}/payment-status")
    @RequireStaffOrGuardian
    @Operation(
        summary = "Get student payment status",
        description = "Retrieve payment status for a specific student. Currently returns mock data until payments module is implemented."
    )
    fun getStudentPaymentStatus(@PathVariable id: Long, httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<com.sigep.students.application.dto.StudentPaymentStatusDto>> {
        studentService.assertCanAccessStudent(
            id,
            httpRequest.getAttribute("userId") as? Long,
            httpRequest.getAttribute("userRole") as? String
        )
        val paymentStatus = studentService.getStudentPaymentStatus(id)
        return ResponseEntity.ok(ApiResponse.success(paymentStatus))
    }

    private fun requireActorUserId(actorUserId: Long?): Long =
        actorUserId ?: throw UnauthorizedException("Token invalido o sin userId")
}
