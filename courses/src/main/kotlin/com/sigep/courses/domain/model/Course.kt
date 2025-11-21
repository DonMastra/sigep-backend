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
    val level: CourseLevel, // Changed to enum for better validation

    @Column(nullable = false)
    val duration: Int, // Duration in hours

    @Column(nullable = false)
    val maxStudents: Int,

    @Column(nullable = false)
    val minStudents: Int = 1, // Minimum students to start the course

    @Column(nullable = false)
    val teacherId: Long, // Reference to User with TEACHER role

    @Column(nullable = false, precision = 10, scale = 2)
    val price: java.math.BigDecimal, // Course price

    @Column(nullable = true)
    val startDate: java.time.LocalDate? = null, // Course start date

    @Column(nullable = true)
    val endDate: java.time.LocalDate? = null, // Course end date

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: CourseStatus = CourseStatus.ACTIVE,

    @Column(nullable = false)
    val isPublished: Boolean = false, // If the course is visible to students

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true)
    val schedules: MutableList<CourseSchedule> = mutableListOf(),

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL])
    val enrollments: MutableList<Enrollment> = mutableListOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(name = "course_schedules")
data class CourseSchedule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val dayOfWeek: DayOfWeek,

    @Column(nullable = false)
    val startTime: String, // Format: HH:mm

    @Column(nullable = false)
    val endTime: String // Format: HH:mm
)

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

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
