package com.sigep.courses.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.service.CourseService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "Courses", description = "API for managing courses available in the Institute")
class CourseController(
    private val courseService: CourseService
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'GUARDIAN')")
    fun getAllCourses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "id") sort: String,
        @RequestParam(defaultValue = "ASC") order: String
    ): ResponseEntity<ApiResponse<PageResponse<CourseDto>>> {
        val courses = courseService.getAllCourses(page, limit, sort, order)
        return ResponseEntity.ok(ApiResponse.success(courses))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'GUARDIAN')")
    fun getCourseById(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseDto>> {
        val course = courseService.getCourseById(id)
        return ResponseEntity.ok(ApiResponse.success(course))
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun searchCourses(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CourseDto>>> {
        val courses = courseService.searchCourses(query, page, limit)
        return ResponseEntity.ok(ApiResponse.success(courses))
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun getCoursesByTeacher(
        @PathVariable teacherId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CourseDto>>> {
        val courses = courseService.getCoursesByTeacher(teacherId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(courses))
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createCourse(@Valid @RequestBody request: CreateCourseRequest): ResponseEntity<ApiResponse<CourseDto>> {
        val course = courseService.createCourse(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(course, "Course created successfully"))
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateCourse(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCourseRequest
    ): ResponseEntity<ApiResponse<CourseDto>> {
        val course = courseService.updateCourse(id, request)
        return ResponseEntity.ok(ApiResponse.success(course, "Course updated successfully"))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteCourse(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        courseService.deleteCourse(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Course deleted successfully"))
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun enrollStudent(
        @PathVariable id: Long,
        @Valid @RequestBody request: EnrollStudentRequest
    ): ResponseEntity<ApiResponse<EnrollmentDto>> {
        val enrollment = courseService.enrollStudent(id, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(enrollment, "Student enrolled successfully"))
    }

    @PostMapping("/filter")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    fun filterCourses(
        @RequestBody filter: CourseFilterRequest,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CourseDto>>> {
        val courses = courseService.filterCourses(filter, page, limit)
        return ResponseEntity.ok(ApiResponse.success(courses))
    }

    @GetMapping("/published")
    fun getPublishedCourses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CourseSimpleDto>>> {
        val courses = courseService.getPublishedCourses(page, limit)
        return ResponseEntity.ok(ApiResponse.success(courses))
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    fun getCourseStatistics(): ResponseEntity<ApiResponse<CourseStatisticsDto>> {
        val statistics = courseService.getCourseStatistics()
        return ResponseEntity.ok(ApiResponse.success(statistics))
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    fun publishCourse(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseDto>> {
        val course = courseService.publishCourse(id)
        return ResponseEntity.ok(ApiResponse.success(course, "Course published successfully"))
    }

    @PutMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    fun unpublishCourse(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseDto>> {
        val course = courseService.unpublishCourse(id)
        return ResponseEntity.ok(ApiResponse.success(course, "Course unpublished successfully"))
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    fun activateCourse(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseDto>> {
        val course = courseService.activateCourse(id)
        return ResponseEntity.ok(ApiResponse.success(course, "Course activated successfully"))
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    fun deactivateCourse(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseDto>> {
        val course = courseService.deactivateCourse(id)
        return ResponseEntity.ok(ApiResponse.success(course, "Course deactivated successfully"))
    }
}

