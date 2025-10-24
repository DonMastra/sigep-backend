package com.sigep.courses.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.service.CourseSessionService
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Course Sessions", description = "Course sessions and scheduling management")
class CourseSessionController(
    private val sessionService: CourseSessionService
) {

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get session by ID")
    fun getSessionById(@PathVariable id: Long): ResponseEntity<ApiResponse<CourseSessionDto>> {
        val session = sessionService.getSessionById(id)
        return ResponseEntity.ok(ApiResponse.success(session))
    }

    @GetMapping("/course/{courseId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get sessions by course")
    fun getSessionsByCourse(
        @PathVariable courseId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CourseSessionDto>>> {
        val sessions = sessionService.getSessionsByCourse(courseId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(sessions))
    }

    @GetMapping("/course/{courseId}/range")
    @RequireAdminOrTeacher
    @Operation(summary = "Get sessions by date range")
    fun getSessionsByDateRange(
        @PathVariable courseId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ResponseEntity<ApiResponse<List<CourseSessionDto>>> {
        val sessions = sessionService.getSessionsByDateRange(courseId, startDate, endDate)
        return ResponseEntity.ok(ApiResponse.success(sessions))
    }

    @PostMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Create a session")
    fun createSession(@Valid @RequestBody request: CreateSessionRequest): ResponseEntity<ApiResponse<CourseSessionDto>> {
        val session = sessionService.createSession(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(session, "Session created successfully"))
    }

    @PostMapping("/recurring")
    @RequireAdminOrTeacher
    @Operation(summary = "Generate recurring sessions")
    fun generateRecurringSessions(
        @Valid @RequestBody request: GenerateRecurringSessionsRequest
    ): ResponseEntity<ApiResponse<List<CourseSessionDto>>> {
        val sessions = sessionService.generateRecurringSessions(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(sessions, "${sessions.size} sessions generated successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Update session")
    fun updateSession(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateSessionRequest
    ): ResponseEntity<ApiResponse<CourseSessionDto>> {
        val session = sessionService.updateSession(id, request)
        return ResponseEntity.ok(ApiResponse.success(session, "Session updated successfully"))
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Delete session")
    fun deleteSession(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        sessionService.deleteSession(id)
        return ResponseEntity.ok(ApiResponse.success(Unit, "Session deleted successfully"))
    }

    @PostMapping("/exceptions")
    @RequireAdminOrTeacher
    @Operation(summary = "Create session exception")
    fun createException(
        @Valid @RequestBody request: CreateSessionExceptionRequest
    ): ResponseEntity<ApiResponse<SessionExceptionDto>> {
        val exception = sessionService.createException(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(exception, "Exception created successfully"))
    }

    @PostMapping("/check-conflicts")
    @RequireAdminOrTeacher
    @Operation(summary = "Check for scheduling conflicts")
    fun checkConflicts(@Valid @RequestBody request: ConflictCheckRequest): ResponseEntity<ApiResponse<ConflictDto>> {
        val result = sessionService.checkConflictsForRequest(request)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/{id}/attendance-summary")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance summary for a session")
    fun getSessionAttendanceSummary(@PathVariable id: Long): ResponseEntity<ApiResponse<SessionAttendanceSummaryDto>> {
        val summary = sessionService.getSessionAttendanceSummary(id)
        return ResponseEntity.ok(ApiResponse.success(summary))
    }

    @GetMapping("/calendar")
    @RequireAdminOrTeacher
    @Operation(summary = "Get session calendar")
    fun getCalendar(
        @RequestParam(required = false) courseId: Long?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ResponseEntity<ApiResponse<List<SessionCalendarDto>>> {
        val calendar = sessionService.getCalendar(courseId, startDate, endDate)
        return ResponseEntity.ok(ApiResponse.success(calendar))
    }
}

