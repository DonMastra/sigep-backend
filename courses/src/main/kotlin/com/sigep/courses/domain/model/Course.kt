package com.sigep.courses.domain.model

import com.sigep.common.domain.AggregateRoot
import com.sigep.students.domain.model.Student
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "courses")
data class Course(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, length = 1000)
    val description: String,

    @Column(nullable = false)
    val level: String, // Beginner, Intermediate, Advanced

    @Column(nullable = false)
    val duration: Int, // Duration in hours

    @Column(nullable = false)
    val maxStudents: Int,

    @Column(nullable = false)
    val teacherId: Long, // Reference to User with TEACHER role

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: CourseStatus = CourseStatus.ACTIVE,

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true)
    val schedules: MutableList<CourseSchedule> = mutableListOf(),

    @ManyToMany
    @JoinTable(
        name = "course_enrollments",
        joinColumns = [JoinColumn(name = "course_id")],
        inverseJoinColumns = [JoinColumn(name = "student_id")]
    )
    val students: MutableSet<Student> = mutableSetOf(), // Changed from Long to Student entity

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

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
