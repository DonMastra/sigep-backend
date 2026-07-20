package com.sigep.courses.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "courses")
data class Course(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val code: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, length = 1000)
    val description: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val level: CourseLevel,

    @Column(nullable = false)
    val duration: Int, // Duration in hours

    @Column(nullable = false)
    val maxStudents: Int,

    @Column(nullable = false)
    val minStudents: Int = 1,

    @Column(nullable = true)
    val teacherId: Long? = null,

    @Column(nullable = false, precision = 10, scale = 2)
    val price: java.math.BigDecimal,

    @Column(nullable = true)
    val startDate: java.time.LocalDate? = null,

    @Column(nullable = true)
    val endDate: java.time.LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: CourseStatus = CourseStatus.INACTIVE,

    @Column(nullable = false)
    val isPublished: Boolean = false,

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL])
    val enrollments: MutableList<Enrollment> = mutableListOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

enum class CourseStatus {
    ACTIVE,
    INACTIVE,
    COMPLETED,
    CANCELLED
}

enum class CourseLevel {
    BEGINNER,
    ELEMENTARY,
    PRE_INTERMEDIATE,
    INTERMEDIATE,
    UPPER_INTERMEDIATE,
    ADVANCED,
    PROFICIENCY
}
