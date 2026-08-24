package com.sigep.courses.application.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.sigep.courses.domain.model.AttendanceStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime

data class AttendanceDto(
    val id: Long,
    val enrollmentId: Long,
    val courseSessionId: Long?,
    val studentId: Long,
    val studentName: String? = null,
    val courseId: Long,
    val courseName: String,
    val attendanceDate: LocalDate,
    val status: AttendanceStatus,
    val notes: String?,
    val recordedBy: Long,
    val recordedByName: String? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateAttendanceRequest(
    @field:NotNull(message = "Enrollment ID is required")
    val enrollmentId: Long,

    val courseSessionId: Long? = null,

    @field:NotNull(message = "Attendance date is required")
    val attendanceDate: LocalDate,

    @field:NotNull(message = "Status is required")
    val status: AttendanceStatus,

    @field:Size(max = 500, message = "Notes cannot exceed 500 characters")
    val notes: String? = null
)

data class UpdateAttendanceRequest(
    val status: AttendanceStatus?,

    @field:Size(max = 500, message = "Notes cannot exceed 500 characters")
    val notes: String?
)

data class BulkAttendanceRequest(
    val courseSessionId: Long? = null,

    val courseId: Long? = null,

    @field:NotNull(message = "Attendance date is required")
    @field:JsonAlias("attendanceDate")
    val date: LocalDate,

    @field:NotEmpty(message = "Attendance records cannot be empty")
    @field:JsonAlias("attendances")
    val records: List<StudentAttendanceRecord>
)

data class StudentAttendanceRecord(
    @field:NotNull(message = "Enrollment ID is required")
    val enrollmentId: Long,

    @field:NotNull(message = "Status is required")
    val status: AttendanceStatus,

    val notes: String? = null
)

data class AttendanceStatisticsDto(
    val enrollmentId: Long,
    val studentId: Long,
    val studentName: String? = null,
    val totalClasses: Long,
    val present: Long,
    val absent: Long,
    val late: Long,
    val excusedAbsence: Long,
    val sickLeave: Long,
    val attendanceRate: Double // Percentage (present + late) / total
)

data class CourseAttendanceStatisticsDto(
    val courseId: Long,
    val totalRecords: Long,
    val present: Long,
    val absent: Long,
    val late: Long,
    val excusedAbsence: Long,
    val sickLeave: Long,
    val attendanceRate: Double,
    val students: List<AttendanceStatisticsDto>
)

data class CourseAttendanceReportDto(
    val courseId: Long,
    val courseName: String,
    val date: LocalDate,
    val totalEnrolled: Int,
    val totalPresent: Int,
    val totalAbsent: Int,
    val attendanceRate: Double,
    val attendances: List<AttendanceDto>
)

data class AttendanceRangeRequest(
    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate,

    @field:NotNull(message = "End date is required")
    val endDate: LocalDate
)

