package com.sigep.security.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_role_assignments",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_role_assignment", columnNames = ["user_id", "role"])]
)
data class UserRoleAssignment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val role: UserRole,

    @Column(name = "assigned_at", nullable = false)
    val assignedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "assigned_by")
    val assignedBy: Long? = null,

    @Column(name = "revoked_at")
    val revokedAt: LocalDateTime? = null,

    @Column(name = "revoked_by")
    val revokedBy: Long? = null
) {
    val isActive: Boolean
        get() = revokedAt == null
}

@Entity
@Table(name = "user_role_context_events")
data class UserRoleContextEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", length = 32)
    val previousRole: UserRole? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "active_role", nullable = false, length = 32)
    val activeRole: UserRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    val eventType: UserRoleContextEventType,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class UserRoleContextEventType {
    LOGIN,
    LOGIN_SELECTION,
    SWITCH
}
