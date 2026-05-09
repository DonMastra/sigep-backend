package com.sigep.security.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "registration_requests")
data class RegistrationRequest(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(nullable = false)
    val username: String,

    @Column(nullable = false)
    val email: String,

    @Column(nullable = false)
    val firstName: String,

    @Column(nullable = false)
    val lastName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val requestedRole: UserRole,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AccountStatus = AccountStatus.PENDING_APPROVAL,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    val reviewedAt: LocalDateTime? = null,

    val reviewedBy: Long? = null,

    @Column(length = 1000)
    val adminNotes: String? = null
) : AggregateRoot

