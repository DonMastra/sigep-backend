package com.sigep.staff.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.staff.application.dto.*
import com.sigep.staff.domain.model.StaffAttendance
import com.sigep.staff.infrastructure.repository.NonTeachingStaffRepository
import com.sigep.staff.infrastructure.repository.StaffAttendanceRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import org.slf4j.LoggerFactory
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

    fun createAttendance(request: CreateAttendanceRequest): StaffAttendanceDto {
        log.info("Creating attendance record for date: {}", request.attendanceDate)

        val attendance = when {
            request.teachingStaffId != null -> {
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

    fun updateAttendance(id: Long, request: UpdateAttendanceRequest): StaffAttendanceDto {
        log.info("Updating attendance record with id: {}", id)

        val attendance = attendanceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Attendance record not found") }

        val updated = attendance.copy(
            checkInTime = request.checkInTime ?: attendance.checkInTime,
            checkOutTime = request.checkOutTime ?: attendance.checkOutTime,
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
        limit: Int
    ): PageResponse<StaffAttendanceDto> {
        log.debug("Fetching attendance for teaching staff: {}", staffId)

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

