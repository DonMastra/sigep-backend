package com.sigep.staff.domain.model

import com.sigep.common.infrastructure.audit.AuditMetadata
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "non_teaching_staff")
data class NonTeachingStaff(
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
    val hourlyRate: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    val role: NonTeachingRole? = null,

    @Column(nullable = true)
    val companyName: String? = null,

    @Column(columnDefinition = "TEXT")
    val assignedTasks: String? = null,

    @Column(columnDefinition = "TEXT")
    val observations: String? = null,

    @Column(nullable = false)
    val emergencyContactName: String,

    @Column(nullable = false)
    val emergencyContactPhone: String
) : AuditMetadata() {

    val fullName: String
        get() = "$firstName $lastName"
}

enum class NonTeachingRole {
    CLEANING,
    MAINTENANCE,
    IT_SUPPORT,
    /** Alias de IT_SUPPORT para compatibilidad con el frontend */
    IT,
    SECURITY,
    ADMINISTRATION,
    OTHER
}

