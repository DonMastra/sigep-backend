package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.model.EnrollmentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface EnrollmentRepository : JpaRepository<Enrollment, Long> {
    fun findByStudentId(studentId: Long, pageable: Pageable): Page<Enrollment>
    fun findByStudentIdAndCourseTeacherId(studentId: Long, teacherId: Long, pageable: Pageable): Page<Enrollment>
    fun findByStudentIdAndStatus(studentId: Long, status: EnrollmentStatus, pageable: Pageable): Page<Enrollment>
    fun findByCourseId(courseId: Long, pageable: Pageable): Page<Enrollment>
    fun findAllByCourseIdOrderByStudentIdAsc(courseId: Long): List<Enrollment>
    fun findByStudentIdAndCourseId(studentId: Long, courseId: Long): Optional<Enrollment>

    @Query("SELECT DISTINCT e.studentId FROM Enrollment e WHERE e.status = 'ACTIVE'")
    fun findActiveStudentIds(): Set<Long>

    @Query("SELECT DISTINCT e.studentId FROM Enrollment e WHERE e.course.teacherId = :teacherUserId AND e.status = 'ACTIVE'")
    fun findActiveStudentIdsByTeacher(teacherUserId: Long): Set<Long>

    @Query("SELECT DISTINCT e.studentId FROM Enrollment e WHERE e.course.id = :courseId AND e.status = 'ACTIVE'")
    fun findActiveStudentIdsByCourse(courseId: Long): Set<Long>

    @Query("SELECT COUNT(e) > 0 FROM Enrollment e WHERE e.studentId = :studentId AND e.course.teacherId = :teacherUserId AND e.status = 'ACTIVE'")
    fun existsActiveByStudentIdAndTeacherId(studentId: Long, teacherUserId: Long): Boolean

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course.id = :courseId AND e.status = 'ACTIVE'")
    fun countActiveEnrollmentsByCourse(courseId: Long): Long

    fun countByCourseId(courseId: Long): Long

    fun countByStatus(status: EnrollmentStatus): Long
}


