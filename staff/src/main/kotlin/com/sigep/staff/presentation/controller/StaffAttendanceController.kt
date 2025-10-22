package com.sigep.staff.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.staff.application.dto.CreateAttendanceRequest
import com.sigep.staff.application.dto.StaffAttendanceDto
import com.sigep.staff.application.dto.UpdateAttendanceRequest
import com.sigep.staff.application.service.StaffAttendanceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/staff/attendance")
@Tag(name = "Staff Attendance", description = "API for managing staff attendance and work hours")
@SecurityRequirement(name = "Bearer Authentication")
class StaffAttendanceController(
    private val attendanceService: StaffAttendanceService
) {

    @PostMapping
    @RequireAdmin
    @Operation(summary = "Register attendance", description = "Register attendance record for teaching or non-teaching staff")
    fun createAttendance(
        @Valid @RequestBody request: CreateAttendanceRequest
    ): ResponseEntity<ApiResponse<StaffAttendanceDto>> {
        val attendance = attendanceService.createAttendance(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(attendance, "Attendance registered successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Update attendance", description = "Update an attendance record")
    fun updateAttendance(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateAttendanceRequest
    ): ResponseEntity<ApiResponse<StaffAttendanceDto>> {
        val attendance = attendanceService.updateAttendance(id, request)
        return ResponseEntity.ok(ApiResponse.success(attendance, "Attendance updated successfully"))
    }

    @GetMapping("/teaching/{staffId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get teaching staff attendance", description = "Retrieve attendance records for a teaching staff member within a date range")
    fun getTeachingStaffAttendance(
        @PathVariable staffId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<StaffAttendanceDto>>> {
        val attendance = attendanceService.getTeachingStaffAttendance(staffId, startDate, endDate, page, limit)
        return ResponseEntity.ok(ApiResponse.success(attendance))
    }

    @GetMapping("/non-teaching/{staffId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get non-teaching staff attendance", description = "Retrieve attendance records for a non-teaching staff member within a date range")
    fun getNonTeachingStaffAttendance(
        @PathVariable staffId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<StaffAttendanceDto>>> {
        val attendance = attendanceService.getNonTeachingStaffAttendance(staffId, startDate, endDate, page, limit)
        return ResponseEntity.ok(ApiResponse.success(attendance))
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    @Operation(summary = "Delete attendance", description = "Delete an attendance record")
    fun deleteAttendance(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        attendanceService.deleteAttendance(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Attendance deleted successfully"))
    }
}

