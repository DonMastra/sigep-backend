package com.sigep.staff.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ValidationException
import com.sigep.staff.application.dto.*
import com.sigep.staff.domain.model.StaffAttendance
import com.sigep.staff.infrastructure.repository.NonTeachingStaffRepository
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Service
@Transactional
class StaffAttendanceService(
    private val attendanceRepository: StaffAttendanceRepository,
    private val teachingStaffRepository: TeachingStaffRepository,
    private val nonTeachingStaffRepository: NonTeachingStaffRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(StaffAttendanceService::class.java)
    }

    @CacheEvict(value = ["teachingStaff", "nonTeachingStaff"], allEntries = true)
    fun createAttendance(request: CreateAttendanceRequest): StaffAttendanceDto {
        log.info("Creating attendance record for date: {}", request.attendanceDate)

        if (request.attendanceDate.isAfter(LocalDate.now())) {
            throw ValidationException("No se puede registrar asistencia en una fecha futura")
        }

        val attendance = when {
            request.teachingStaffId != null -> {
                if (attendanceRepository.findByTeachingStaffIdAndAttendanceDate(request.teachingStaffId, request.attendanceDate).isPresent) {
                    throw DuplicateResourceException("La asistencia del docente ya fue registrada para esa fecha")
                }
                val staff = teachingStaffRepository.findById(request.teachingStaffId)
                    .orElseThrow { ResourceNotFoundException("Teaching staff not found") }
                if (request.attendanceDate.isBefore(staff.hireDate)) {
                    throw ValidationException("La asistencia no puede ser anterior a la fecha de contratación")
                }
                val workedStatus = isWorkedStatus(request.status)

                StaffAttendance(
                    teachingStaff = staff,
                    attendanceDate = request.attendanceDate,
                    checkInTime = request.checkInTime.takeIf { workedStatus },
                    checkOutTime = request.checkOutTime.takeIf { workedStatus },
                    status = request.status,
                    notes = request.notes,
                    hoursWorked = resolveHoursWorked(request.status, request.checkInTime, request.checkOutTime, request.hoursWorked, true)
                )
            }
            request.nonTeachingStaffId != null -> {
                if (attendanceRepository.findByNonTeachingStaffIdAndAttendanceDate(request.nonTeachingStaffId, request.attendanceDate).isPresent) {
                    throw DuplicateResourceException("La asistencia del personal no docente ya fue registrada para esa fecha")
                }
                val staff = nonTeachingStaffRepository.findById(request.nonTeachingStaffId)
                    .orElseThrow { ResourceNotFoundException("Non-teaching staff not found") }
                if (request.attendanceDate.isBefore(staff.hireDate)) {
                    throw ValidationException("La actividad no puede ser anterior a la fecha de contratación")
                }
                val hoursWorked = resolveHoursWorked(
                    request.status,
                    request.checkInTime,
                    request.checkOutTime,
                    request.hoursWorked,
                    true
                )
                if ((hoursWorked ?: 0.0) > 0.0 && staff.currency == null) {
                    throw ValidationException("Debe definir la moneda de la tarifa antes de registrar horas")
                }
                val workedStatus = isWorkedStatus(request.status)

                StaffAttendance(
                    nonTeachingStaff = staff,
                    attendanceDate = request.attendanceDate,
                    checkInTime = request.checkInTime.takeIf { workedStatus },
                    checkOutTime = request.checkOutTime.takeIf { workedStatus },
                    status = request.status,
                    notes = request.notes,
                    hoursWorked = hoursWorked,
                    hourlyRateSnapshot = staff.currency?.let {
                        BigDecimal.valueOf(staff.hourlyRate).setScale(2, RoundingMode.HALF_EVEN)
                    },
                    currencySnapshot = staff.currency
                )
            }
            else -> throw IllegalArgumentException("Either teachingStaffId or nonTeachingStaffId must be provided")
        }

        val saved = attendanceRepository.save(attendance)
        return toDto(saved)
    }

    @CacheEvict(value = ["teachingStaff", "nonTeachingStaff"], allEntries = true)
    fun updateAttendance(id: Long, request: UpdateAttendanceRequest): StaffAttendanceDto {
        log.info("Updating attendance record with id: {}", id)

        val attendance = attendanceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Attendance record not found") }

        val resolvedCheckIn = request.checkInTime ?: attendance.checkInTime
        val resolvedCheckOut = request.checkOutTime ?: attendance.checkOutTime
        if (resolvedCheckIn != null && resolvedCheckOut != null && resolvedCheckOut.isBefore(resolvedCheckIn)) {
            throw ValidationException("La hora de salida no puede ser anterior a la hora de entrada")
        }

        val resolvedStatus = request.status ?: attendance.status
        val hoursCandidate = if (resolvedStatus in setOf(
                com.sigep.staff.domain.model.AttendanceStatus.PRESENT,
                com.sigep.staff.domain.model.AttendanceStatus.LATE
            )) request.hoursWorked ?: attendance.hoursWorked else request.hoursWorked
        val workedStatus = isWorkedStatus(resolvedStatus)
        val finalCheckIn = if (workedStatus) resolvedCheckIn else null
        val finalCheckOut = if (workedStatus) resolvedCheckOut else null
        val resolvedHours = resolveHoursWorked(
            resolvedStatus,
            finalCheckIn,
            finalCheckOut,
            hoursCandidate,
            true
        )
        val currentNonTeachingStaff = attendance.nonTeachingStaff
        if (currentNonTeachingStaff != null && (resolvedHours ?: 0.0) > 0.0 &&
            attendance.currencySnapshot == null && currentNonTeachingStaff.currency == null
        ) {
            throw ValidationException("Debe definir la moneda de la tarifa antes de registrar horas")
        }

        val updated = attendance.copy(
            checkInTime = finalCheckIn,
            checkOutTime = finalCheckOut,
            status = resolvedStatus,
            notes = request.notes ?: attendance.notes,
            hoursWorked = resolvedHours,
            hourlyRateSnapshot = attendance.hourlyRateSnapshot ?: if ((resolvedHours ?: 0.0) > 0.0) {
                currentNonTeachingStaff?.currency?.let {
                    BigDecimal.valueOf(currentNonTeachingStaff.hourlyRate).setScale(2, RoundingMode.HALF_EVEN)
                }
            } else null,
            currencySnapshot = attendance.currencySnapshot ?: if ((resolvedHours ?: 0.0) > 0.0) {
                currentNonTeachingStaff?.currency
            } else null
        )

        val saved = attendanceRepository.save(updated)
        return toDto(saved)
    }

    @Transactional(readOnly = true)
    fun getTeachingStaffAttendance(
        staffId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        page: Int,
        limit: Int,
        actorUserId: Long?,
        actorRole: String?
    ): PageResponse<StaffAttendanceDto> {
        log.debug("Fetching attendance for teaching staff: {}", staffId)
        validateListRequest(startDate, endDate, page, limit)
        if (actorRole == "TEACHER") {
            val staff = teachingStaffRepository.findById(staffId)
                .orElseThrow { ResourceNotFoundException("Teaching staff not found") }
            if (staff.linkedUserId != actorUserId) {
                throw ForbiddenException("Los docentes solo pueden consultar su propia asistencia")
            }
        } else if (actorRole != "ADMIN") {
            throw ForbiddenException("No tiene permisos para consultar asistencia docente")
        }

        val pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "attendanceDate"))
        val attendancePage = attendanceRepository.findByTeachingStaffIdAndAttendanceDateBetween(
            staffId, startDate, endDate, pageable
        )

        val dtos = attendancePage.content.map { toDto(it) }

        return PageResponse(
            content = dtos,
            page = attendancePage.number,
            size = attendancePage.size,
            totalElements = attendancePage.totalElements,
            totalPages = attendancePage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getNonTeachingStaffAttendance(
        staffId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        page: Int,
        limit: Int
    ): PageResponse<StaffAttendanceDto> {
        log.debug("Fetching attendance for non-teaching staff: {}", staffId)
        validateListRequest(startDate, endDate, page, limit)

        val pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "attendanceDate"))
        val attendancePage = attendanceRepository.findByNonTeachingStaffIdAndAttendanceDateBetween(
            staffId, startDate, endDate, pageable
        )

        val dtos = attendancePage.content.map { toDto(it) }

        return PageResponse(
            content = dtos,
            page = attendancePage.number,
            size = attendancePage.size,
            totalElements = attendancePage.totalElements,
            totalPages = attendancePage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getTeachingStaffMonthlySummary(
        staffId: Long,
        month: YearMonth,
        actorUserId: Long?,
        actorRole: String?
    ): StaffMonthlySummaryDto {
        val staff = teachingStaffRepository.findById(staffId)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found") }
        if (actorRole == "TEACHER" && staff.linkedUserId != actorUserId) {
            throw ForbiddenException("Los docentes solo pueden consultar su propio resumen mensual")
        }
        if (actorRole != "ADMIN" && actorRole != "TEACHER") {
            throw ForbiddenException("No tiene permisos para consultar el resumen docente")
        }

        val records = attendanceRepository.findAllByTeachingStaffIdAndAttendanceDateBetween(
            staffId, month.atDay(1), month.atEndOfMonth()
        )
        return buildMonthlySummary(
            staffId = staffId,
            staffName = staff.fullName,
            staffType = StaffType.TEACHING,
            hireDate = staff.hireDate,
            month = month,
            records = records,
            currentMonthlyAmount = BigDecimal.valueOf(staff.monthlySalary),
            currentCurrency = staff.currency
        )
    }

    @Transactional(readOnly = true)
    fun getNonTeachingStaffMonthlySummary(staffId: Long, month: YearMonth): StaffMonthlySummaryDto {
        val staff = nonTeachingStaffRepository.findById(staffId)
            .orElseThrow { ResourceNotFoundException("Non-teaching staff not found") }
        val records = attendanceRepository.findAllByNonTeachingStaffIdAndAttendanceDateBetween(
            staffId, month.atDay(1), month.atEndOfMonth()
        )
        return buildMonthlySummary(
            staffId = staffId,
            staffName = staff.fullName,
            staffType = StaffType.NON_TEACHING,
            hireDate = staff.hireDate,
            month = month,
            records = records,
            currentHourlyRate = BigDecimal.valueOf(staff.hourlyRate),
            currentCurrency = staff.currency
        )
    }

    @CacheEvict(value = ["teachingStaff", "nonTeachingStaff"], allEntries = true)
    fun deleteAttendance(id: Long) {
        log.info("Deleting attendance record with id: {}", id)

        if (!attendanceRepository.existsById(id)) {
            throw ResourceNotFoundException("Attendance record not found")
        }

        attendanceRepository.deleteById(id)
    }

    private fun toDto(attendance: StaffAttendance): StaffAttendanceDto {
        val (staffId, staffName, staffType) = when {
            attendance.teachingStaff != null -> Triple(
                attendance.teachingStaff.id!!,
                attendance.teachingStaff.fullName,
                StaffType.TEACHING
            )
            attendance.nonTeachingStaff != null -> Triple(
                attendance.nonTeachingStaff.id!!,
                attendance.nonTeachingStaff.fullName,
                StaffType.NON_TEACHING
            )
            else -> throw IllegalStateException("Invalid attendance record")
        }

        return StaffAttendanceDto(
            id = attendance.id!!,
            staffId = staffId,
            staffName = staffName,
            staffType = staffType,
            attendanceDate = attendance.attendanceDate,
            checkInTime = attendance.checkInTime,
            checkOutTime = attendance.checkOutTime,
            status = attendance.status,
            notes = attendance.notes,
            hoursWorked = attendance.hoursWorked,
            hourlyRateSnapshot = attendance.hourlyRateSnapshot,
            currencySnapshot = attendance.currencySnapshot
        )
    }

    private fun buildMonthlySummary(
        staffId: Long,
        staffName: String,
        staffType: StaffType,
        hireDate: LocalDate,
        month: YearMonth,
        records: List<StaffAttendance>,
        currentHourlyRate: BigDecimal? = null,
        currentMonthlyAmount: BigDecimal? = null,
        currentCurrency: com.sigep.staff.domain.model.StaffCurrency? = null
    ): StaffMonthlySummaryDto {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val employmentStart = if (hireDate.isAfter(start)) hireDate else start
        val today = LocalDate.now()
        val elapsedEnd = when {
            today.isBefore(start) -> start.minusDays(1)
            today.isBefore(end) -> today
            else -> end
        }
        val businessDays = countBusinessDays(employmentStart, end)
        val elapsedBusinessDays = countBusinessDays(employmentStart, elapsedEnd)
        val elapsedRecords = records.filter { record ->
            !record.attendanceDate.isBefore(employmentStart) &&
                !record.attendanceDate.isAfter(elapsedEnd)
        }
        val businessDayRecords = elapsedRecords.filter { record ->
                record.attendanceDate.dayOfWeek.value in 1..5
        }
        val presentDays = elapsedRecords.count { it.status == com.sigep.staff.domain.model.AttendanceStatus.PRESENT }
        val lateDays = elapsedRecords.count { it.status == com.sigep.staff.domain.model.AttendanceStatus.LATE }
        val attendedDays = presentDays + lateDays
        val registeredBusinessDays = businessDayRecords.map { it.attendanceDate }.distinct().size
        val hoursWorked = elapsedRecords.sumOf { it.hoursWorked ?: 0.0 }

        val rates = elapsedRecords.filter { (it.hoursWorked ?: 0.0) > 0.0 }.map { record ->
            Triple(
                record.hoursWorked ?: 0.0,
                record.hourlyRateSnapshot ?: currentHourlyRate,
                record.currencySnapshot ?: currentCurrency
            )
        }
        val currencies = rates.mapNotNull { it.third }.distinct()
        val hasMixedCurrencies = currencies.size > 1
        val usesFallback = currentHourlyRate != null && elapsedRecords.any {
            (it.hoursWorked ?: 0.0) > 0.0 &&
                (it.hourlyRateSnapshot == null || it.currencySnapshot == null)
        }
        val summaryCurrency = if (hasMixedCurrencies) null else currencies.singleOrNull() ?: currentCurrency
        val estimatedAmount = when (staffType) {
            StaffType.NON_TEACHING -> if (
                !hasMixedCurrencies &&
                summaryCurrency != null &&
                rates.all { it.second != null && it.third != null }
            ) {
                rates.fold(BigDecimal.ZERO) { total, (hours, rate, _) ->
                    total + rate!!.multiply(BigDecimal.valueOf(hours))
                }.setScale(2, RoundingMode.HALF_EVEN)
            } else null
            StaffType.TEACHING -> currentMonthlyAmount
                ?.takeIf { summaryCurrency != null }
                ?.setScale(2, RoundingMode.HALF_EVEN)
        }
        val compensationBasis = when (staffType) {
            StaffType.NON_TEACHING -> StaffCompensationBasis.HOURLY_RATE
            StaffType.TEACHING -> StaffCompensationBasis.MONTHLY_SALARY
        }

        return StaffMonthlySummaryDto(
            staffId = staffId,
            staffName = staffName,
            staffType = staffType,
            period = month.toString(),
            periodStart = start,
            periodEnd = end,
            businessDaysInMonth = businessDays,
            elapsedBusinessDays = elapsedBusinessDays,
            registeredDays = elapsedRecords.size,
            attendedDays = attendedDays,
            presentDays = presentDays,
            lateDays = lateDays,
            absentDays = elapsedRecords.count { it.status == com.sigep.staff.domain.model.AttendanceStatus.ABSENT },
            excusedDays = elapsedRecords.count { it.status == com.sigep.staff.domain.model.AttendanceStatus.EXCUSED },
            sickLeaveDays = elapsedRecords.count { it.status == com.sigep.staff.domain.model.AttendanceStatus.SICK_LEAVE },
            vacationDays = elapsedRecords.count { it.status == com.sigep.staff.domain.model.AttendanceStatus.VACATION },
            unregisteredElapsedDays = (elapsedBusinessDays - registeredBusinessDays).coerceAtLeast(0),
            attendanceRate = percentage(attendedDays, elapsedRecords.size),
            dataCoverageRate = percentage(registeredBusinessDays, elapsedBusinessDays),
            hoursWorked = BigDecimal.valueOf(hoursWorked).setScale(2, RoundingMode.HALF_EVEN).toDouble(),
            estimatedAmount = estimatedAmount,
            currency = summaryCurrency,
            compensationBasis = compensationBasis,
            amountIsHistorical = staffType == StaffType.NON_TEACHING && estimatedAmount != null && !usesFallback,
            usesCurrentRateFallback = usesFallback,
            hasMixedCurrencies = hasMixedCurrencies
        )
    }

    private fun countBusinessDays(start: LocalDate, end: LocalDate): Int {
        if (end.isBefore(start)) return 0
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .count { it.dayOfWeek.value in 1..5 }
    }

    private fun validateListRequest(startDate: LocalDate, endDate: LocalDate, page: Int, limit: Int) {
        if (endDate.isBefore(startDate)) {
            throw ValidationException("La fecha de fin no puede ser anterior a la fecha de inicio")
        }
        if (page < 0 || limit !in 1..100) {
            throw ValidationException("La página debe ser mayor o igual a cero y el límite debe estar entre 1 y 100")
        }
    }

    private fun percentage(numerator: Int, denominator: Int): Double {
        if (denominator <= 0) return 0.0
        return BigDecimal.valueOf(numerator.toLong())
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator.toLong()), 2, RoundingMode.HALF_EVEN)
            .coerceAtMost(BigDecimal.valueOf(100))
            .toDouble()
    }

    private fun resolveHoursWorked(
        status: com.sigep.staff.domain.model.AttendanceStatus,
        checkIn: LocalTime?,
        checkOut: LocalTime?,
        providedHours: Double?,
        requireForPresence: Boolean
    ): Double? {
        if ((checkIn == null) xor (checkOut == null)) {
            throw ValidationException("Debe indicar la hora de entrada y de salida juntas")
        }
        if (!isWorkedStatus(status)) {
            if ((providedHours ?: 0.0) > 0.0) {
                throw ValidationException("Una ausencia o licencia no puede registrar horas trabajadas")
            }
            return 0.0
        }

        val derived = if (checkIn != null && checkOut != null) {
            Duration.between(checkIn, checkOut).toMinutes().toDouble() / 60.0
        } else null
        val resolved = derived ?: providedHours
        if (requireForPresence && (resolved == null || resolved <= 0.0)) {
            throw ValidationException("Para Presente o Tardanza debe indicar entrada y salida u horas trabajadas")
        }
        if (resolved != null && (resolved < 0.0 || resolved > 24.0)) {
            throw ValidationException("Las horas trabajadas deben estar entre 0 y 24")
        }
        return resolved?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_EVEN).toDouble() }
    }

    private fun isWorkedStatus(status: com.sigep.staff.domain.model.AttendanceStatus): Boolean =
        status == com.sigep.staff.domain.model.AttendanceStatus.PRESENT ||
            status == com.sigep.staff.domain.model.AttendanceStatus.LATE
}

