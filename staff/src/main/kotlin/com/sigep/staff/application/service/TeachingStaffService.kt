package com.sigep.staff.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.staff.application.dto.*
import com.sigep.staff.domain.model.AttendanceStatus
import com.sigep.staff.domain.model.TeachingStaff
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

@Service
@Transactional
class TeachingStaffService(
    private val teachingStaffRepository: TeachingStaffRepository,
    private val attendanceRepository: StaffAttendanceRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(TeachingStaffService::class.java)
    }

    @Cacheable(value = ["teachingStaff"], key = "#page + '-' + #limit + '-' + #sort + '-' + #order")
    @Transactional(readOnly = true)
    fun getAllTeachingStaff(page: Int, limit: Int, sort: String, order: String): PageResponse<TeachingStaffDto> {
        log.debug("Fetching all teaching staff - page: {}, limit: {}", page, limit)

        val direction = if (order.uppercase() == "DESC") Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(page, limit, Sort.by(direction, sort))
        val staffPage = teachingStaffRepository.findByIsActiveTrue(pageable)
        val staffDtos = staffPage.content.map { toDto(it) }

        return PageResponse(
            content = staffDtos,
            page = staffPage.number,
            size = staffPage.size,
            totalElements = staffPage.totalElements,
            totalPages = staffPage.totalPages
        )
    }

    @Cacheable(value = ["teachingStaff"], key = "#id")
    @Transactional(readOnly = true)
    fun getTeachingStaffById(id: Long): TeachingStaffDto {
        log.debug("Fetching teaching staff by id: {}", id)

        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }

        if (!staff.isActive) {
            throw ResourceNotFoundException("Teaching staff is inactive with id: $id")
        }

        return toDtoWithDetails(staff)
    }

    @Transactional(readOnly = true)
    fun searchTeachingStaff(query: String, page: Int, limit: Int): PageResponse<TeachingStaffDto> {
        log.debug("Searching teaching staff with query: {}", query)

        val pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.ASC, "lastName"))
        val staffPage = teachingStaffRepository.searchByQuery(query, pageable)
        val staffDtos = staffPage.content.map { toDto(it) }

        return PageResponse(
            content = staffDtos,
            page = staffPage.number,
            size = staffPage.size,
            totalElements = staffPage.totalElements,
            totalPages = staffPage.totalPages
        )
    }

    @CacheEvict(value = ["teachingStaff"], allEntries = true)
    fun createTeachingStaff(request: CreateTeachingStaffRequest): TeachingStaffDto {
        log.info("Creating new teaching staff: {} {}", request.firstName, request.lastName)

        teachingStaffRepository.findByEmail(request.email)?.let {
            throw IllegalArgumentException("Email already exists: ${request.email}")
        }

        teachingStaffRepository.findByDocumentNumber(request.documentNumber)?.let {
            throw IllegalArgumentException("Document number already exists: ${request.documentNumber}")
        }

        val staff = TeachingStaff(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            phoneNumber = request.phoneNumber,
            documentNumber = request.documentNumber,
            birthDate = request.birthDate,
            address = request.address,
            hireDate = request.hireDate,
            monthlySalary = request.monthlySalary,
            specialization = request.specialization ?: request.qualifications,
            observations = request.observations,
            notes = request.notes,
            emergencyContactName = request.resolvedEmergencyContactName,
            emergencyContactPhone = request.resolvedEmergencyContactPhone
        )

        val savedStaff = teachingStaffRepository.save(staff)
        log.info("Teaching staff created successfully with id: {}", savedStaff.id)

        return toDto(savedStaff)
    }

    @CacheEvict(value = ["teachingStaff"], allEntries = true)
    fun updateTeachingStaff(id: Long, request: UpdateTeachingStaffRequest): TeachingStaffDto {
        log.info("Updating teaching staff with id: {}", id)

        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }

        request.email?.let { newEmail ->
            if (newEmail != staff.email) {
                teachingStaffRepository.findByEmail(newEmail)?.let {
                    throw IllegalArgumentException("Email already exists: $newEmail")
                }
            }
        }

        val updatedStaff = staff.copy(
            firstName = request.firstName ?: staff.firstName,
            lastName = request.lastName ?: staff.lastName,
            email = request.email ?: staff.email,
            phoneNumber = request.phoneNumber ?: staff.phoneNumber,
            address = request.address ?: staff.address,
            monthlySalary = request.monthlySalary ?: staff.monthlySalary,
            paymentStatus = request.paymentStatus ?: staff.paymentStatus,
            specialization = request.specialization ?: request.qualifications ?: staff.specialization,
            observations = request.observations ?: staff.observations,
            notes = request.notes ?: staff.notes,
            emergencyContactName = request.resolvedEmergencyContactName ?: staff.emergencyContactName,
            emergencyContactPhone = request.resolvedEmergencyContactPhone ?: staff.emergencyContactPhone
        )

        val savedStaff = teachingStaffRepository.save(updatedStaff)
        log.info("Teaching staff updated successfully with id: {}", savedStaff.id)

        return toDto(savedStaff)
    }

    @CacheEvict(value = ["teachingStaff"], allEntries = true)
    fun deleteTeachingStaff(id: Long) {
        log.info("Deleting teaching staff with id: {}", id)

        val staff = teachingStaffRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Teaching staff not found with id: $id") }

        staff.isActive = false
        teachingStaffRepository.save(staff)

        log.info("Teaching staff soft-deleted successfully with id: {}", id)
    }

    private fun toDto(staff: TeachingStaff): TeachingStaffDto {
        return TeachingStaffDto(
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
            monthlySalary = staff.monthlySalary,
            paymentStatus = staff.paymentStatus,
            status = if (staff.isActive) "ACTIVE" else "INACTIVE",
            assignedStudentsCount = staff.assignedStudentsCount,
            specialization = staff.specialization,
            observations = staff.observations,
            notes = staff.notes,
            emergencyContactName = staff.emergencyContactName,
            emergencyContactPhone = staff.emergencyContactPhone,
            attendanceStats = AttendanceStatsDto.EMPTY,
            totalWorkingDaysInMonth = 0,
            createdAt = staff.createdAt,
            updatedAt = staff.updatedAt
        )
    }

    private fun toDtoWithDetails(staff: TeachingStaff): TeachingStaffDto {
        val dto = toDto(staff)

        val now = LocalDate.now()
        val startOfMonth = now.withDayOfMonth(1)
        val endOfMonth = YearMonth.now().atEndOfMonth()

        // Calcular días laborales reales del mes (lunes a viernes)
        val totalWorkingDays = generateSequence(startOfMonth) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endOfMonth) }
            .count { it.dayOfWeek.value in 1..5 }

        val presentDays = attendanceRepository.countTeachingStaffAttendanceByStatus(
            staff.id!!, AttendanceStatus.PRESENT, startOfMonth, endOfMonth
        ).toInt()

        val absentDays = attendanceRepository.countTeachingStaffAttendanceByStatus(
            staff.id, AttendanceStatus.ABSENT, startOfMonth, endOfMonth
        ).toInt()

        val lateDays = attendanceRepository.countTeachingStaffAttendanceByStatus(
            staff.id, AttendanceStatus.LATE, startOfMonth, endOfMonth
        ).toInt()

        val totalDays = presentDays + absentDays + lateDays
        val attendanceRate = if (totalWorkingDays > 0) (presentDays.toDouble() / totalWorkingDays.toDouble()) * 100 else 0.0

        return dto.copy(
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
}

