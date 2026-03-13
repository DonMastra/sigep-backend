package com.sigep.staff.application.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.sigep.staff.domain.model.NonTeachingRole
import java.time.LocalDate
import java.time.LocalDateTime

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
    /** Rol en formato backend — ej: IT_SUPPORT, CLEANING, etc. */
    val role: NonTeachingRole,
    /** Alias de `role` para compatibilidad con frontend que usa `position` */
    val position: String,
    val companyName: String,
    /** Alias de `companyName` para compatibilidad con frontend que usa `company` */
    val company: String,
    val assignedTasks: String? = null,
    val observations: String? = null,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    /** Estado derivado de isActive — compatible con frontend: ACTIVE | INACTIVE */
    val status: String,
    val attendanceStats: AttendanceStatsDto? = null,
    val hoursWorkedThisMonth: Double? = null,
    val estimatedEarningsThisMonth: Double? = null,
    val photoUrl: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
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
    /** Puede recibirse como `role` o como `position` (alias frontend) */
    val role: NonTeachingRole? = null,
    val position: NonTeachingRole? = null,
    /** Puede recibirse como `companyName` o como `company` (alias frontend) */
    val companyName: String? = null,
    val company: String? = null,
    val assignedTasks: String? = null,
    val observations: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    /** Campo combinado aceptado del frontend: "Nombre / Teléfono" */
    val emergencyContact: String? = null
) {
    @get:JsonIgnore
    val resolvedRole: NonTeachingRole
        get() = role ?: position ?: NonTeachingRole.OTHER

    @get:JsonIgnore
    val resolvedCompanyName: String
        get() = companyName ?: company ?: ""

    @get:JsonIgnore
    val resolvedEmergencyContactName: String
        get() = emergencyContactName ?: emergencyContact?.split("/")?.getOrNull(0)?.trim() ?: ""

    @get:JsonIgnore
    val resolvedEmergencyContactPhone: String
        get() = emergencyContactPhone ?: emergencyContact?.split("/")?.getOrNull(1)?.trim() ?: ""
}

data class UpdateNonTeachingStaffRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val hourlyRate: Double? = null,
    val role: NonTeachingRole? = null,
    val position: NonTeachingRole? = null,
    val companyName: String? = null,
    val company: String? = null,
    val assignedTasks: String? = null,
    val observations: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    val emergencyContact: String? = null
) {
    @get:JsonIgnore
    val resolvedRole: NonTeachingRole?
        get() = role ?: position

    @get:JsonIgnore
    val resolvedCompanyName: String?
        get() = companyName ?: company

    @get:JsonIgnore
    val resolvedEmergencyContactName: String?
        get() = emergencyContactName ?: emergencyContact?.split("/")?.getOrNull(0)?.trim()

    @get:JsonIgnore
    val resolvedEmergencyContactPhone: String?
        get() = emergencyContactPhone ?: emergencyContact?.split("/")?.getOrNull(1)?.trim()
}
