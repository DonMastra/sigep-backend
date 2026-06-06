package com.sigep.scheduling.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "reservations",
    indexes = [
        Index(name = "idx_reservation_slot", columnList = "slot_id"),
        Index(name = "idx_reservation_status", columnList = "status"),
        Index(name = "idx_reservation_target", columnList = "target_type,target_id")
    ]
)
data class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    val slot: ScheduleSlot,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val targetType: ReservationTargetType = ReservationTargetType.NONE,

    @Column
    val targetId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val status: ReservationStatus = ReservationStatus.AVAILABLE,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

enum class ReservationTargetType { COURSE, SESSION, NONE }
enum class ReservationStatus { AVAILABLE, ASSIGNED, INACTIVE }
