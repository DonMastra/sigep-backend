package com.sigep.courses.domain.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity
@Table(name = "course_sessions", indexes = [
    Index(name = "idx_session_course", columnList = "course_id"),
    Index(name = "idx_session_date", columnList = "session_date"),
    Index(name = "idx_session_classroom", columnList = "classroom_id")
])
data class CourseSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Column(nullable = false)
    val sessionDate: LocalDate,

    @Column(nullable = false)
    val startTime: LocalTime,

    @Column(nullable = false)
    val endTime: LocalTime,

    @Column
    val classroomId: Long? = null, // Reference to classroom (from another module or simple ID)

    @Column
    val classroomName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: SessionStatus = SessionStatus.SCHEDULED,

    @Column(length = 1000)
    val topic: String? = null, // Topic or agenda for this session

    @Column(length = 1000)
    val notes: String? = null,

    @Column(nullable = false)
    val isRecurring: Boolean = false, // If this session is part of a recurring pattern

    @Column
    val recurrenceRule: String? = null, // RRULE format for recurring sessions

    @Column
    val parentSessionId: Long? = null, // Reference to the original session if this is an instance of recurring

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "session_exceptions", indexes = [
    Index(name = "idx_exception_session", columnList = "session_id"),
    Index(name = "idx_exception_date", columnList = "exception_date")
])
data class SessionException(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    val session: CourseSession,

    @Column(nullable = false)
    val exceptionDate: LocalDate, // Date when the exception applies

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val exceptionType: ExceptionType,

    @Column
    val newStartTime: LocalTime? = null, // If rescheduled

    @Column
    val newEndTime: LocalTime? = null, // If rescheduled

    @Column
    val newClassroomId: Long? = null, // If classroom changed

    @Column(length = 500)
    val reason: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class SessionStatus {
    SCHEDULED,   // Session is scheduled
    IN_PROGRESS, // Session is currently happening
    COMPLETED,   // Session has ended
    CANCELLED,   // Session was cancelled
    RESCHEDULED  // Session was moved to another date/time
}

enum class ExceptionType {
    CANCELLED,    // Session is cancelled on this date
    RESCHEDULED,  // Session is moved to different time
    HOLIDAY,      // Holiday or institutional closure
    CLASSROOM_CHANGE // Only classroom changed
}

