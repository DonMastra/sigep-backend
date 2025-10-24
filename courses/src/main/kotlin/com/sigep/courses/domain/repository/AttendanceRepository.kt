package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.Attendance
import com.sigep.courses.domain.model.AttendanceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface AttendanceRepository : JpaRepository<Attendance, Long> {

    fun findByEnrollmentId(enrollmentId: Long, pageable: Pageable): Page<Attendance>

    fun findByEnrollmentIdAndAttendanceDate(enrollmentId: Long, date: LocalDate): Optional<Attendance>

    @Query("SELECT a FROM Attendance a WHERE a.enrollment.course.id = :courseId AND a.attendanceDate = :date")
    fun findByCourseIdAndDate(courseId: Long, date: LocalDate): List<Attendance>

    @Query("SELECT a FROM Attendance a WHERE a.enrollment.id = :enrollmentId AND a.attendanceDate BETWEEN :startDate AND :endDate")
    fun findByEnrollmentIdAndDateRange(enrollmentId: Long, startDate: LocalDate, endDate: LocalDate): List<Attendance>

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.enrollment.id = :enrollmentId AND a.status = :status")
    fun countByEnrollmentIdAndStatus(enrollmentId: Long, status: AttendanceStatus): Long

    @Query("""
        SELECT COUNT(a) FROM Attendance a 
        WHERE a.enrollment.id = :enrollmentId 
        AND a.status IN ('PRESENT', 'LATE')
    """)
    fun countPresentByEnrollmentId(enrollmentId: Long): Long

    @Query("""
        SELECT COUNT(a) FROM Attendance a 
        WHERE a.enrollment.course.id = :courseId 
        AND a.attendanceDate = :date
        AND a.status IN ('PRESENT', 'LATE')
    """)
    fun countPresentByCourseIdAndDate(courseId: Long, date: LocalDate): Long

    @Query("""
        SELECT a FROM Attendance a 
        WHERE a.enrollment.course.id = :courseId 
        AND a.attendanceDate BETWEEN :startDate AND :endDate
    """)
    fun findByCourseIdAndDateRange(courseId: Long, startDate: LocalDate, endDate: LocalDate, pageable: Pageable): Page<Attendance>
}

