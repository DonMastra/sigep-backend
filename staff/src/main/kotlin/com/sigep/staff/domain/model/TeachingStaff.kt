package com.sigep.staff.domain.model

import com.sigep.common.infrastructure.audit.AuditMetadata
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "teaching_staff")
data class TeachingStaff(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val firstName: String,

    @Column(nullable = false)
    val lastName: String,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    val phoneNumber: String,

    @Column(unique = true, nullable = false)
    val documentNumber: String,

    @Column(nullable = false)
    val birthDate: LocalDate,

    @Column(nullable = false)
    val address: String,

    @Column(nullable = false)
    val hireDate: LocalDate,

    @Column(nullable = false)
    val monthlySalary: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val paymentStatus: PaymentStatus = PaymentStatus.UP_TO_DATE,

    @Column(nullable = false)
    val assignedStudentsCount: Int = 0,

    @Column(columnDefinition = "TEXT")
    val specialization: String? = null,

    /** Titulaciones y certificaciones del docente */
    @Column(columnDefinition = "TEXT")
    val qualifications: String? = null,

    @Column(columnDefinition = "TEXT")
    val observations: String? = null,

    @Column(columnDefinition = "TEXT")
    val notes: String? = null,

    @Column(nullable = false)
    val emergencyContactName: String,

    @Column(nullable = false)
    val emergencyContactPhone: String
) : AuditMetadata() {

    val fullName: String
        get() = "$firstName $lastName"
}

enum class PaymentStatus {
    UP_TO_DATE,
    PENDING,
    OVERDUE,
    PARTIALLY_PAID
}

