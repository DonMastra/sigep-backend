package com.sigep.staff.application.dto

import com.sigep.staff.domain.model.AttendanceStatus
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import java.time.LocalDate
import java.time.LocalTime

data class StaffAttendanceDto(
    val id: Long,
    val staffId: Long,
    val staffName: String,
    val staffType: StaffType,
    val attendanceDate: LocalDate,
    val checkInTime: LocalTime? = null,
    val checkOutTime: LocalTime? = null,
    val status: AttendanceStatus,
    val notes: String? = null,
    val hoursWorked: Double? = null
)

enum class StaffType {
    TEACHING,
    NON_TEACHING
}

data class CreateAttendanceRequest(
    val teachingStaffId: Long? = null,
    val nonTeachingStaffId: Long? = null,
    val attendanceDate: LocalDate,
    val checkInTime: LocalTime? = null,
    val checkOutTime: LocalTime? = null,
    val status: AttendanceStatus,
    val notes: String? = null,
    val hoursWorked: Double? = null
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "Debe indicar exactamente un docente o un no docente")
    val hasExactlyOneStaffReference: Boolean
        get() = (teachingStaffId != null) xor (nonTeachingStaffId != null)

    @get:JsonIgnore
    @get:AssertTrue(message = "La hora de salida no puede ser anterior a la hora de entrada")
    val hasValidTimeRange: Boolean
        get() = checkInTime == null || checkOutTime == null || !checkOutTime.isBefore(checkInTime)
}

data class UpdateAttendanceRequest(
    val checkInTime: LocalTime? = null,
    val checkOutTime: LocalTime? = null,
    val status: AttendanceStatus? = null,
    val notes: String? = null,
    val hoursWorked: Double? = null
)

data class AttendanceReportDto(
    val staffId: Long,
    val staffName: String,
    val staffType: StaffType,
    val period: String,
    val attendanceRecords: List<StaffAttendanceDto>,
    val stats: AttendanceStatsDto
)

