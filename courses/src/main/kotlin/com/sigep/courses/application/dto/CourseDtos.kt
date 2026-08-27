package com.sigep.courses.application.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sigep.common.application.service.ReservationInfo
import com.sigep.courses.domain.model.CourseStatus
import com.sigep.courses.domain.model.CourseLevel
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CourseDto(
    val id: Long,
    val code: String,
    val name: String,
    val description: String,
    val level: CourseLevel,
    val duration: Int,
    val maxStudents: Int,
    val minStudents: Int,
    val teacherId: Long?,
    val teacherName: String? = null,
    val price: BigDecimal,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val status: CourseStatus,
    val isPublished: Boolean,
    val hasReservation: Boolean,
    val reservationSummary: ReservationInfo? = null,
    val reservationSummaries: List<ReservationInfo> = emptyList(),
    val enrolledStudents: Int,
    val totalEnrollments: Int,
    val availableSeats: Int,
    val isEnrollmentOpen: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateCourseRequest(
    @field:NotBlank(message = "Course code is required")
    @field:Size(min = 1, max = 50)
    @field:Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "Code may contain letters, numbers, spaces, hyphens, underscores and dots")
    val code: String,

    @field:NotBlank(message = "Course name is required")
    @field:Size(min = 3, max = 200)
    val name: String,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 10, max = 1000)
    val description: String,

    @field:NotNull(message = "Level is required")
    val level: CourseLevel,

    @field:NotNull(message = "Duration is required")
    @field:Min(value = 1, message = "Duration must be at least 1 hour")
    @field:Max(value = 1000, message = "Duration cannot exceed 1000 hours")
    val duration: Int,

    @field:NotNull(message = "Max students is required")
    @field:Min(value = 1, message = "Max students must be at least 1")
    @field:Max(value = 100, message = "Max students cannot exceed 100")
    val maxStudents: Int,

    @field:Min(value = 1, message = "Min students must be at least 1")
    val minStudents: Int = 1,

    val teacherId: Long? = null,

    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.0", message = "Price must be positive")
    val price: BigDecimal,

    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val reservationId: Long? = null,
    val reservationIds: Set<Long> = emptySet(),
    val status: CourseStatus? = null,
    val isPublished: Boolean = false
)

data class UpdateCourseRequest(
    @field:Size(min = 1, max = 50)
    @field:Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "Code may contain letters, numbers, spaces, hyphens, underscores and dots")
    val code: String?,

    @field:Size(min = 3, max = 200)
    val name: String?,

    @field:Size(min = 10, max = 1000)
    val description: String?,

    val level: CourseLevel?,

    @field:Min(value = 1, message = "Duration must be at least 1 hour")
    @field:Max(value = 1000, message = "Duration cannot exceed 1000 hours")
    val duration: Int?,

    @field:Min(value = 1, message = "Max students must be at least 1")
    @field:Max(value = 100, message = "Max students cannot exceed 100")
    val maxStudents: Int?,

    @field:Min(value = 1, message = "Min students must be at least 1")
    val minStudents: Int?,

    val teacherId: Long?,

    @field:DecimalMin(value = "0.0", message = "Price must be positive")
    val price: BigDecimal?,

    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val reservationIds: Set<Long>? = null,
    val status: CourseStatus?,
    val isPublished: Boolean?
)

data class EnrollStudentRequest(
    @field:NotNull(message = "Student ID is required")
    val studentId: Long,

    val notes: String? = null
)

data class CourseStatisticsDto(
    val totalCourses: Long,
    val activeCourses: Long,
    val publishedCourses: Long,
    val totalEnrollments: Long,
    val averageEnrollmentRate: Double,
    val coursesByLevel: Map<CourseLevel, Long>,
    val coursesByStatus: Map<CourseStatus, Long>
)

data class CourseSimpleDto(
    val id: Long,
    val code: String,
    val name: String,
    val level: CourseLevel,
    val price: BigDecimal,
    val availableSeats: Int,
    val isEnrollmentOpen: Boolean,
    val hasReservation: Boolean
)

data class CourseFilterRequest(
    val level: CourseLevel? = null,
    val status: CourseStatus? = null,
    val teacherId: Long? = null,
    val isPublished: Boolean? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val hasAvailableSeats: Boolean? = null
)
