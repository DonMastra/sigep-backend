package com.sigep.tuition.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.ColumnDefault
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "tuition_academic_years",
    indexes = [Index(name = "idx_tuition_academic_year_status", columnList = "status")]
)
data class TuitionAcademicYear(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 100)
    val name: String,

    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "first_term_start_date", nullable = false)
    val firstTermStartDate: LocalDate,

    @Column(name = "first_term_end_date", nullable = false)
    val firstTermEndDate: LocalDate,

    @Column(name = "second_term_start_date", nullable = false)
    val secondTermStartDate: LocalDate,

    @Column(name = "second_term_end_date", nullable = false)
    val secondTermEndDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    val endDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: TuitionAcademicYearStatus = TuitionAcademicYearStatus.DRAFT,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(
    name = "tuition_levels",
    indexes = [
        Index(name = "idx_tuition_level_segment", columnList = "segment"),
        Index(name = "idx_tuition_level_active", columnList = "active")
    ]
)
data class TuitionLevel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    val code: String,

    @Column(nullable = false, length = 150)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val segment: TuitionSegment,

    @Column(name = "level_order", nullable = false)
    val levelOrder: Int,

    @Column(name = "course_level", length = 40)
    val courseLevel: String? = null,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(
    name = "tuition_level_progression",
    indexes = [
        Index(name = "idx_tuition_progression_from", columnList = "from_level_id"),
        Index(name = "idx_tuition_progression_to", columnList = "to_level_id")
    ]
)
data class TuitionLevelProgression(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_level_id", nullable = false)
    val fromLevel: TuitionLevel,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_level_id", nullable = false)
    val toLevel: TuitionLevel,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val rule: TuitionProgressionRule = TuitionProgressionRule.PASS_PREVIOUS_LEVEL,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(
    name = "tuition_fee_plans",
    indexes = [
        Index(name = "idx_tuition_fee_plan_year", columnList = "academic_year_id"),
        Index(name = "idx_tuition_fee_plan_status", columnList = "status"),
        Index(name = "idx_tuition_fee_plan_segment", columnList = "segment")
    ]
)
data class TuitionFeePlan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    val academicYear: TuitionAcademicYear,

    @Column(nullable = false, length = 120)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val segment: TuitionSegment? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    val level: TuitionLevel? = null,

    @Column(name = "enrollment_fee", nullable = false, precision = 12, scale = 2)
    val enrollmentFee: BigDecimal,

    @Column(name = "monthly_fee", nullable = false, precision = 12, scale = 2)
    val monthlyFee: BigDecimal,

    @Column(nullable = false)
    val installments: Int,

    @Column(nullable = false, length = 3)
    val currency: String = "ARS",

    @Column(name = "valid_from", nullable = false)
    val validFrom: LocalDate,

    @Column(name = "valid_to")
    val validTo: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: TuitionFeePlanStatus = TuitionFeePlanStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(
    name = "tuition_discounts",
    indexes = [
        Index(name = "idx_tuition_discount_student", columnList = "student_id"),
        Index(name = "idx_tuition_discount_segment", columnList = "segment"),
        Index(name = "idx_tuition_discount_active", columnList = "active")
    ]
)
data class TuitionDiscount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "student_id")
    val studentId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val segment: TuitionSegment? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    val level: TuitionLevel? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TuitionDiscountType,

    @Column(precision = 5, scale = 2)
    val percentage: BigDecimal? = null,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "valid_from", nullable = false)
    val validFrom: LocalDate,

    @Column(name = "valid_to")
    val validTo: LocalDate? = null,

    @Column(nullable = false, length = 500)
    val reason: String,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(
    name = "tuition_applications",
    indexes = [
        Index(name = "idx_tuition_application_guardian", columnList = "guardian_user_id"),
        Index(name = "idx_tuition_application_student", columnList = "student_id"),
        Index(name = "idx_tuition_application_course", columnList = "requested_course_id"),
        Index(name = "idx_tuition_application_status", columnList = "status"),
        Index(name = "idx_tuition_application_year", columnList = "academic_year_id")
    ]
)
data class TuitionApplication(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "guardian_user_id", nullable = false)
    val guardianUserId: Long,

    @Column(name = "student_id")
    val studentId: Long? = null,

    @Column(name = "student_first_name", length = 100)
    val studentFirstName: String? = null,

    @Column(name = "student_last_name", length = 100)
    val studentLastName: String? = null,

    @Column(name = "student_email")
    val studentEmail: String? = null,

    @Column(name = "student_document_number", length = 50)
    val studentDocumentNumber: String? = null,

    @Column(name = "student_date_of_birth")
    val studentDateOfBirth: LocalDate? = null,

    @Column(name = "student_address", length = 300)
    val studentAddress: String? = null,

    @Column(name = "student_phone_number", length = 50)
    val studentPhoneNumber: String? = null,

    @Column(name = "student_emergency_contact", length = 200)
    val studentEmergencyContact: String? = null,

    @Column(name = "student_medical_notes", length = 1000)
    val studentMedicalNotes: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    val academicYear: TuitionAcademicYear,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_level_id", nullable = false)
    val requestedLevel: TuitionLevel,

    @Column(name = "requested_course_id", nullable = false)
    val requestedCourseId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val applicationType: TuitionApplicationType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val status: TuitionApplicationStatus = TuitionApplicationStatus.SUBMITTED,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_plan_id", nullable = false)
    val feePlan: TuitionFeePlan,

    @Column(name = "enrollment_id")
    val enrollmentId: Long? = null,

    @Column(name = "warning_message", length = 1000)
    val warningMessage: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "progression_rule", length = 30)
    val progressionRule: TuitionProgressionRule? = null,

    @ColumnDefault("false")
    @Column(name = "requires_admin_override", nullable = false)
    val requiresAdminOverride: Boolean = false,

    @Column(name = "admin_notes", length = 1000)
    val adminNotes: String? = null,

    @Column(name = "submitted_at", nullable = false)
    val submittedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "approved_at")
    val approvedAt: LocalDateTime? = null,

    @Column(name = "approved_by")
    val approvedBy: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(
    name = "tuition_seat_reservations",
    indexes = [
        Index(name = "idx_tuition_seat_course", columnList = "course_id"),
        Index(name = "idx_tuition_seat_status", columnList = "status"),
        Index(name = "idx_tuition_seat_expires", columnList = "expires_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_tuition_seat_application", columnNames = ["application_id"])
    ]
)
data class TuitionSeatReservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.MERGE])
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    val application: TuitionApplication,

    @Column(name = "course_id", nullable = false)
    val courseId: Long,

    @Column(nullable = false)
    val quantity: Int = 1,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: TuitionSeatReservationStatus = TuitionSeatReservationStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

@Entity
@Table(
    name = "tuition_ledger_entries",
    indexes = [
        Index(name = "idx_tuition_ledger_application", columnList = "application_id"),
        Index(name = "idx_tuition_ledger_student", columnList = "student_id"),
        Index(name = "idx_tuition_ledger_status", columnList = "status"),
        Index(name = "idx_tuition_ledger_due_date", columnList = "due_date")
    ]
)
data class TuitionLedgerEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    val application: TuitionApplication,

    @Column(name = "student_id")
    val studentId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    val discount: TuitionDiscount? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val concept: TuitionLedgerConcept,

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    val grossAmount: BigDecimal,

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    val discountAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    val netAmount: BigDecimal,

    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: TuitionLedgerStatus = TuitionLedgerStatus.MOCK_PENDING,

    @Column(name = "mock_reference", unique = true, length = 100)
    val mockReference: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : AggregateRoot

enum class TuitionAcademicYearStatus { DRAFT, OPEN, CLOSED }
enum class TuitionSegment { CHILDREN, TEENS, ADULTS }
enum class TuitionProgressionRule { PASS_PREVIOUS_LEVEL, ADMIN_APPROVAL }
enum class TuitionFeePlanStatus { ACTIVE, INACTIVE }
enum class TuitionDiscountType { SCHOLARSHIP, DISCOUNT }
enum class TuitionApplicationType { NEW_STUDENT, REGULAR_PROMOTION, ADDITIONAL_STUDENT }
enum class TuitionApplicationStatus {
    DRAFT,
    SUBMITTED,
    SEAT_RESERVED,
    PAYMENT_PENDING,
    READY_FOR_ADMIN_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
enum class TuitionSeatReservationStatus { ACTIVE, CONFIRMED, RELEASED, EXPIRED }
enum class TuitionLedgerConcept { TUITION_ENROLLMENT, MONTHLY_FEE }
enum class TuitionLedgerStatus { MOCK_PENDING, MOCK_PAID, CANCELLED }
