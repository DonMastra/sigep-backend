package com.sigep.exams.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "exams")
data class Exam(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val courseId: Long,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, length = 1000)
    val description: String,

    @Column(nullable = false)
    val examDate: LocalDateTime,

    @Column(nullable = false)
    val duration: Int, // Duration in minutes

    @Column(nullable = false)
    val maxScore: Int,

    @Column(nullable = false)
    val passingScore: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ExamType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ExamStatus = ExamStatus.SCHEDULED,

    @OneToMany(mappedBy = "exam", cascade = [CascadeType.ALL], orphanRemoval = true)
    val results: MutableList<ExamResult> = mutableListOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(name = "exam_results")
data class ExamResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    val exam: Exam,

    @Column(nullable = false)
    val studentId: Long,

    @Column(nullable = false)
    val score: Int,

    @Column(nullable = false)
    val passed: Boolean,

    @Column(length = 1000)
    val feedback: String?,

    @Column(nullable = false)
    val submittedAt: LocalDateTime = LocalDateTime.now()
)

enum class ExamType {
    WRITTEN,
    ORAL,
    LISTENING,
    READING,
    MIXED
}

enum class ExamStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

