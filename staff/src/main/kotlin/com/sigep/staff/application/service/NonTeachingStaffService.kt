package com.sigep.staff.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.staff.application.dto.*
import com.sigep.staff.domain.model.AttendanceStatus
import com.sigep.staff.domain.model.NonTeachingStaff
import com.sigep.staff.domain.model.NonTeachingRole
import com.sigep.staff.infrastructure.repository.NonTeachingStaffRepository
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional
class NonTeachingStaffService(
    private val nonTeachingStaffRepository: NonTeachingStaffRepository,
    private val attendanceRepository: StaffAttendanceRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(NonTeachingStaffService::class.java)
    }

    @Cacheable(value = ["nonTeachingStaff"], key = "#page + '-' + #limit + '-' + #sort + '-' + #order")
    @Transactional(readOnly = true)
    fun getAllNonTeachingStaff(page: Int, limit: Int, sort: String, order: String): PageResponse<NonTeachingStaffDto> {
        log.debug("Fetching all non-teaching staff - page: {}, limit: {}", page, limit)

        val direction = if (order.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, limit, Sort.by(direction, sort))
        val staffPage = nonTeachingStaffRepository.findByIsActiveTrue(pageable)
        val staffDtos = toDtosWithCurrentMetrics(staffPage.content)

        return PageResponse(
            content = staffDtos,
            page = staffPage.number,
            size = staffPage.size,
            totalElements = staffPage.totalElements,
            totalPages = staffPage.totalPages
        )
    }

    @Cacheable(value = ["nonTeachingStaff"], key = "#id")
    @Transactional(readOnly = true)
    fun getNonTeachingStaffById(id: Long): NonTeachingStaffDto {
        log.debug("Fetching non-teaching staff by id: {}", id)

        val staff = nonTeachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Non-teaching staff not found with id: $id") }

        if (!staff.isActive) {
            throw ResourceNotFoundException("Non-teaching staff is inactive with id: $id")
        }

        return toDtoWithDetails(staff)
    }

    @Transactional(readOnly = true)
    fun getNonTeachingStaffByRole(role: NonTeachingRole, page: Int, limit: Int): PageResponse<NonTeachingStaffDto> {
        log.debug("Fetching non-teaching staff by role: {}", role)

        val pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.ASC, "lastName"))
        val staffPage = nonTeachingStaffRepository.findByRoleAndIsActiveTrue(role, pageable)
        val staffDtos = toDtosWithCurrentMetrics(staffPage.content)

        return PageResponse(
            content = staffDtos,
            page = staffPage.number,
            size = staffPage.size,
            totalElements = staffPage.totalElements,
            totalPages = staffPage.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun searchNonTeachingStaff(query: String, page: Int, limit: Int): PageResponse<NonTeachingStaffDto> {
        log.debug("Searching non-teaching staff with a supplied query")

        val pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.ASC, "lastName"))
        val staffPage = nonTeachingStaffRepository.searchByQuery(query, pageable)
        val staffDtos = toDtosWithCurrentMetrics(staffPage.content)

        return PageResponse(
            content = staffDtos,
            page = staffPage.number,
            size = staffPage.size,
            totalElements = staffPage.totalElements,
            totalPages = staffPage.totalPages
        )
    }

    @CacheEvict(value = ["nonTeachingStaff"], allEntries = true)
    fun createNonTeachingStaff(request: CreateNonTeachingStaffRequest): NonTeachingStaffDto {
        log.info("Creating new non-teaching staff record")

        nonTeachingStaffRepository.findByEmail(request.email)?.let {
            throw IllegalArgumentException("Email already exists: ${request.email}")
        }

        nonTeachingStaffRepository.findByDocumentNumber(request.documentNumber)?.let {
            throw IllegalArgumentException("Document number already exists: ${request.documentNumber}")
        }

        val staff = NonTeachingStaff(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phoneNumber = request.phoneNumber,
            documentNumber = request.documentNumber,
            birthDate = request.birthDate,
            address = request.address,
            hireDate = request.hireDate,
            hourlyRate = request.hourlyRate,
            currency = request.currency,
            role = request.resolvedRole,
            companyName = request.resolvedCompanyName,
            assignedTasks = request.assignedTasks,
            observations = request.observations,
            emergencyContactName = request.resolvedEmergencyContactName,
            emergencyContactPhone = request.resolvedEmergencyContactPhone
        )

        val savedStaff = nonTeachingStaffRepository.save(staff)
        log.info("Non-teaching staff created successfully with id: {}", savedStaff.id)

        return toDto(savedStaff)
    }

    @CacheEvict(value = ["nonTeachingStaff"], allEntries = true)
    fun updateNonTeachingStaff(id: Long, request: UpdateNonTeachingStaffRequest): NonTeachingStaffDto {
        log.info("Updating non-teaching staff with id: {}", id)

        val staff = nonTeachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Non-teaching staff not found with id: $id") }

        request.email?.let { newEmail ->
            if (newEmail != staff.email) {
                nonTeachingStaffRepository.findByEmail(newEmail)?.let {
                    throw IllegalArgumentException("Email already exists: $newEmail")
                }
            }
        }
        request.documentNumber?.let { newDocument ->
            if (newDocument != staff.documentNumber) {
                nonTeachingStaffRepository.findByDocumentNumber(newDocument)?.let {
                    throw IllegalArgumentException("Document number already exists: $newDocument")
                }
            }
        }

        val updatedStaff = staff.copy(
            firstName = request.firstName ?: staff.firstName,
            lastName = request.lastName ?: staff.lastName,
            email = request.email ?: staff.email,
            phoneNumber = request.phoneNumber ?: staff.phoneNumber,
            documentNumber = request.documentNumber ?: staff.documentNumber,
            birthDate = request.birthDate ?: staff.birthDate,
            hireDate = request.hireDate ?: staff.hireDate,
            address = request.address ?: staff.address,
            hourlyRate = request.hourlyRate ?: staff.hourlyRate,
            currency = request.currency ?: staff.currency,
            role = request.resolvedRole ?: staff.role,
            companyName = request.resolvedCompanyName ?: staff.companyName,
            assignedTasks = request.assignedTasks ?: staff.assignedTasks,
            observations = request.observations ?: staff.observations,
            emergencyContactName = request.resolvedEmergencyContactName ?: staff.emergencyContactName,
            emergencyContactPhone = request.resolvedEmergencyContactPhone ?: staff.emergencyContactPhone
        )

        val savedStaff = nonTeachingStaffRepository.save(updatedStaff)
        request.isActive?.let { savedStaff.isActive = it }
        log.info("Non-teaching staff updated successfully with id: {}", savedStaff.id)

        return toDto(savedStaff)
    }

    @CacheEvict(value = ["nonTeachingStaff"], allEntries = true)
    fun deleteNonTeachingStaff(id: Long) {
        log.info("Deleting non-teaching staff with id: {}", id)

        val staff = nonTeachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Non-teaching staff not found with id: $id") }

        staff.isActive = false
        nonTeachingStaffRepository.save(staff)

        log.info("Non-teaching staff soft-deleted successfully with id: {}", id)
    }

    private fun toDto(staff: NonTeachingStaff): NonTeachingStaffDto {
        val resolvedRole = staff.role ?: NonTeachingRole.OTHER
        return NonTeachingStaffDto(
            id = staff.id!!,
            firstName = staff.firstName,
            lastName = staff.lastName,
            fullName = staff.fullName,
            email = staff.email,
            phoneNumber = staff.phoneNumber,
            documentNumber = staff.documentNumber,
            birthDate = staff.birthDate,
            address = staff.address,
            hireDate = staff.hireDate,
            hourlyRate = staff.hourlyRate,
            currency = staff.currency,
            role = resolvedRole,
            position = resolvedRole.name,
            companyName = staff.companyName ?: "",
            company = staff.companyName ?: "",
            assignedTasks = staff.assignedTasks,
            observations = staff.observations,
            emergencyContactName = staff.emergencyContactName,
            emergencyContactPhone = staff.emergencyContactPhone,
            status = if (staff.isActive) "ACTIVE" else "INACTIVE",
            attendanceStats = AttendanceStatsDto.EMPTY,
            hoursWorkedThisMonth = 0.0,
            estimatedEarningsThisMonth = BigDecimal.ZERO.setScale(2),
            totalWorkingDaysInMonth = countBusinessDays(YearMonth.now(), staff.hireDate),
            createdAt = staff.createdAt,
            updatedAt = staff.updatedAt
        )
    }

    private fun toDtoWithDetails(staff: NonTeachingStaff): NonTeachingStaffDto {
        val now = LocalDate.now()
        val startOfMonth = now.withDayOfMonth(1)
        val endOfMonth = YearMonth.now().atEndOfMonth()
        val records = attendanceRepository.findAllByNonTeachingStaffIdAndAttendanceDateBetween(
            staff.id!!, startOfMonth, endOfMonth
        )
        return toDtoForMonth(staff, records, YearMonth.now())
    }

    private fun toDtosWithCurrentMetrics(staff: List<NonTeachingStaff>): List<NonTeachingStaffDto> {
        if (staff.isEmpty()) return emptyList()
        val month = YearMonth.now()
        val recordsByStaff = attendanceRepository.findAllByNonTeachingStaffIdsAndAttendanceDateBetween(
            staff.mapNotNull { it.id }, month.atDay(1), month.atEndOfMonth()
        ).groupBy { it.nonTeachingStaff?.id }
        return staff.map { member -> toDtoForMonth(member, recordsByStaff[member.id].orEmpty(), month) }
    }

    private fun toDtoForMonth(
        staff: NonTeachingStaff,
        records: List<com.sigep.staff.domain.model.StaffAttendance>,
        month: YearMonth
    ): NonTeachingStaffDto {
        val dto = toDto(staff)
        val totalWorkingDays = countBusinessDays(month, staff.hireDate)
        val today = LocalDate.now()
        val eligibleRecords = records.filter {
            !it.attendanceDate.isBefore(staff.hireDate) && !it.attendanceDate.isAfter(today)
        }
        val hoursWorked = eligibleRecords.sumOf { it.hoursWorked ?: 0.0 }

        val estimatedEarnings = BigDecimal.valueOf(staff.hourlyRate)
            .multiply(BigDecimal.valueOf(hoursWorked))
            .setScale(2, RoundingMode.HALF_EVEN)

        val presentDays = eligibleRecords.count { it.status == AttendanceStatus.PRESENT }
        val absentDays = eligibleRecords.count { it.status == AttendanceStatus.ABSENT }
        val lateDays = eligibleRecords.count { it.status == AttendanceStatus.LATE }
        val totalDays = eligibleRecords.size
        val attendanceRate = if (totalDays > 0) ((presentDays + lateDays).toDouble() / totalDays.toDouble()) * 100 else 0.0

        return dto.copy(
            hoursWorkedThisMonth = hoursWorked,
            estimatedEarningsThisMonth = estimatedEarnings,
            totalWorkingDaysInMonth = totalWorkingDays,
            attendanceStats = AttendanceStatsDto(
                totalDays = totalDays,
                presentDays = presentDays,
                absentDays = absentDays,
                lateDays = lateDays,
                attendanceRate = attendanceRate
            )
        )
    }

    private fun countBusinessDays(month: YearMonth, hireDate: LocalDate): Int {
        val start = if (hireDate.isAfter(month.atDay(1))) hireDate else month.atDay(1)
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(month.atEndOfMonth()) }
            .count { it.dayOfWeek.value in 1..5 }
    }
}

