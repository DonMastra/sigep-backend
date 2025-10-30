package com.sigep.courses.domain.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "course_certificates", indexes = [
    Index(name = "idx_certificate_enrollment", columnList = "enrollment_id"),
    Index(name = "idx_certificate_code", columnList = "certificate_code", unique = true)
])
data class Certificate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false, unique = true)
    val enrollment: Enrollment,

    @Column(nullable = false, unique = true)
    val certificateCode: String, // Unique code for verification (e.g., CERT-2024-001234)

    @Column(nullable = false)
    val issueDate: LocalDate,

    @Column
    val expiryDate: LocalDate? = null, // Some certificates may expire

    @Column(nullable = false, precision = 5, scale = 2)
    val finalGrade: java.math.BigDecimal, // Final grade to appear on certificate

    @Column(length = 500)
    val honors: String? = null, // e.g., "With Distinction", "With High Honors"

    @Column(length = 1000)
    val notes: String? = null, // Additional notes

    @Column
    val pdfUrl: String? = null, // URL to generated PDF certificate

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: CertificateStatus = CertificateStatus.ACTIVE,

    @Column(nullable = false)
    val issuedBy: Long, // ID of the user who issued the certificate

    @Column
    val revokedBy: Long? = null, // ID of the user who revoked it (if revoked)

    @Column
    val revokedAt: LocalDateTime? = null,

    @Column(length = 500)
    val revocationReason: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class CertificateStatus {
    ACTIVE,      // Certificate is valid
    REVOKED,     // Certificate has been revoked
    EXPIRED      // Certificate has expired
}

