package com.sigep.staff.application.dto

import com.sigep.staff.domain.model.AttendanceStatus
import com.fasterxml.jackson.annotation.JsonIgnore
import com.sigep.staff.domain.model.StaffCurrency
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal
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
    val hoursWorked: Double? = null,
    val hourlyRateSnapshot: BigDecimal? = null,
    val currencySnapshot: StaffCurrency? = null
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
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Las horas trabajadas no pueden ser negativas")
    @field:DecimalMax(value = "24.0", inclusive = true, message = "Las horas trabajadas no pueden superar 24 por día")
    val hoursWorked: Double? = null
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "Debe indicar exactamente un docente o un no docente")
    val hasExactlyOneStaffReference: Boolean
        get() = (teachingStaffId != null) xor (nonTeachingStaffId != null)

    @get:JsonIgnore
    @get:AssertTrue(message = "Debe indicar entrada y salida juntas, y la salida no puede ser anterior a la entrada")
    val hasValidTimeRange: Boolean
        get() = (checkInTime == null && checkOutTime == null) ||
            (checkInTime != null && checkOutTime != null && !checkOutTime.isBefore(checkInTime))
}

data class UpdateAttendanceRequest(
    val checkInTime: LocalTime? = null,
    val checkOutTime: LocalTime? = null,
    val status: AttendanceStatus? = null,
    val notes: String? = null,
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Las horas trabajadas no pueden ser negativas")
    @field:DecimalMax(value = "24.0", inclusive = true, message = "Las horas trabajadas no pueden superar 24 por día")
    val hoursWorked: Double? = null
)

data class StaffMonthlySummaryDto(
    val staffId: Long,
    val staffName: String,
    val staffType: StaffType,
    val period: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val businessDaysInMonth: Int,
    val elapsedBusinessDays: Int,
    val registeredDays: Int,
    val attendedDays: Int,
    val presentDays: Int,
    val lateDays: Int,
    val absentDays: Int,
    val excusedDays: Int,
    val sickLeaveDays: Int,
    val vacationDays: Int,
    val unregisteredElapsedDays: Int,
    val attendanceRate: Double,
    val dataCoverageRate: Double,
    val hoursWorked: Double,
    val estimatedAmount: BigDecimal? = null,
    val currency: StaffCurrency? = null,
    val usesCurrentRateFallback: Boolean = false,
    val hasMixedCurrencies: Boolean = false
)

data class AttendanceReportDto(
    val staffId: Long,
    val staffName: String,
    val staffType: StaffType,
    val period: String,
    val attendanceRecords: List<StaffAttendanceDto>,
    val stats: AttendanceStatsDto
)

