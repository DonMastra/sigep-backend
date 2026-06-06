package com.sigep.scheduling.domain.model
import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDateTime
@Entity
@Table(
    name = "classrooms",
    indexes = [Index(name = "idx_classroom_name", columnList = "name")]
)
data class Classroom(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, length = 100)
    val name: String,
    @Column(length = 100)
    val building: String? = null,
    @Column(length = 20)
    val floor: String? = null,
    @Column(nullable = false)
    val capacity: Int,
    /** Soft delete — inactive classrooms are not assignable */
    @Column(nullable = false)
    val active: Boolean = true,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot
