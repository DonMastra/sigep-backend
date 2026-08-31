package com.sigep.guardians.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class GuardianClientSearchCriteria(
    val search: String? = null,
    val accountStatus: String? = null,
    val relationship: GuardianRelationshipFilter? = null,
    val billing: GuardianBillingFilter? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sort: String = "lastName",
    val order: String = "ASC"
)

enum class GuardianRelationshipFilter { WITH_STUDENTS, WITHOUT_STUDENTS }
enum class GuardianBillingFilter { WITH_DEBT, NO_DEBT, NO_ACCOUNT }

data class GuardianClientSummaryReadModel(
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

data class GuardianClientStatsReadModel(
    val totalClients: Long,
    val withStudents: Long,
    val withoutStudents: Long,
    val withBillingAccount: Long,
    val withOpenDebt: Long,
    val missingContactData: Long
)

data class GuardianClientDetailReadModel(
    val summary: GuardianClientSummaryReadModel,
    val accountVersion: Long,
    val address: String?,
    val dateOfBirth: LocalDate?,
    val emergencyContact: String?,
    val administrativeNotes: String?,
    val updatedAt: LocalDateTime?
)

data class GuardianClientStudentReadModel(
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

data class GuardianClientTuitionReadModel(
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

data class GuardianClientChargeReadModel(
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

data class GuardianClientPaymentReadModel(
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
