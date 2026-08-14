package com.sigep.scheduling.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.scheduling.application.dto.*
import com.sigep.scheduling.application.service.ClassroomService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/classrooms")
@Tag(name = "Classrooms", description = "Classroom management for scheduling")
class ClassroomController(private val classroomService: ClassroomService) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getAllClassrooms(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) size: Int?,
        @RequestParam(defaultValue = "false") activeOnly: Boolean
    ): ResponseEntity<ApiResponse<Any>> {
        val pageSize = limit ?: size ?: 20
        return if (activeOnly) {
            val classrooms = classroomService.getActiveClassrooms()
            ResponseEntity.ok(ApiResponse.success(classrooms))
        } else {
            val result = classroomService.getAllClassrooms(page, pageSize)
            ResponseEntity.ok(ApiResponse.success(result))
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getClassroomById(@PathVariable id: Long): ResponseEntity<ApiResponse<ClassroomDto>> {
        return ResponseEntity.ok(ApiResponse.success(classroomService.getClassroomById(id)))
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createClassroom(@Valid @RequestBody request: CreateClassroomRequest): ResponseEntity<ApiResponse<ClassroomDto>> {
        val created = classroomService.createClassroom(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Classroom created successfully"))
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateClassroom(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateClassroomRequest
    ): ResponseEntity<ApiResponse<ClassroomDto>> {
        return ResponseEntity.ok(ApiResponse.success(classroomService.updateClassroom(id, request), "Classroom updated"))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteClassroom(@PathVariable id: Long): ResponseEntity<ApiResponse<ClassroomDto>> {
        return ResponseEntity.ok(ApiResponse.success(classroomService.softDeleteClassroom(id), "Classroom deactivated"))
    }
}
