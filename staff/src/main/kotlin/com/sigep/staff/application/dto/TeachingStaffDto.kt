package com.sigep.staff.application.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.sigep.staff.domain.model.PaymentStatus
import java.time.LocalDate
import java.time.LocalDateTime
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TeachingStaffDto(
    val id: Long,
    val linkedUserId: Long?,
    val username: String?,
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
    /** Estado derivado de isActive — compatible con frontend: ACTIVE | INACTIVE */
    val status: String,
    val assignedStudentsCount: Int,
    val assignedCourses: List<CourseAssignmentDto>? = null,
    val specialization: String? = null,
    val qualifications: String? = null,
    val observations: String? = null,
    val notes: String? = null,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val attendanceStats: AttendanceStatsDto? = null,
    /** Días laborales reales del mes — requerido por frontend para calcular porcentaje de asistencia */
    val totalWorkingDaysInMonth: Int? = null,
    val photoUrl: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
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
) {
    companion object {
        /** Valor por defecto cuando no hay registros de asistencia — nunca se retorna null */
        val EMPTY = AttendanceStatsDto(
            totalDays = 0,
            presentDays = 0,
            absentDays = 0,
            lateDays = 0,
            attendanceRate = 0.0
        )
    }
}

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
    val paymentStatus: PaymentStatus = PaymentStatus.UP_TO_DATE,
    val specialization: String? = null,
    val observations: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    /** Campo combinado aceptado del frontend: "Nombre / Teléfono" — se splitea automáticamente */
    val emergencyContact: String? = null,
    val qualifications: String? = null,
    val notes: String? = null,
    @field:NotBlank val username: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val initialPassword: String,
    val assignedCourseIds: List<Long> = emptyList(),
    val confirmCourseReassignments: Boolean = false
) {
    /** Resuelve el nombre del contacto de emergencia desde los dos campos o el campo único */
    @get:JsonIgnore
    val resolvedEmergencyContactName: String
        get() = emergencyContactName ?: emergencyContact?.split("/")?.getOrNull(0)?.trim() ?: ""

    @get:JsonIgnore
    val resolvedEmergencyContactPhone: String
        get() = emergencyContactPhone ?: emergencyContact?.split("/")?.getOrNull(1)?.trim() ?: ""
}

data class UpdateTeachingStaffRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val documentNumber: String? = null,
    val birthDate: LocalDate? = null,
    val hireDate: LocalDate? = null,
    val address: String? = null,
    val monthlySalary: Double? = null,
    val paymentStatus: PaymentStatus? = null,
    val specialization: String? = null,
    val observations: String? = null,
    val notes: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
    /** Campo combinado aceptado del frontend: "Nombre / Teléfono" */
    val emergencyContact: String? = null,
    val qualifications: String? = null,
    val linkedUserId: Long? = null,
    val assignedCourseIds: List<Long>? = null,
    val confirmCourseReassignments: Boolean = false,
    val isActive: Boolean? = null
) {
    @get:JsonIgnore
    val resolvedEmergencyContactName: String?
        get() = emergencyContactName ?: emergencyContact?.split("/")?.getOrNull(0)?.trim()

    @get:JsonIgnore
    val resolvedEmergencyContactPhone: String?
        get() = emergencyContactPhone ?: emergencyContact?.split("/")?.getOrNull(1)?.trim()
}

data class ResolveTeachersRequest(
    val ids: List<Long>
)

data class TeacherResolutionDto(
    val id: Long,
    val fullName: String
)

