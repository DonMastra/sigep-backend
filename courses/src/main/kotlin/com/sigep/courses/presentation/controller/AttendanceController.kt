package com.sigep.courses.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.service.AttendanceService
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.security.application.annotation.RequireStaffOrGuardian
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
@RequestMapping("/api/v1/attendance")
@Tag(name = "Attendance", description = "Course attendance management")
class AttendanceController(
    private val attendanceService: AttendanceService
) {

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance by ID")
    fun getAttendanceById(@PathVariable id: Long): ResponseEntity<ApiResponse<AttendanceDto>> {
        val attendance = attendanceService.getAttendanceById(id)
        return ResponseEntity.ok(ApiResponse.success(attendance))
    }

    @GetMapping("/enrollment/{enrollmentId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance by enrollment")
    fun getAttendanceByEnrollment(
        @PathVariable enrollmentId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<AttendanceDto>>> {
        val attendances = attendanceService.getAttendanceByEnrollment(enrollmentId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(attendances))
    }

    @GetMapping("/course/{courseId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance by course")
    fun getAttendanceByCourse(
        @PathVariable courseId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<AttendanceDto>>> {
        val attendances = attendanceService.getAttendanceByCourse(courseId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(attendances))
    }

    @GetMapping("/student/{studentId}")
    @RequireStaffOrGuardian
    @Operation(summary = "Get attendance by student")
    fun getAttendanceByStudent(
        @PathVariable studentId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PageResponse<AttendanceDto>>> {
        val attendances = attendanceService.getAttendanceByStudent(
            studentId,
            page,
            limit,
            extractUserId(httpRequest),
            httpRequest.getAttribute("userRole") as? String
        )
        return ResponseEntity.ok(ApiResponse.success(attendances))
    }

    @GetMapping("/course/{courseId}/date/{date}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance by course and date")
    fun getAttendanceByCourseAndDate(
        @PathVariable courseId: Long,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<ApiResponse<List<AttendanceDto>>> {
        val attendances = attendanceService.getAttendanceByCourseAndDate(courseId, date)
        return ResponseEntity.ok(ApiResponse.success(attendances))
    }

    @PostMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Record attendance")
    fun recordAttendance(
        @Valid @RequestBody request: CreateAttendanceRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AttendanceDto>> {
        val recordedBy = extractUserId(httpRequest)
        val attendance = attendanceService.recordAttendance(request, recordedBy)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(attendance, "Attendance recorded successfully"))
    }

    @PostMapping("/bulk")
    @RequireAdminOrTeacher
    @Operation(summary = "Record bulk attendance for a course")
    fun recordBulkAttendance(
        @Valid @RequestBody request: BulkAttendanceRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<AttendanceDto>>> {
        val recordedBy = extractUserId(httpRequest)
        val attendances = attendanceService.recordBulkAttendance(request, recordedBy)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(attendances, "Bulk attendance recorded successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Update attendance")
    fun updateAttendance(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateAttendanceRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AttendanceDto>> {
        val recordedBy = extractUserId(httpRequest)
        val attendance = attendanceService.updateAttendance(id, request, recordedBy)
        return ResponseEntity.ok(ApiResponse.success(attendance, "Attendance updated successfully"))
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Delete attendance")
    fun deleteAttendance(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        attendanceService.deleteAttendance(id)
        return ResponseEntity.ok(ApiResponse.success(Unit, "Attendance deleted successfully"))
    }

    @GetMapping("/enrollment/{enrollmentId}/statistics")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance statistics for an enrollment")
    fun getAttendanceStatistics(@PathVariable enrollmentId: Long): ResponseEntity<ApiResponse<AttendanceStatisticsDto>> {
        val statistics = attendanceService.getAttendanceStatistics(enrollmentId)
        return ResponseEntity.ok(ApiResponse.success(statistics))
    }

    @GetMapping("/course/{courseId}/statistics")
    @RequireAdminOrTeacher
    @Operation(summary = "Get cumulative attendance statistics for a course and its enrollments")
    fun getCourseAttendanceStatistics(
        @PathVariable courseId: Long
    ): ResponseEntity<ApiResponse<CourseAttendanceStatisticsDto>> {
        val statistics = attendanceService.getCourseAttendanceStatistics(courseId)
        return ResponseEntity.ok(ApiResponse.success(statistics))
    }

    @GetMapping("/course/{courseId}/report/{date}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance report for a course on a specific date")
    fun getCourseAttendanceReport(
        @PathVariable courseId: Long,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<ApiResponse<CourseAttendanceReportDto>> {
        val report = attendanceService.getCourseAttendanceReport(courseId, date)
        return ResponseEntity.ok(ApiResponse.success(report))
    }

    @PostMapping("/enrollment/{enrollmentId}/range")
    @RequireAdminOrTeacher
    @Operation(summary = "Get attendance by date range")
    fun getAttendanceByDateRange(
        @PathVariable enrollmentId: Long,
        @Valid @RequestBody request: AttendanceRangeRequest
    ): ResponseEntity<ApiResponse<List<AttendanceDto>>> {
        val attendances = attendanceService.getAttendanceByDateRange(enrollmentId, request.startDate, request.endDate)
        return ResponseEntity.ok(ApiResponse.success(attendances))
    }

    private fun extractUserId(httpRequest: HttpServletRequest): Long {
        return httpRequest.getAttribute("userId") as? Long
            ?: throw UnauthorizedException("Token inválido o sin userId")
    }
}

