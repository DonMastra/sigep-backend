package com.sigep.courses.application.dto

import com.sigep.courses.domain.model.CourseStatus
import com.sigep.courses.domain.model.DayOfWeek
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CourseDto(
    val id: Long,
    val name: String,
    val description: String,
    val level: String,
    val duration: Int,
    val maxStudents: Int,
    val teacherId: Long,
    val status: CourseStatus,
    val schedules: List<CourseScheduleDto>,
    val enrolledStudents: Int, // Cantidad de estudiantes inscritos
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CourseScheduleDto(
    val id: Long?,
    val dayOfWeek: DayOfWeek,
    val startTime: String,
    val endTime: String
)

data class CreateCourseRequest(
    @field:NotBlank(message = "Course name is required")
    @field:Size(min = 3, max = 200)
    val name: String,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 10, max = 1000)
    val description: String,

    @field:NotBlank(message = "Level is required")
    val level: String,

    @field:NotNull(message = "Duration is required")
    @field:Min(1)
    val duration: Int,

    @field:NotNull(message = "Max students is required")
    @field:Min(1)
    val maxStudents: Int,

    @field:NotNull(message = "Teacher ID is required")
    val teacherId: Long,

    val schedules: List<CreateCourseScheduleRequest> = emptyList()
)

data class CreateCourseScheduleRequest(
    @field:NotNull(message = "Day of week is required")
    val dayOfWeek: DayOfWeek,

    @field:NotBlank(message = "Start time is required")
    val startTime: String, // Format: HH:mm

    @field:NotBlank(message = "End time is required")
    val endTime: String // Format: HH:mm
)

data class UpdateCourseRequest(
    @field:Size(min = 3, max = 200)
    val name: String?,

    @field:Size(min = 10, max = 1000)
    val description: String?,

    val level: String?,

    @field:Min(1)
    val duration: Int?,

    @field:Min(1)
    val maxStudents: Int?,

    val teacherId: Long?,

    val status: CourseStatus?
)

data class EnrollStudentRequest(
    @field:NotNull(message = "Student ID is required")
    val studentId: Long,

    val notes: String? = null
)

