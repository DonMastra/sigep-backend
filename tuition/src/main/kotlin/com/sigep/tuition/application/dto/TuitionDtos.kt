package com.sigep.tuition.application.dto

import com.sigep.tuition.domain.model.TuitionAcademicYearStatus
import com.sigep.tuition.domain.model.TuitionApplicationStatus
import com.sigep.tuition.domain.model.TuitionApplicationType
import com.sigep.tuition.domain.model.TuitionDiscountType
import com.sigep.tuition.domain.model.TuitionFeePlanStatus
import com.sigep.tuition.domain.model.TuitionEnrollmentFeePolicyStatus
import com.sigep.tuition.domain.model.TuitionLedgerConcept
import com.sigep.tuition.domain.model.TuitionLedgerStatus
import com.sigep.tuition.domain.model.TuitionProgressionRule
import com.sigep.tuition.domain.model.TuitionSegment
import com.sigep.tuition.domain.model.TuitionPlacementStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class TuitionAcademicYearDto(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val firstTermStartDate: LocalDate,
    val firstTermEndDate: LocalDate,
    val secondTermStartDate: LocalDate,
    val secondTermEndDate: LocalDate,
    val endDate: LocalDate,
    val status: TuitionAcademicYearStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTuitionAcademicYearRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    @field:NotNull val startDate: LocalDate,
    @field:NotNull val firstTermStartDate: LocalDate,
    @field:NotNull val firstTermEndDate: LocalDate,
    @field:NotNull val secondTermStartDate: LocalDate,
    @field:NotNull val secondTermEndDate: LocalDate,
    @field:NotNull val endDate: LocalDate,
    val status: TuitionAcademicYearStatus = TuitionAcademicYearStatus.DRAFT
)

data class UpdateTuitionAcademicYearRequest(
    @field:Size(max = 100)
    val name: String? = null,
    val startDate: LocalDate? = null,
    val firstTermStartDate: LocalDate? = null,
    val firstTermEndDate: LocalDate? = null,
    val secondTermStartDate: LocalDate? = null,
    val secondTermEndDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val status: TuitionAcademicYearStatus? = null
)

data class TuitionLevelDto(
    val id: Long,
    val code: String,
    val name: String,
    val segment: TuitionSegment,
    val levelOrder: Int,
    val courseLevel: String?,
    val active: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTuitionLevelRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val code: String,
    @field:NotBlank
    @field:Size(max = 150)
    val name: String,
    @field:NotNull
    val segment: TuitionSegment,
    @field:Min(1)
    val levelOrder: Int,
    @field:Pattern(regexp = "^(BEGINNER|ELEMENTARY|PRE_INTERMEDIATE|INTERMEDIATE|UPPER_INTERMEDIATE|ADVANCED|PROFICIENCY)$")
    val courseLevel: String? = null,
    val active: Boolean = true
)

data class UpdateTuitionLevelRequest(
    @field:Size(max = 50)
    val code: String? = null,
    @field:Size(max = 150)
    val name: String? = null,
    val segment: TuitionSegment? = null,
    @field:Min(1)
    val levelOrder: Int? = null,
    @field:Pattern(regexp = "^(BEGINNER|ELEMENTARY|PRE_INTERMEDIATE|INTERMEDIATE|UPPER_INTERMEDIATE|ADVANCED|PROFICIENCY)$")
    val courseLevel: String? = null,
    val active: Boolean? = null
)

data class TuitionLevelProgressionDto(
    val id: Long,
    val fromLevelId: Long,
    val fromLevelCode: String,
    val toLevelId: Long,
    val toLevelCode: String,
    val rule: TuitionProgressionRule,
    val active: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTuitionLevelProgressionRequest(
    @field:NotNull val fromLevelId: Long,
    @field:NotNull val toLevelId: Long,
    val rule: TuitionProgressionRule = TuitionProgressionRule.PASS_PREVIOUS_LEVEL,
    val active: Boolean = true
)

data class UpdateTuitionLevelProgressionRequest(
    val toLevelId: Long? = null,
    val rule: TuitionProgressionRule? = null,
    val active: Boolean? = null
)

data class TuitionFeePlanDto(
    val id: Long,
    val academicYearId: Long,
    val academicYearName: String,
    val name: String,
    val segment: TuitionSegment?,
    val levelId: Long?,
    val levelCode: String?,
    val monthlyFee: BigDecimal,
    val installments: Int,
    val monthlyDueDay: Int,
    val lateFeePercentage: BigDecimal,
    val automaticDebitMonthly: Boolean,
    val currency: String,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val status: TuitionFeePlanStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTuitionFeePlanRequest(
    @field:NotNull val academicYearId: Long,
    @field:NotBlank @field:Size(max = 120) val name: String,
    val segment: TuitionSegment? = null,
    val levelId: Long? = null,
    @field:DecimalMin("0.00") val monthlyFee: BigDecimal,
    @field:Min(1) @field:Max(24) val installments: Int,
    @field:Min(1) @field:Max(28) val monthlyDueDay: Int = 20,
    @field:DecimalMin("0.00") @field:DecimalMax("100.00") val lateFeePercentage: BigDecimal = BigDecimal.ZERO,
    val automaticDebitMonthly: Boolean = true,
    @field:Size(min = 3, max = 3) val currency: String = "ARS",
    @field:NotNull val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    val status: TuitionFeePlanStatus = TuitionFeePlanStatus.ACTIVE
)

data class UpdateTuitionFeePlanRequest(
    @field:Size(max = 120) val name: String? = null,
    val segment: TuitionSegment? = null,
    val levelId: Long? = null,
    @field:DecimalMin("0.00") val monthlyFee: BigDecimal? = null,
    @field:Min(1) @field:Max(24) val installments: Int? = null,
    @field:Min(1) @field:Max(28) val monthlyDueDay: Int? = null,
    @field:DecimalMin("0.00") @field:DecimalMax("100.00") val lateFeePercentage: BigDecimal? = null,
    val automaticDebitMonthly: Boolean? = null,
    @field:Size(min = 3, max = 3) val currency: String? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val status: TuitionFeePlanStatus? = null
)

data class TuitionEnrollmentFeePolicyDto(
    val id: Long,
    val name: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentDueDays: Int,
    val automaticDebitEligible: Boolean,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val status: TuitionEnrollmentFeePolicyStatus,
    val defaultPolicy: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTuitionEnrollmentFeePolicyRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:DecimalMin("0.01") val amount: BigDecimal,
    @field:Size(min = 3, max = 3) val currency: String = "ARS",
    @field:Min(0) @field:Max(90) val paymentDueDays: Int = 3,
    val automaticDebitEligible: Boolean = false,
    @field:NotNull val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    val status: TuitionEnrollmentFeePolicyStatus = TuitionEnrollmentFeePolicyStatus.ACTIVE,
    val defaultPolicy: Boolean = false
)

data class UpdateTuitionEnrollmentFeePolicyRequest(
    @field:Size(max = 120) val name: String? = null,
    @field:DecimalMin("0.01") val amount: BigDecimal? = null,
    @field:Size(min = 3, max = 3) val currency: String? = null,
    @field:Min(0) @field:Max(90) val paymentDueDays: Int? = null,
    val automaticDebitEligible: Boolean? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val status: TuitionEnrollmentFeePolicyStatus? = null,
    val defaultPolicy: Boolean? = null
)

data class TuitionDiscountDto(
    val id: Long,
    val studentId: Long?,
    val segment: TuitionSegment?,
    val levelId: Long?,
    val levelCode: String?,
    val type: TuitionDiscountType,
    val percentage: BigDecimal?,
    val amount: BigDecimal,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val reason: String,
    val active: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTuitionDiscountRequest(
    val studentId: Long? = null,
    val segment: TuitionSegment? = null,
    val levelId: Long? = null,
    @field:NotNull val type: TuitionDiscountType,
    @field:DecimalMin("0.00") val percentage: BigDecimal? = null,
    @field:DecimalMin("0.00") val amount: BigDecimal = BigDecimal.ZERO,
    @field:NotNull val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    @field:NotBlank @field:Size(max = 500) val reason: String,
    val active: Boolean = true
)

data class UpdateTuitionDiscountRequest(
    val studentId: Long? = null,
    val segment: TuitionSegment? = null,
    val levelId: Long? = null,
    val type: TuitionDiscountType? = null,
    @field:DecimalMin("0.00") val percentage: BigDecimal? = null,
    @field:DecimalMin("0.00") val amount: BigDecimal? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    @field:Size(max = 500) val reason: String? = null,
    val active: Boolean? = null
)

data class TuitionApplicationDto(
    val id: Long,
    val guardianUserId: Long,
    val studentId: Long?,
    val studentFirstName: String?,
    val studentLastName: String?,
    val studentEmail: String?,
    val studentDocumentNumber: String?,
    val assignedAcademicYearId: Long?,
    val assignedAcademicYearName: String?,
    val assignedLevelId: Long?,
    val assignedLevelCode: String?,
    val assignedLevelName: String?,
    val assignedCourseId: Long?,
    val applicationType: TuitionApplicationType,
    val status: TuitionApplicationStatus,
    val feePlan: TuitionFeePlanDto?,
    val enrollmentFeePolicy: TuitionEnrollmentFeePolicyDto?,
    val placement: TuitionPlacementAssessmentDto?,
    val enrollmentId: Long?,
    val warningMessage: String?,
    val progressionRule: TuitionProgressionRule?,
    val requiresAdminOverride: Boolean,
    val adminNotes: String?,
    val ledgerEntries: List<TuitionLedgerEntryDto>,
    val submittedAt: LocalDateTime,
    val approvedAt: LocalDateTime?,
    val approvedBy: Long?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTuitionApplicationRequest(
    @field:NotNull val applicationType: TuitionApplicationType,
    val studentId: Long? = null,
    @field:Size(min = 1, max = 100) val studentFirstName: String? = null,
    @field:Size(min = 1, max = 100) val studentLastName: String? = null,
    @field:Email val studentEmail: String? = null,
    val studentDocumentNumber: String? = null,
    @field:Past val studentDateOfBirth: LocalDate? = null,
    val studentAddress: String? = null,
    val studentPhoneNumber: String? = null,
    val studentEmergencyContact: String? = null,
    val studentMedicalNotes: String? = null
)

data class CreateTuitionEnrollmentChargeRequest(
    val enrollmentFeePolicyId: Long? = null
)

data class TuitionPlacementRequest(
    @field:NotNull val status: TuitionPlacementStatus,
    val recommendedLevelId: Long? = null,
    @field:Size(max = 2000) val notes: String? = null
)

data class TuitionAcademicAssignmentRequest(
    @field:NotNull val academicYearId: Long,
    @field:NotNull val levelId: Long,
    @field:NotNull val courseId: Long,
    @field:NotNull val feePlanId: Long,
    @field:Size(max = 1000) val adminNotes: String? = null
)

data class TuitionPlacementAssessmentDto(
    val id: Long,
    val applicationId: Long,
    val status: TuitionPlacementStatus,
    val recommendedLevelId: Long?,
    val recommendedLevelCode: String?,
    val recommendedLevelName: String?,
    val evaluatorUserId: Long,
    val notes: String?,
    val assessedAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class TuitionDecisionRequest(
    @field:Size(max = 1000)
    val adminNotes: String? = null
)

data class TuitionLedgerEntryDto(
    val id: Long,
    val applicationId: Long,
    val studentId: Long?,
    val discountId: Long?,
    val concept: TuitionLedgerConcept,
    val grossAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val netAmount: BigDecimal,
    val paidAmount: BigDecimal,
    val lateFeeAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val outstandingAmount: BigDecimal,
    val dueDate: LocalDate,
    val status: TuitionLedgerStatus,
    val billingReference: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
