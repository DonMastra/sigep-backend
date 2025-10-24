package com.sigep.courses.domain.repository

import com.sigep.courses.domain.model.Certificate
import com.sigep.courses.domain.model.CertificateStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface CertificateRepository : JpaRepository<Certificate, Long> {

    fun findByCertificateCode(certificateCode: String): Optional<Certificate>

    fun existsByCertificateCode(certificateCode: String): Boolean

    fun findByEnrollmentId(enrollmentId: Long): Optional<Certificate>

    fun existsByEnrollmentId(enrollmentId: Long): Boolean

    @Query("SELECT c FROM Certificate c WHERE c.enrollment.studentId = :studentId")
    fun findByStudentId(studentId: Long, pageable: Pageable): Page<Certificate>

    @Query("SELECT c FROM Certificate c WHERE c.enrollment.course.id = :courseId")
    fun findByCourseId(courseId: Long, pageable: Pageable): Page<Certificate>

    fun findByStatus(status: CertificateStatus, pageable: Pageable): Page<Certificate>

    @Query("SELECT c FROM Certificate c WHERE c.expiryDate < :date AND c.status = 'ACTIVE'")
    fun findExpiredCertificates(date: LocalDate): List<Certificate>

    @Query("SELECT COUNT(c) FROM Certificate c WHERE c.enrollment.course.id = :courseId")
    fun countByCourseId(courseId: Long): Long

    fun countByStatus(status: CertificateStatus): Long
}

