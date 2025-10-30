package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.CourseSession
import com.sigep.courses.domain.model.SessionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalTime

@Repository
interface CourseSessionRepository : JpaRepository<CourseSession, Long> {

    fun findByCourseId(courseId: Long, pageable: Pageable): Page<CourseSession>

    fun findByCourseIdAndSessionDateBetween(
        courseId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<CourseSession>

    fun findBySessionDate(date: LocalDate): List<CourseSession>

    fun findByClassroomIdAndSessionDate(classroomId: Long, date: LocalDate): List<CourseSession>

    @Query("""
        SELECT s FROM CourseSession s 
        WHERE s.course.teacherId = :teacherId 
        AND s.sessionDate = :date
    """)
    fun findByTeacherIdAndDate(teacherId: Long, date: LocalDate): List<CourseSession>

    @Query("""
        SELECT s FROM CourseSession s 
        WHERE s.classroomId = :classroomId 
        AND s.sessionDate = :date
        AND s.status NOT IN ('CANCELLED')
        AND (
            (s.startTime < :endTime AND s.endTime > :startTime)
        )
    """)
    fun findClassroomConflicts(
        classroomId: Long,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime
    ): List<CourseSession>

    @Query("""
        SELECT s FROM CourseSession s 
        WHERE s.course.teacherId = :teacherId 
        AND s.sessionDate = :date
        AND s.status NOT IN ('CANCELLED')
        AND (
            (s.startTime < :endTime AND s.endTime > :startTime)
        )
    """)
    fun findTeacherConflicts(
        teacherId: Long,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime
    ): List<CourseSession>

    @Query("""
        SELECT s FROM CourseSession s 
        JOIN Enrollment e ON e.course.id = s.course.id
        WHERE e.studentId = :studentId 
        AND s.sessionDate = :date
        AND s.status NOT IN ('CANCELLED')
        AND e.status = 'ACTIVE'
        AND (
            (s.startTime < :endTime AND s.endTime > :startTime)
        )
    """)
    fun findStudentConflicts(
        studentId: Long,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime
    ): List<CourseSession>

    fun countByCourseId(courseId: Long): Long

    fun countByCourseIdAndStatus(courseId: Long, status: SessionStatus): Long
}

