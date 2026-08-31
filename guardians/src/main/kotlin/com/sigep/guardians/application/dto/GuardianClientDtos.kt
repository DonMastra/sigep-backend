package com.sigep.guardians.application.dto

import com.sigep.guardians.domain.model.GuardianContactChannel
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class GuardianClientSummaryDto(
    val guardianUserId: Long,
    val clientNumber: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String?,
    val documentNumber: String?,
    val accountStatus: String,
    val accountActive: Boolean,
    val preferredContactChannel: String,
    val studentCount: Long,
    val activeStudentCount: Long,
    val activeEnrollmentCount: Long,
    val tuitionApplicationCount: Long,
    val billingAccountId: Long?,
    val billingAccountStatus: String?,
    val billingProfileStatus: String?,
    val openChargeCount: Long,
    val overdueChargeCount: Long,
    val outstandingAmount: BigDecimal,
    val lastPaymentDate: LocalDate?,
    val missingContactData: Boolean,
    val profileVersion: Long
)

data class GuardianClientStatsDto(
    val totalClients: Long,
    val withStudents: Long,
    val withoutStudents: Long,
    val withBillingAccount: Long,
    val withOpenDebt: Long,
    val missingContactData: Long
)

data class GuardianClientDetailDto(
    val summary: GuardianClientSummaryDto,
    val accountVersion: Long,
    val address: String?,
    val dateOfBirth: LocalDate?,
    val emergencyContact: String?,
    val administrativeNotes: String?,
    val profileUpdatedAt: LocalDateTime?,
    val students: List<GuardianClientStudentDto>,
    val tuitionApplications: List<GuardianClientTuitionDto>,
    val charges: List<GuardianClientChargeDto>,
    val payments: List<GuardianClientPaymentDto>
)

data class GuardianClientStudentDto(
    val studentId: Long,
    val studentNumber: String,
    val firstName: String,
    val lastName: String,
    val active: Boolean,
    val currentLevel: String,
    val enrollmentId: Long?,
    val courseId: Long?,
    val courseName: String?,
    val enrollmentStatus: String?,
    val tuitionApplicationId: Long?,
    val tuitionApplicationStatus: String?,
    val openChargeCount: Long,
    val outstandingAmount: BigDecimal
)

data class GuardianClientTuitionDto(
    val applicationId: Long,
    val studentId: Long?,
    val studentName: String,
    val applicationType: String,
    val status: String,
    val origin: String,
    val submittedAt: LocalDateTime,
    val enrollmentId: Long?,
    val assignedCourseId: Long?,
    val assignedCourseName: String?
)

data class GuardianClientChargeDto(
    val chargeId: Long,
    val studentId: Long?,
    val studentName: String,
    val concept: String,
    val description: String,
    val amount: BigDecimal,
    val paidAmount: BigDecimal,
    val outstandingAmount: BigDecimal,
    val currency: String,
    val dueDate: LocalDate,
    val status: String,
    val overdue: Boolean,
    val fiscalDisposition: String,
    val fiscalInvoiceId: Long?,
    val fiscalInvoiceStatus: String?
)

data class GuardianClientPaymentDto(
    val paymentId: Long,
    val paymentDate: LocalDate?,
    val amount: BigDecimal,
    val allocatedAmount: BigDecimal,
    val currency: String,
    val status: String,
    val paymentMethod: String?,
    val receiptId: Long?,
    val receiptNumber: String?,
    val invoiceId: Long?,
    val invoiceStatus: String?
)

data class UpdateGuardianClientProfileRequest(
    @field:NotNull
    val preferredContactChannel: GuardianContactChannel,

    @field:Size(max = 1000)
    val administrativeNotes: String? = null,

    @field:PositiveOrZero
    val version: Long
)

data class UpdateGuardianClientAccountRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val firstName: String,

    @field:NotBlank
    @field:Size(max = 100)
    val lastName: String,

    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,

    @field:Size(max = 50)
    val phoneNumber: String? = null,

    @field:Size(max = 255)
    val address: String? = null,

    @field:Past
    val dateOfBirth: LocalDate? = null,

    @field:Size(max = 50)
    val documentNumber: String? = null,

    @field:Size(max = 255)
    val emergencyContact: String? = null,

    @field:PositiveOrZero
    val version: Long
)
