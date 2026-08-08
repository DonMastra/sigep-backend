package com.sigep.payments.application.dto

import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingChargeFiscalDisposition
import com.sigep.payments.domain.model.FiscalClosure
import com.sigep.payments.domain.model.AutomaticDebitInstructionStatus
import com.sigep.payments.domain.model.AutomaticDebitInstrumentType
import com.sigep.payments.domain.model.AutomaticDebitMandateStatus
import com.sigep.payments.domain.model.AutomaticDebitResolution
import com.sigep.payments.domain.model.AutomaticDebitScope
import com.sigep.payments.domain.model.BillingCollectionChannel
import com.sigep.payments.domain.model.BillingProfileStatus
import com.sigep.payments.domain.model.BillingRunStatus
import com.sigep.payments.domain.model.BillingSelectionMode
import com.sigep.payments.domain.model.FiscalAttemptOutcome
import com.sigep.payments.domain.model.FiscalAmountTreatment
import com.sigep.payments.domain.model.FiscalAttemptType
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.model.PaymentStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import jakarta.validation.Valid
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CreatePaymentRequest(
    @field:Positive
    val studentId: Long?,

    @field:DecimalMin(value = "0.01")
    @field:Digits(integer = 10, fraction = 2)
    val amount: BigDecimal,

    @field:Pattern(regexp = "^[A-Z]{3}$")
    val currency: String = "ARS",

    @field:NotBlank
    @field:Size(max = 500)
    val concept: String,

    val dueDate: LocalDate,

    @field:Size(max = 150)
    val externalReference: String? = null,

    @field:Size(max = 1000)
    val notes: String? = null
)

data class ConfirmPaymentRequest(
    val paymentDate: LocalDate,
    val paymentMethod: PaymentMethod,

    @field:NotBlank
    @field:Size(max = 200)
    val payerName: String
)

data class RegisterPaymentAndInvoiceRequest(
    @field:jakarta.validation.Valid
    val payment: CreatePaymentRequest,

    @field:jakarta.validation.Valid
    val confirmation: ConfirmPaymentRequest,

    @field:jakarta.validation.Valid
    val invoice: CreateFiscalInvoiceRequest
)

data class CreateFiscalInvoiceRequest(
    @field:Positive
    val voucherType: Int,

    @field:Min(1)
    @field:Max(3)
    val concept: Int,

    @field:NotBlank
    @field:Size(max = 200)
    val receiverName: String,

    @field:NotBlank
    @field:Size(max = 300)
    val receiverAddress: String = "",

    @field:Min(0)
    val receiverDocumentType: Int,

    @field:Pattern(regexp = "^[0-9]{1,20}$")
    val receiverDocumentNumber: String,

    @field:Positive
    val receiverVatConditionId: Int,

    val issueDate: LocalDate,
    val serviceFrom: LocalDate? = null,
    val serviceTo: LocalDate? = null,
    val paymentDueDate: LocalDate? = null,

    @field:Pattern(regexp = "^[A-Z]{3}$")
    val currency: String = "PES",

    @field:DecimalMin(value = "0.000001")
    @field:Digits(integer = 12, fraction = 6)
    val exchangeRate: BigDecimal = BigDecimal.ONE,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val nonTaxedAmount: BigDecimal = BigDecimal.ZERO,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val netAmount: BigDecimal = BigDecimal.ZERO,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val exemptAmount: BigDecimal = BigDecimal.ZERO,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val vatAmount: BigDecimal = BigDecimal.ZERO,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val otherTaxesAmount: BigDecimal = BigDecimal.ZERO,

    @field:Valid
    @field:Size(max = 20)
    val vatSubtotals: List<FiscalVatSubtotalRequest> = emptyList(),

    @field:Valid
    @field:Size(max = 20)
    val taxes: List<FiscalOtherTaxRequest> = emptyList()
)

data class FiscalVatSubtotalRequest(
    @field:Positive
    val id: Int,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val baseAmount: BigDecimal,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val amount: BigDecimal
)

data class FiscalOtherTaxRequest(
    @field:Positive
    val id: Int,

    @field:NotBlank
    @field:Size(max = 200)
    val description: String,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val baseAmount: BigDecimal,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 6, fraction = 6)
    val rate: BigDecimal,

    @field:DecimalMin(value = "0.00")
    @field:Digits(integer = 10, fraction = 2)
    val amount: BigDecimal
)

data class PaymentDto(
    val id: Long,
    val studentId: Long?,
    val amount: BigDecimal,
    val currency: String,
    val concept: String,
    val paymentDate: LocalDate?,
    val dueDate: LocalDate,
    val status: PaymentStatus,
    val paymentMethod: PaymentMethod?,
    val receiptNumber: String?,
    val externalReference: String?,
    val notes: String?,
    val confirmedAt: LocalDateTime?,
    val confirmedBy: Long?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class PaymentReceiptDto(
    val id: Long,
    val paymentId: Long,
    val receiptNumber: String,
    val payerName: String,
    val amount: BigDecimal,
    val currency: String,
    val concept: String,
    val issuedAt: LocalDateTime,
    val issuedBy: Long,
    val documentType: String,
    val fiscalDisclaimer: String
)

data class PaymentDetailDto(
    val payment: PaymentDto,
    val receipt: PaymentReceiptDto?,
    val invoice: FiscalInvoiceDto?
)

data class BillingWorkflowDto(
    val payment: PaymentDetailDto,
    val invoice: FiscalInvoiceDetailDto
)

data class FiscalInvoiceDto(
    val id: Long,
    val paymentId: Long?,
    val chargeId: Long?,
    val collectionChannel: BillingCollectionChannel?,
    val studentId: Long?,
    val paymentReceiptNumber: String?,
    val status: FiscalInvoiceStatus,
    val issuerCuit: String?,
    val pointOfSale: Int?,
    val voucherType: Int,
    val voucherNumber: Long?,
    val concept: Int,
    val receiverName: String,
    val receiverAddress: String,
    val receiverDocumentType: Int,
    val receiverDocumentNumber: String,
    val receiverVatConditionId: Int,
    val issueDate: LocalDate,
    val serviceFrom: LocalDate?,
    val serviceTo: LocalDate?,
    val paymentDueDate: LocalDate?,
    val currency: String,
    val exchangeRate: BigDecimal,
    val totalAmount: BigDecimal,
    val nonTaxedAmount: BigDecimal,
    val netAmount: BigDecimal,
    val exemptAmount: BigDecimal,
    val vatAmount: BigDecimal,
    val otherTaxesAmount: BigDecimal,
    val vatSubtotals: List<FiscalVatSubtotalDto>,
    val taxes: List<FiscalOtherTaxDto>,
    val authorizationCode: String?,
    val authorizationExpiresOn: LocalDate?,
    val authorizedAt: LocalDateTime?,
    val qrUrl: String?,
    val providerRequestId: String?,
    val preflightErrors: List<String>,
    val observations: List<String>,
    val errors: List<String>,
    val outboxStatus: BillingOutboxStatus?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class FiscalVatSubtotalDto(
    val id: Int,
    val baseAmount: BigDecimal,
    val amount: BigDecimal
)

data class FiscalOtherTaxDto(
    val id: Int,
    val description: String,
    val baseAmount: BigDecimal,
    val rate: BigDecimal,
    val amount: BigDecimal
)

data class FiscalInvoiceAttemptDto(
    val id: Long,
    val attemptNumber: Int,
    val type: FiscalAttemptType,
    val provider: String,
    val environment: String,
    val outcome: FiscalAttemptOutcome,
    val providerRequestId: String?,
    val observations: List<String>,
    val errors: List<String>,
    val requestedAt: LocalDateTime,
    val respondedAt: LocalDateTime?
)

data class FiscalInvoiceDetailDto(
    val invoice: FiscalInvoiceDto,
    val attempts: List<FiscalInvoiceAttemptDto>
)

data class BillingProfileDto(
    val id: Long,
    val accountId: Long,
    val guardianUserId: Long,
    val receiverName: String,
    val receiverAddress: String?,
    val receiverDocumentType: Int?,
    val receiverDocumentNumber: String?,
    val receiverVatConditionId: Int?,
    val defaultVoucherType: Int?,
    val defaultFiscalConcept: Int,
    val fiscalCurrency: String,
    val rg5866Applicable: Boolean,
    val status: BillingProfileStatus,
    val missingFields: List<String>,
    val updatedAt: LocalDateTime
)

data class UpdateBillingProfileRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val receiverName: String,

    @field:NotBlank
    @field:Size(max = 300)
    val receiverAddress: String,

    @field:Positive
    val receiverDocumentType: Int,

    @field:Pattern(regexp = "^[0-9]{1,20}$")
    val receiverDocumentNumber: String,

    @field:Positive
    val receiverVatConditionId: Int,

    @field:Positive
    val defaultVoucherType: Int,

    @field:Min(1)
    @field:Max(3)
    val defaultFiscalConcept: Int = 2,

    @field:Pattern(regexp = "^[A-Z]{3}$")
    val fiscalCurrency: String = "PES"
)

data class BillingChargeDto(
    val id: Long,
    val accountId: Long,
    val guardianUserId: Long,
    val studentId: Long?,
    val studentName: String,
    val sourceType: String,
    val sourceId: Long,
    val concept: String,
    val description: String,
    val baseAmount: BigDecimal,
    val lateFeeAmount: BigDecimal,
    val amount: BigDecimal,
    val paidAmount: BigDecimal,
    val outstandingAmount: BigDecimal,
    val currency: String,
    val dueDate: LocalDate,
    val serviceFrom: LocalDate?,
    val serviceTo: LocalDate?,
    val status: BillingChargeStatus,
    val overdue: Boolean,
    val lateFeePercentage: BigDecimal,
    val lateFeeEligible: Boolean,
    val automaticDebitEligible: Boolean,
    val collectionChannel: BillingCollectionChannel,
    val automaticDebitEnrolled: Boolean,
    val fiscalDisposition: BillingChargeFiscalDisposition,
    val automaticDebitStatus: AutomaticDebitInstructionStatus?,
    val profile: BillingProfileDto,
    val invoiceId: Long?,
    val invoiceStatus: FiscalInvoiceStatus?,
    val paymentId: Long?,
    val receiptNumber: String?,
    val payments: List<BillingChargePaymentDto>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class RegisterPaymentReceiptRequest(
    @field:Valid
    val payment: CreatePaymentRequest,

    @field:Valid
    val confirmation: ConfirmPaymentRequest
)

data class BillingChargePaymentDto(
    val paymentId: Long,
    val amount: BigDecimal,
    val paymentDate: LocalDate?,
    val method: PaymentMethod?,
    val status: PaymentStatus,
    val receiptNumber: String?
)

data class BillingChargeFilterRequest(
    val status: BillingChargeStatus? = BillingChargeStatus.OPEN,
    val studentId: Long? = null,
    @field:Size(max = 100)
    val studentQuery: String? = null,
    val profileStatus: BillingProfileStatus? = null,
    val fiscalDisposition: BillingChargeFiscalDisposition? = null,
    val overdue: Boolean? = null,
    val automaticDebitStatus: AutomaticDebitInstructionStatus? = null,
    val collectionChannel: BillingCollectionChannel? = BillingCollectionChannel.REGULAR
)

data class PrepareBillingRunRequest(
    val selectionMode: BillingSelectionMode,

    @field:Size(max = 1000)
    val chargeIds: List<Long> = emptyList(),

    @field:Valid
    val filters: BillingChargeFilterRequest = BillingChargeFilterRequest(),

    val issueDate: LocalDate,
    val amountTreatment: FiscalAmountTreatment
)

data class BillingRunPreviewItemDto(
    val charge: BillingChargeDto,
    val blockers: List<String>
)

data class BillingRunPreviewDto(
    val selectedCount: Int,
    val readyCount: Int,
    val blockedCount: Int,
    val totalAmount: BigDecimal,
    val items: List<BillingRunPreviewItemDto>
)

data class BillingRunItemDto(
    val chargeId: Long,
    val invoiceId: Long,
    val invoiceStatus: FiscalInvoiceStatus
)

data class BillingRunDto(
    val id: Long,
    val selectionMode: BillingSelectionMode,
    val amountTreatment: FiscalAmountTreatment,
    val issueDate: LocalDate,
    val selectedCount: Int,
    val createdCount: Int,
    val status: BillingRunStatus,
    val requestedBy: Long,
    val createdAt: LocalDateTime,
    val items: List<BillingRunItemDto>
)

data class RegisterChargePaymentRequest(
    @field:DecimalMin(value = "0.01")
    @field:Digits(integer = 10, fraction = 2)
    val amount: BigDecimal,

    @field:Valid
    val confirmation: ConfirmPaymentRequest,

    val fiscalClosure: FiscalClosure? = null,

    @field:Size(max = 500)
    val fiscalReason: String? = null,

    @field:Size(max = 150)
    val externalReference: String? = null,

    @field:Size(max = 1000)
    val notes: String? = null
)

data class ChargePaymentResultDto(
    val charge: BillingChargeDto,
    val payment: PaymentDetailDto
)

data class AutomaticDebitMandateDto(
    val id: Long,
    val accountId: Long,
    val provider: String,
    val maskedLabel: String,
    val processorName: String,
    val instrumentType: AutomaticDebitInstrumentType,
    val scope: AutomaticDebitScope,
    val effectiveFrom: LocalDate,
    val status: AutomaticDebitMandateStatus,
    val isDefault: Boolean,
    val consentVersion: String,
    val consentedAt: LocalDateTime,
    val cancelledAt: LocalDateTime?,
    val simulated: Boolean
)

data class CreateAutomaticDebitMandateRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val maskedLabel: String,

    @field:NotBlank
    @field:Size(max = 40)
    val consentVersion: String,

    @field:NotBlank
    @field:Size(max = 80)
    val processorName: String = "Simulated",

    val instrumentType: AutomaticDebitInstrumentType = AutomaticDebitInstrumentType.CARD,
    val scope: AutomaticDebitScope = AutomaticDebitScope.INSTALLMENTS,
    val effectiveFrom: LocalDate = LocalDate.now()
)

data class CreateAdminAutomaticDebitMandateRequest(
    @field:Positive
    val accountId: Long,

    @field:NotBlank
    @field:Size(max = 100)
    val maskedLabel: String,

    @field:NotBlank
    @field:Size(max = 80)
    val processorName: String,

    val instrumentType: AutomaticDebitInstrumentType,
    val scope: AutomaticDebitScope = AutomaticDebitScope.INSTALLMENTS,
    val effectiveFrom: LocalDate,

    @field:NotBlank
    @field:Size(max = 40)
    val consentVersion: String
)

data class UpdateAutomaticDebitMandateRequest(
    val status: AutomaticDebitMandateStatus
)

data class AutomaticDebitInstructionDto(
    val id: Long,
    val mandateId: Long,
    val chargeId: Long,
    val invoiceId: Long,
    val paymentId: Long?,
    val accountId: Long,
    val studentName: String,
    val receiverName: String,
    val pointOfSale: Int,
    val voucherNumber: Long,
    val voucherSuffix: String,
    val processorName: String,
    val instrumentType: AutomaticDebitInstrumentType,
    val maskedLabel: String,
    val amount: BigDecimal,
    val currency: String,
    val processingDate: LocalDate,
    val status: AutomaticDebitInstructionStatus,
    val submissionReference: String?,
    val failureCode: String?,
    val failureMessage: String?,
    val resolution: AutomaticDebitResolution?,
    val resolutionReason: String?,
    val submittedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val simulated: Boolean
)

data class CreateAutomaticDebitInstructionRequest(
    @field:Positive
    val invoiceId: Long,
    val processingDate: LocalDate
)

data class SubmitAutomaticDebitInstructionRequest(
    @field:NotBlank
    @field:Size(max = 150)
    val submissionReference: String
)

data class RecordAutomaticDebitResultRequest(
    val outcome: AutomaticDebitInstructionStatus,

    @field:Size(max = 80)
    val failureCode: String? = null,

    @field:Size(max = 500)
    val failureMessage: String? = null
)

data class ResolveAutomaticDebitRejectionRequest(
    val resolution: AutomaticDebitResolution,

    @field:NotBlank
    @field:Size(max = 500)
    val reason: String
)

data class ReverseAutomaticDebitRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String
)

data class ReverseLateFeeRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String
)

data class RectifyFiscalDecisionRequest(
    val decision: FiscalClosure,

    @field:NotBlank
    @field:Size(max = 500)
    val reason: String
)
