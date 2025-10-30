package com.sigep.courses.application.dto

import com.sigep.courses.domain.model.SessionStatus
import com.sigep.courses.domain.model.ExceptionType
import jakarta.validation.constraints.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class CourseSessionDto(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val sessionDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val classroomId: Long?,
    val classroomName: String?,
    val status: SessionStatus,
    val topic: String?,
    val notes: String?,
    val isRecurring: Boolean,
    val recurrenceRule: String?,
    val attendanceCount: Int? = null, // Number of students who attended
    val expectedAttendance: Int? = null, // Number of enrolled students
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateSessionRequest(
    @field:NotNull(message = "Course ID is required")
    val courseId: Long,

    @field:NotNull(message = "Session date is required")
    val sessionDate: LocalDate,

    @field:NotNull(message = "Start time is required")
    val startTime: LocalTime,

    @field:NotNull(message = "End time is required")
    val endTime: LocalTime,

    val classroomId: Long? = null,

    val classroomName: String? = null,

    @field:Size(max = 1000)
    val topic: String? = null,

    @field:Size(max = 1000)
    val notes: String? = null,

    val isRecurring: Boolean = false,

    val recurrenceRule: String? = null // RRULE format
)

data class UpdateSessionRequest(
    val sessionDate: LocalDate?,

    val startTime: LocalTime?,

    val endTime: LocalTime?,

    val classroomId: Long?,

    val classroomName: String?,

    val status: SessionStatus?,

    @field:Size(max = 1000)
    val topic: String?,

    @field:Size(max = 1000)
    val notes: String?
)

data class SessionExceptionDto(
    val id: Long,
    val sessionId: Long?,
    val exceptionDate: LocalDate,
    val exceptionType: ExceptionType,
    val newStartTime: LocalTime?,
    val newEndTime: LocalTime?,
    val newClassroomId: Long?,
    val reason: String?,
    val createdAt: LocalDateTime
)

data class CreateSessionExceptionRequest(
    @field:NotNull(message = "Session ID is required")
    val sessionId: Long,

    @field:NotNull(message = "Exception date is required")
    val exceptionDate: LocalDate,

    @field:NotNull(message = "Exception type is required")
    val exceptionType: ExceptionType,

    val newStartTime: LocalTime? = null,

    val newEndTime: LocalTime? = null,

    val newClassroomId: Long? = null,

    @field:Size(max = 500)
    val reason: String? = null
)

data class ConflictCheckRequest(
    val courseId: Long? = null,
    val teacherId: Long? = null,
    val studentId: Long? = null,
    val classroomId: Long? = null,

    @field:NotNull(message = "Date is required")
    val date: LocalDate,

    @field:NotNull(message = "Start time is required")
    val startTime: LocalTime,

    @field:NotNull(message = "End time is required")
    val endTime: LocalTime
)

data class ConflictDto(
    val hasConflict: Boolean,
    val conflictType: String, // TEACHER, STUDENT, CLASSROOM
    val conflicts: List<CourseSessionDto>,
    val message: String
)

data class SessionCalendarDto(
    val date: LocalDate,
    val sessions: List<CourseSessionDto>,
    val totalSessions: Int,
    val completedSessions: Int,
    val cancelledSessions: Int
)

data class GenerateRecurringSessionsRequest(
    @field:NotNull(message = "Course ID is required")
    val courseId: Long,

    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate,

    @field:NotNull(message = "End date is required")
    val endDate: LocalDate,

    @field:NotNull(message = "Start time is required")
    val startTime: LocalTime,

    @field:NotNull(message = "End time is required")
    val endTime: LocalTime,

    @field:NotEmpty(message = "At least one day of week is required")
    val daysOfWeek: List<java.time.DayOfWeek>, // MONDAY, TUESDAY, etc.

    val classroomId: Long? = null,

    val classroomName: String? = null,

    @field:Size(max = 1000)
    val topic: String? = null
)

data class SessionAttendanceSummaryDto(
    val sessionId: Long,
    val sessionDate: LocalDate,
    val courseName: String,
    val totalEnrolled: Int,
    val present: Int,
    val absent: Int,
    val late: Int,
    val attendanceRate: Double
)

data class ICSExportRequest(
    val courseId: Long? = null,
    val studentId: Long? = null,
    val teacherId: Long? = null,
    val startDate: LocalDate,
    val endDate: LocalDate
)

