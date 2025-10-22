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
    fun findByStudentIdAndStatus(studentId: Long, status: EnrollmentStatus, pageable: Pageable): Page<Enrollment>
    fun findByCourseId(courseId: Long, pageable: Pageable): Page<Enrollment>
    fun findByStudentIdAndCourseId(studentId: Long, courseId: Long): Optional<Enrollment>

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.course.id = :courseId AND e.status = 'ACTIVE'")
    fun countActiveEnrollmentsByCourse(courseId: Long): Long
}


