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
import java.time.LocalDate

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

        val attendance = when {
            request.teachingStaffId != null -> {
                if (attendanceRepository.findByTeachingStaffIdAndAttendanceDate(request.teachingStaffId, request.attendanceDate).isPresent) {
                    throw DuplicateResourceException("La asistencia del docente ya fue registrada para esa fecha")
                }
                val staff = teachingStaffRepository.findById(request.teachingStaffId)
                    .orElseThrow { ResourceNotFoundException("Teaching staff not found") }

                StaffAttendance(
                    teachingStaff = staff,
                    attendanceDate = request.attendanceDate,
                    checkInTime = request.checkInTime,
                    checkOutTime = request.checkOutTime,
                    status = request.status,
                    notes = request.notes,
                    hoursWorked = request.hoursWorked
                )
            }
            request.nonTeachingStaffId != null -> {
                if (attendanceRepository.findByNonTeachingStaffIdAndAttendanceDate(request.nonTeachingStaffId, request.attendanceDate).isPresent) {
                    throw DuplicateResourceException("La asistencia del personal no docente ya fue registrada para esa fecha")
                }
                val staff = nonTeachingStaffRepository.findById(request.nonTeachingStaffId)
                    .orElseThrow { ResourceNotFoundException("Non-teaching staff not found") }

                StaffAttendance(
                    nonTeachingStaff = staff,
                    attendanceDate = request.attendanceDate,
                    checkInTime = request.checkInTime,
                    checkOutTime = request.checkOutTime,
                    status = request.status,
                    notes = request.notes,
                    hoursWorked = request.hoursWorked
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

        val updated = attendance.copy(
            checkInTime = resolvedCheckIn,
            checkOutTime = resolvedCheckOut,
            status = request.status ?: attendance.status,
            notes = request.notes ?: attendance.notes,
            hoursWorked = request.hoursWorked ?: attendance.hoursWorked
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
            hoursWorked = attendance.hoursWorked
        )
    }
}

