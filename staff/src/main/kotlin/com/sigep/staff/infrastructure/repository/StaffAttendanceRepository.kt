package com.sigep.staff.infrastructure.repository

import com.sigep.staff.domain.model.StaffAttendance
import com.sigep.staff.domain.model.AttendanceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface StaffAttendanceRepository : JpaRepository<StaffAttendance, Long> {

    fun findByTeachingStaffIdAndAttendanceDate(teachingStaffId: Long, attendanceDate: LocalDate): Optional<StaffAttendance>

    fun findByNonTeachingStaffIdAndAttendanceDate(nonTeachingStaffId: Long, attendanceDate: LocalDate): Optional<StaffAttendance>

    @Query("""
        SELECT a.teachingStaff.id AS staffId,
               SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END) AS presentDays,
               SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) AS absentDays,
               SUM(CASE WHEN a.status = 'LATE' THEN 1 ELSE 0 END) AS lateDays
        FROM StaffAttendance a
        WHERE a.teachingStaff.id IN :staffIds
        AND a.attendanceDate BETWEEN :startDate AND :endDate
        GROUP BY a.teachingStaff.id
    """)
    fun summarizeTeachingAttendance(
        @Param("staffIds") staffIds: Collection<Long>,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<TeachingAttendanceStatsProjection>

    fun findByTeachingStaffIdAndAttendanceDateBetween(
        teachingStaffId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        pageable: Pageable
    ): Page<StaffAttendance>

    fun findByNonTeachingStaffIdAndAttendanceDateBetween(
        nonTeachingStaffId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        pageable: Pageable
    ): Page<StaffAttendance>

    @Query("""
        SELECT COUNT(a) FROM StaffAttendance a 
        WHERE a.teachingStaff.id = :staffId 
        AND a.status = :status
        AND a.attendanceDate BETWEEN :startDate AND :endDate
    """)
    fun countTeachingStaffAttendanceByStatus(
        @Param("staffId") staffId: Long,
        @Param("status") status: AttendanceStatus,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Long

    @Query("""
        SELECT COUNT(a) FROM StaffAttendance a 
        WHERE a.nonTeachingStaff.id = :staffId 
        AND a.status = :status
        AND a.attendanceDate BETWEEN :startDate AND :endDate
    """)
    fun countNonTeachingStaffAttendanceByStatus(
        @Param("staffId") staffId: Long,
        @Param("status") status: AttendanceStatus,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Long

    @Query("""
        SELECT SUM(a.hoursWorked) FROM StaffAttendance a 
        WHERE a.nonTeachingStaff.id = :staffId 
        AND a.attendanceDate BETWEEN :startDate AND :endDate
    """)
    fun sumHoursWorkedByNonTeachingStaff(
        @Param("staffId") staffId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Double?
}

interface TeachingAttendanceStatsProjection {
    val staffId: Long
    val presentDays: Long
    val absentDays: Long
    val lateDays: Long
}

