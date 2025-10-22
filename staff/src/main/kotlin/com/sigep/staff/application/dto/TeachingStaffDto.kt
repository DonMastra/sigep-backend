package com.sigep.staff.application.dto

import com.sigep.staff.domain.model.PaymentStatus
import java.time.LocalDate

data class TeachingStaffDto(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val documentNumber: String,
    val birthDate: LocalDate,
    val address: String,
    val hireDate: LocalDate,
    val monthlySalary: Double,
    val paymentStatus: PaymentStatus,
    val assignedStudentsCount: Int,
    val assignedCourses: List<CourseAssignmentDto>? = null,
    val specialization: String? = null,
    val observations: String? = null,
    val notes: String? = null,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val attendanceStats: AttendanceStatsDto? = null
)

data class CourseAssignmentDto(
    val courseId: Long,
    val courseName: String,
    val level: String,
    val enrolledStudents: Int
)

data class AttendanceStatsDto(
    val totalDays: Int,
    val presentDays: Int,
    val absentDays: Int,
    val lateDays: Int,
    val attendanceRate: Double
)

data class CreateTeachingStaffRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String,
    val documentNumber: String,
    val birthDate: LocalDate,
    val address: String,
    val hireDate: LocalDate,
    val monthlySalary: Double,
    val specialization: String? = null,
    val observations: String? = null,
    val emergencyContactName: String,
    val emergencyContactPhone: String
)

data class UpdateTeachingStaffRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val monthlySalary: Double? = null,
    val paymentStatus: PaymentStatus? = null,
    val specialization: String? = null,
    val observations: String? = null,
    val notes: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null
)

