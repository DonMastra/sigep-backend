package com.sigep.courses.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.BusinessException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.event.CourseEventPublisher
import com.sigep.courses.domain.event.CertificateIssuedEvent
import com.sigep.courses.domain.model.Certificate
import com.sigep.courses.domain.model.CertificateStatus
import com.sigep.courses.domain.model.EnrollmentStatus
import com.sigep.courses.domain.repository.CertificateRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class CertificateService(
    private val certificateRepository: CertificateRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val eventPublisher: CourseEventPublisher
) {

    private val logger = LoggerFactory.getLogger(CertificateService::class.java)

    fun getCertificateById(id: Long): CertificateDto {
        logger.info("Fetching certificate with id: {}", id)
        val certificate = certificateRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Certificate not found with id: $id") }
        return certificate.toDto()
    }

    fun getCertificateByCode(code: String): CertificateDto {
        logger.info("Fetching certificate with code: {}", code)
        val certificate = certificateRepository.findByCertificateCode(code)
            .orElseThrow { ResourceNotFoundException("Certificate not found with code: $code") }
        return certificate.toDto()
    }

    fun getCertificatesByStudent(studentId: Long, page: Int, size: Int): PageResponse<CertificateDto> {
        logger.info("Fetching certificates for student: {}", studentId)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issueDate"))
        val certificatesPage = certificateRepository.findByStudentId(studentId, pageable)

        return PageResponse(
            content = certificatesPage.content.map { it.toDto() },
            page = certificatesPage.number,
            size = certificatesPage.size,
            totalElements = certificatesPage.totalElements,
            totalPages = certificatesPage.totalPages
        )
    }

    fun getCertificatesByCourse(courseId: Long, page: Int, size: Int): PageResponse<CertificateDto> {
        logger.info("Fetching certificates for course: {}", courseId)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issueDate"))
        val certificatesPage = certificateRepository.findByCourseId(courseId, pageable)

        return PageResponse(
            content = certificatesPage.content.map { it.toDto() },
            page = certificatesPage.number,
            size = certificatesPage.size,
            totalElements = certificatesPage.totalElements,
            totalPages = certificatesPage.totalPages
        )
    }

    fun issueCertificate(request: CreateCertificateRequest, issuedBy: Long): CertificateDto {
        logger.info("Issuing certificate for enrollment: {}", request.enrollmentId)

        val enrollment = enrollmentRepository.findById(request.enrollmentId)
            .orElseThrow { ResourceNotFoundException("Enrollment not found with id: ${request.enrollmentId}") }

        // Verificar que el enrollment esté completado
        if (enrollment.status != EnrollmentStatus.COMPLETED) {
            throw BusinessException("Cannot issue certificate. Enrollment status must be COMPLETED")
        }

        // Verificar que no exista ya un certificado para este enrollment
        if (certificateRepository.existsByEnrollmentId(request.enrollmentId)) {
            throw BusinessException("Certificate already exists for this enrollment")
        }

        // Verificar que tenga nota final
        if (enrollment.finalGrade == null) {
            throw BusinessException("Cannot issue certificate. Enrollment does not have a final grade")
        }

        // Generar código único de certificado
        val certificateCode = generateUniqueCertificateCode()

        // Determinar honores basado en la nota
        val honors = determineHonors(request.finalGrade)

        val certificate = Certificate(
            enrollment = enrollment,
            certificateCode = certificateCode,
            issueDate = request.issueDate,
            expiryDate = request.expiryDate,
            finalGrade = request.finalGrade,
            honors = request.honors ?: honors,
            notes = request.notes,
            status = CertificateStatus.ACTIVE,
            issuedBy = issuedBy,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedCertificate = certificateRepository.save(certificate)
        logger.info("Certificate issued successfully with code: {}", savedCertificate.certificateCode)

        // Publish event for notifications
        eventPublisher.publishCertificateIssued(
            CertificateIssuedEvent(
                certificateId = savedCertificate.id!!,
                certificateCode = savedCertificate.certificateCode,
                studentId = enrollment.studentId,
                courseId = enrollment.course.id!!,
                courseName = enrollment.course.name,
                finalGrade = request.finalGrade,
                honors = savedCertificate.honors,
                issueDate = request.issueDate
            )
        )

        return savedCertificate.toDto()
    }

    fun updateCertificate(id: Long, request: UpdateCertificateRequest): CertificateDto {
        logger.info("Updating certificate with id: {}", id)

        val certificate = certificateRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Certificate not found with id: $id") }

        if (certificate.status == CertificateStatus.REVOKED) {
            throw BusinessException("Cannot update a revoked certificate")
        }

        val updatedCertificate = certificate.copy(
            expiryDate = request.expiryDate ?: certificate.expiryDate,
            finalGrade = request.finalGrade ?: certificate.finalGrade,
            honors = request.honors ?: certificate.honors,
            notes = request.notes ?: certificate.notes,
            pdfUrl = request.pdfUrl ?: certificate.pdfUrl,
            updatedAt = LocalDateTime.now()
        )

        val savedCertificate = certificateRepository.save(updatedCertificate)
        logger.info("Certificate updated successfully with id: {}", savedCertificate.id)

        return savedCertificate.toDto()
    }

    fun revokeCertificate(id: Long, request: RevokeCertificateRequest, revokedBy: Long): CertificateDto {
        logger.info("Revoking certificate with id: {}", id)

        val certificate = certificateRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Certificate not found with id: $id") }

        if (certificate.status == CertificateStatus.REVOKED) {
            throw BusinessException("Certificate is already revoked")
        }

        val revokedCertificate = certificate.copy(
            status = CertificateStatus.REVOKED,
            revokedBy = revokedBy,
            revokedAt = LocalDateTime.now(),
            revocationReason = request.reason,
            updatedAt = LocalDateTime.now()
        )

        val savedCertificate = certificateRepository.save(revokedCertificate)
        logger.info("Certificate revoked successfully with id: {}", savedCertificate.id)

        return savedCertificate.toDto()
    }

    fun verifyCertificate(certificateCode: String): VerifyCertificateDto {
        logger.info("Verifying certificate with code: {}", certificateCode)

        val certificateOpt = certificateRepository.findByCertificateCode(certificateCode)

        if (certificateOpt.isEmpty) {
            return VerifyCertificateDto(
                isValid = false,
                certificateCode = certificateCode,
                courseName = "Unknown",
                issueDate = LocalDate.now(),
                finalGrade = java.math.BigDecimal.ZERO,
                status = CertificateStatus.REVOKED,
                message = "Certificate not found"
            )
        }

        val certificate = certificateOpt.get()

        // Verificar si está expirado
        if (certificate.expiryDate != null && certificate.expiryDate.isBefore(LocalDate.now())) {
            if (certificate.status != CertificateStatus.EXPIRED) {
                val expiredCert = certificate.copy(status = CertificateStatus.EXPIRED)
                certificateRepository.save(expiredCert)
            }
        }

        val isValid = certificate.status == CertificateStatus.ACTIVE
        val message = when (certificate.status) {
            CertificateStatus.ACTIVE -> "Certificate is valid and active"
            CertificateStatus.REVOKED -> "Certificate has been revoked: ${certificate.revocationReason ?: "No reason provided"}"
            CertificateStatus.EXPIRED -> "Certificate has expired on ${certificate.expiryDate}"
        }

        return VerifyCertificateDto(
            isValid = isValid,
            certificateCode = certificateCode,
            studentName = null, // Can be populated if needed
            courseName = certificate.enrollment.course.name,
            issueDate = certificate.issueDate,
            finalGrade = certificate.finalGrade,
            status = certificate.status,
            message = message
        )
    }

    fun getCertificateStatistics(): CertificateStatisticsDto {
        logger.info("Calculating certificate statistics")

        val totalCertificates = certificateRepository.count()
        val activeCertificates = certificateRepository.countByStatus(CertificateStatus.ACTIVE)
        val revokedCertificates = certificateRepository.countByStatus(CertificateStatus.REVOKED)
        val expiredCertificates = certificateRepository.countByStatus(CertificateStatus.EXPIRED)

        // Get recent certificates
        val recentCerts = certificateRepository.findAll(
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "issueDate"))
        ).content.map { it.toDto() }

        // Group by course (simplified version - in real scenario you might want to optimize this)
        val allCertificates = certificateRepository.findAll()
        val certificatesByCourse = allCertificates
            .groupBy { it.enrollment.course.name }
            .mapValues { it.value.size.toLong() }

        return CertificateStatisticsDto(
            totalCertificates = totalCertificates,
            activeCertificates = activeCertificates,
            revokedCertificates = revokedCertificates,
            expiredCertificates = expiredCertificates,
            certificatesByCourse = certificatesByCourse,
            recentCertificates = recentCerts
        )
    }

    fun processExpiredCertificates(): Int {
        logger.info("Processing expired certificates")

        val expiredCertificates = certificateRepository.findExpiredCertificates(LocalDate.now())

        expiredCertificates.forEach { certificate ->
            val expiredCert = certificate.copy(
                status = CertificateStatus.EXPIRED,
                updatedAt = LocalDateTime.now()
            )
            certificateRepository.save(expiredCert)
        }

        logger.info("Processed {} expired certificates", expiredCertificates.size)
        return expiredCertificates.size
    }

    private fun generateUniqueCertificateCode(): String {
        val year = LocalDate.now().year
        var code: String
        var attempts = 0

        do {
            val random = UUID.randomUUID().toString().substring(0, 8).uppercase()
            code = "CERT-$year-$random"
            attempts++

            if (attempts > 100) {
                throw BusinessException("Unable to generate unique certificate code after 100 attempts")
            }
        } while (certificateRepository.existsByCertificateCode(code))

        return code
    }

    private fun determineHonors(grade: java.math.BigDecimal): String? {
        return when {
            grade >= java.math.BigDecimal("95") -> "With Highest Honors"
            grade >= java.math.BigDecimal("90") -> "With High Honors"
            grade >= java.math.BigDecimal("85") -> "With Honors"
            else -> null
        }
    }

    private fun Certificate.toDto() = CertificateDto(
        id = id!!,
        enrollmentId = enrollment.id!!,
        studentId = enrollment.studentId,
        studentName = null, // Can be populated via join if needed
        courseId = enrollment.course.id!!,
        courseName = enrollment.course.name,
        certificateCode = certificateCode,
        issueDate = issueDate,
        expiryDate = expiryDate,
        finalGrade = finalGrade,
        honors = honors,
        notes = notes,
        pdfUrl = pdfUrl,
        status = status,
        issuedBy = issuedBy,
        issuedByName = null, // Can be populated if needed
        revokedBy = revokedBy,
        revokedAt = revokedAt,
        revocationReason = revocationReason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

