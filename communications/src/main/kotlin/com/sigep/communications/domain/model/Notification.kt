package com.sigep.communications.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "notifications")
data class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false, length = 2000)
    val message: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: NotificationType,

    @Column(nullable = false)
    val recipientId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val recipientType: RecipientType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: NotificationStatus = NotificationStatus.SENT,

    @Column(nullable = false)
    val sendDate: LocalDateTime = LocalDateTime.now(),

    @Column
    val readDate: LocalDateTime?,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

enum class NotificationType {
    INFO,
    WARNING,
    ERROR,
    SUCCESS,
    REMINDER
}

enum class RecipientType {
    STUDENT,
    TEACHER,
    GUARDIAN,
    ADMIN
}

enum class NotificationStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED
}

