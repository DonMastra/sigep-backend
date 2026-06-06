package com.sigep.scheduling.domain.model
import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDateTime
@Entity
@Table(
    name = "schedule_slots",
    indexes = [
        Index(name = "idx_slot_classroom", columnList = "classroom_id"),
        Index(name = "idx_slot_day", columnList = "day_of_week")
    ]
)
data class ScheduleSlot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    val classroom: Classroom,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val dayOfWeek: SlotDayOfWeek,
    /** HH:mm format */
    @Column(nullable = false, length = 5)
    val startTime: String,
    /** HH:mm format */
    @Column(nullable = false, length = 5)
    val endTime: String,
    /** Soft delete */
    @Column(nullable = false)
    val active: Boolean = true,
    @Column(length = 500)
    val notes: String? = null,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot
enum class SlotDayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
