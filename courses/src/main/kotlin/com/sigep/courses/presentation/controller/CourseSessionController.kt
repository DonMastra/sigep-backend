package com.sigep.courses.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.service.CourseSessionService
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
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

    @GetMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Get all sessions")
    fun getAllSessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PageResponse<CourseSessionDto>>> =
        ResponseEntity.ok(ApiResponse.success(sessionService.getAllSessions(page, limit, actorUserId(httpRequest), actorRole(httpRequest))))

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get session by ID")
    fun getSessionById(@PathVariable id: Long, httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<CourseSessionDto>> {
        val session = sessionService.getSessionById(id, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity.ok(ApiResponse.success(session))
    }

    @GetMapping("/course/{courseId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get sessions by course")
    fun getSessionsByCourse(
        @PathVariable courseId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PageResponse<CourseSessionDto>>> {
        val sessions = sessionService.getSessionsByCourse(courseId, page, limit, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity.ok(ApiResponse.success(sessions))
    }

    @GetMapping("/course/{courseId}/range")
    @RequireAdminOrTeacher
    @Operation(summary = "Get sessions by date range")
    fun getSessionsByDateRange(
        @PathVariable courseId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<CourseSessionDto>>> {
        val sessions = sessionService.getSessionsByDateRange(courseId, startDate, endDate, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity.ok(ApiResponse.success(sessions))
    }

    @PostMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Create a session")
    fun createSession(@Valid @RequestBody request: CreateSessionRequest, httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<CourseSessionDto>> {
        val session = sessionService.createSession(request, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(session, "Session created successfully"))
    }

    @PostMapping("/recurring")
    @RequireAdminOrTeacher
    @Operation(summary = "Generate recurring sessions")
    fun generateRecurringSessions(
        @Valid @RequestBody request: GenerateRecurringSessionsRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<CourseSessionDto>>> {
        val sessions = sessionService.generateRecurringSessions(request, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(sessions, "${sessions.size} sessions generated successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Update session")
    fun updateSession(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateSessionRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<CourseSessionDto>> {
        val session = sessionService.updateSession(id, request, actorUserId(httpRequest), actorRole(httpRequest))
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
        @Valid @RequestBody request: CreateSessionExceptionRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<SessionExceptionDto>> {
        val exception = sessionService.createException(request, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(exception, "Exception created successfully"))
    }

    @PostMapping("/check-conflicts")
    @RequireAdminOrTeacher
    @Operation(summary = "Check for scheduling conflicts")
    fun checkConflicts(@Valid @RequestBody request: ConflictCheckRequest, httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<ConflictDto>> {
        val result = sessionService.checkConflictsForRequest(request, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @GetMapping("/{id}/attendance-summary")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance summary for a session")
    fun getSessionAttendanceSummary(@PathVariable id: Long, httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<SessionAttendanceSummaryDto>> {
        val summary = sessionService.getSessionAttendanceSummary(id, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity.ok(ApiResponse.success(summary))
    }

    @GetMapping("/calendar")
    @RequireAdminOrTeacher
    @Operation(summary = "Get session calendar")
    fun getCalendar(
        @RequestParam(required = false) courseId: Long?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<SessionCalendarDto>>> {
        val calendar = sessionService.getCalendar(courseId, startDate, endDate, actorUserId(httpRequest), actorRole(httpRequest))
        return ResponseEntity.ok(ApiResponse.success(calendar))
    }

    private fun actorUserId(request: HttpServletRequest): Long? = request.getAttribute("userId") as? Long

    private fun actorRole(request: HttpServletRequest): String? = request.getAttribute("userRole") as? String
}

