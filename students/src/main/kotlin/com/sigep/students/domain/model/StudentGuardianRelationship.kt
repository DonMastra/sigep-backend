package com.sigep.students.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "student_guardian_relationships",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_student_guardian_relationship",
            columnNames = ["student_id", "guardian_user_id"]
        )
    ],
    indexes = [
        Index(name = "idx_student_guardian_relationship_student", columnList = "student_id,active"),
        Index(name = "idx_student_guardian_relationship_guardian", columnList = "guardian_user_id,active,can_view_academic"),
        Index(name = "idx_student_guardian_relationship_billing", columnList = "guardian_user_id,active,is_billing_contact")
    ]
)
data class StudentGuardianRelationship(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "student_id", nullable = false)
    val studentId: Long,

    @Column(name = "guardian_user_id", nullable = false)
    val guardianUserId: Long,

    @Column(name = "relationship_type", length = 32)
    val relationshipType: String? = null,

    @Column(name = "is_primary", nullable = false)
    val primary: Boolean = false,

    @Column(name = "can_view_academic", nullable = false)
    val canViewAcademic: Boolean = true,

    @Column(name = "is_billing_contact", nullable = false)
    val billingContact: Boolean = false,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "source_system", nullable = false, length = 80)
    val sourceSystem: String = "APPLICATION",

    @Column(name = "source_reference", length = 255)
    val sourceReference: String? = null,

    @Column(name = "reconciliation_run_id", length = 64)
    val reconciliationRunId: String? = null,

    @Column(name = "verified_by")
    val verifiedBy: Long? = null,

    @Column(name = "verified_at")
    val verifiedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    @Column(nullable = false)
    val version: Long = 0
) : AggregateRoot
