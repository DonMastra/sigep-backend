package com.sigep.students.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "student_guardian_link_events",
    indexes = [
        Index(name = "idx_student_guardian_link_student", columnList = "student_id,created_at"),
        Index(name = "idx_student_guardian_link_guardian", columnList = "guardian_user_id")
    ]
)
data class StudentGuardianLinkEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "student_id", nullable = false)
    val studentId: Long,

    @Column(name = "previous_guardian_user_id")
    val previousGuardianUserId: Long? = null,

    @Column(name = "guardian_user_id")
    val guardianUserId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val action: StudentGuardianLinkAction,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val origin: StudentGuardianLinkOrigin,

    @Column(name = "actor_user_id", nullable = false)
    val actorUserId: Long,

    @Column(length = 500)
    val reason: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class StudentGuardianLinkAction { LINKED, REASSIGNED, UNLINKED }
enum class StudentGuardianLinkOrigin { ADMIN, GUARDIAN, TUITION }
