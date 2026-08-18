package com.sigep.security.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    val username: String,

    @Column(unique = true, nullable = false)

    val email: String,

    @Column(nullable = false)
    val password: String,

    @Column(nullable = false)
    val firstName: String,

    @Column(nullable = false)
    val lastName: String,

    @Column(name = "phone_number", nullable = true)
    val phoneNumber: String? = null,

    @Column(name = "address", nullable = true)
    val address: String? = null,

    @Column(name = "date_of_birth", nullable = true)
    val dateOfBirth: LocalDate? = null,

    @Column(name = "document_number", nullable = true)
    val documentNumber: String? = null,

    @Column(name = "emergency_contact", nullable = true)
    val emergencyContact: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: AccountStatus = AccountStatus.ACTIVE,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "must_change_password", nullable = false)
    val mustChangePassword: Boolean = false,

    @Column(name = "password_changed_at")
    val passwordChangedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

enum class UserRole {
    ADMIN,
    TEACHER,
    GUARDIAN
}

enum class AccountStatus {
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED
}

