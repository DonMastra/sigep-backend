package com.sigep.staff.application.dto

import com.sigep.staff.domain.model.NonTeachingRole
import java.time.LocalDate

data class NonTeachingStaffDto(
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
    val hourlyRate: Double,
    val role: NonTeachingRole,
    val companyName: String,
    val assignedTasks: String? = null,
    val observations: String? = null,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val attendanceStats: AttendanceStatsDto? = null,
    val hoursWorkedThisMonth: Double? = null,
    val estimatedEarningsThisMonth: Double? = null
)

data class CreateNonTeachingStaffRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String,
    val documentNumber: String,
    val birthDate: LocalDate,
    val address: String,
    val hireDate: LocalDate,
    val hourlyRate: Double,
    val role: NonTeachingRole,
    val companyName: String,
    val assignedTasks: String? = null,
    val observations: String? = null,
    val emergencyContactName: String,
    val emergencyContactPhone: String
)

data class UpdateNonTeachingStaffRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val hourlyRate: Double? = null,
    val role: NonTeachingRole? = null,
    val companyName: String? = null,
    val assignedTasks: String? = null,
    val observations: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null
)

