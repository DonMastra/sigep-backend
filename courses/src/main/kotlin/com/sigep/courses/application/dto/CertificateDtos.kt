package com.sigep.courses.application.dto

import com.sigep.courses.domain.model.CertificateStatus
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CertificateDto(
    val id: Long,
    val enrollmentId: Long,
    val studentId: Long,
    val studentName: String? = null,
    val courseId: Long,
    val courseName: String,
    val certificateCode: String,
    val issueDate: LocalDate,
    val expiryDate: LocalDate?,
    val finalGrade: BigDecimal,
    val honors: String?,
    val notes: String?,
    val pdfUrl: String?,
    val status: CertificateStatus,
    val issuedBy: Long,
    val issuedByName: String? = null,
    val revokedBy: Long?,
    val revokedAt: LocalDateTime?,
    val revocationReason: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateCertificateRequest(
    @field:NotNull(message = "Enrollment ID is required")
    val enrollmentId: Long,

    @field:NotNull(message = "Issue date is required")
    val issueDate: LocalDate = LocalDate.now(),

    val expiryDate: LocalDate? = null,

    @field:NotNull(message = "Final grade is required")
    @field:DecimalMin(value = "0.0", message = "Grade must be at least 0")
    @field:DecimalMax(value = "100.0", message = "Grade cannot exceed 100")
    val finalGrade: BigDecimal,

    @field:Size(max = 500)
    val honors: String? = null,

    @field:Size(max = 1000)
    val notes: String? = null
)

data class UpdateCertificateRequest(
    val expiryDate: LocalDate?,

    @field:DecimalMin(value = "0.0", message = "Grade must be at least 0")
    @field:DecimalMax(value = "100.0", message = "Grade cannot exceed 100")
    val finalGrade: BigDecimal?,

    @field:Size(max = 500)
    val honors: String?,

    @field:Size(max = 1000)
    val notes: String?,

    val pdfUrl: String?
)

data class RevokeCertificateRequest(
    @field:NotBlank(message = "Revocation reason is required")
    @field:Size(max = 500)
    val reason: String
)

data class VerifyCertificateDto(
    val isValid: Boolean,
    val certificateCode: String,
    val studentName: String? = null,
    val courseName: String,
    val issueDate: LocalDate,
    val finalGrade: BigDecimal,
    val status: CertificateStatus,
    val message: String
)

data class CertificateStatisticsDto(
    val totalCertificates: Long,
    val activeCertificates: Long,
    val revokedCertificates: Long,
    val expiredCertificates: Long,
    val certificatesByCourse: Map<String, Long>, // Course name -> count
    val recentCertificates: List<CertificateDto> // Last 10 issued
)

