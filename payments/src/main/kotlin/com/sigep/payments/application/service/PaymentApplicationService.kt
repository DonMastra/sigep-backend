package com.sigep.payments.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.payments.application.dto.ConfirmPaymentRequest
import com.sigep.payments.application.dto.CreatePaymentRequest
import com.sigep.payments.application.dto.PaymentDetailDto
import com.sigep.payments.application.dto.PaymentDto
import com.sigep.payments.application.dto.PaymentReceiptDto
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentReceipt
import com.sigep.payments.domain.model.PaymentStatus
import com.sigep.payments.domain.repository.BillingOutboxRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentReceiptRepository
import com.sigep.payments.domain.repository.PaymentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class PaymentApplicationService(
    private val paymentRepository: PaymentRepository,
    private val receiptRepository: PaymentReceiptRepository,
    private val invoiceRepository: FiscalInvoiceRepository,
    private val outboxRepository: BillingOutboxRepository
) {

    fun create(
        idempotencyKey: String,
        request: CreatePaymentRequest,
        initialPaymentDate: LocalDate? = null
    ): PaymentDetailDto {
        validateIdempotencyKey(idempotencyKey)
        val fingerprint = BillingFingerprint.payment(request)
        paymentRepository.findByCreationKey(idempotencyKey).orElse(null)?.let { existing ->
            if (existing.creationFingerprint == fingerprint) {
                return detail(existing)
            }
            throw ResourceConflictException("Idempotency-Key was already used with another payment payload")
        }
        val externalReference = request.externalReference?.trim()?.takeIf(String::isNotEmpty)
        if (externalReference != null && paymentRepository.existsByExternalReference(externalReference)) {
            throw DuplicateResourceException("Payment external reference already exists")
        }

        val now = LocalDateTime.now()
        val payment = paymentRepository.save(
            Payment(
                studentId = request.studentId,
                amount = request.amount,
                currency = request.currency,
                concept = request.concept.trim(),
                // The combined register workflow already has the collection date.
                // Persist it on the first insert so legacy databases that still
                // have payment_date NOT NULL can accept the request; confirm()
                // writes the same value again as the authoritative transition.
                paymentDate = initialPaymentDate,
                dueDate = request.dueDate,
                status = PaymentStatus.PENDING,
                paymentMethod = null,
                receiptNumber = null,
                externalReference = externalReference,
                creationKey = idempotencyKey,
                creationFingerprint = fingerprint,
                notes = request.notes?.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        return detail(payment)
    }

    fun confirm(
        paymentId: Long,
        idempotencyKey: String,
        request: ConfirmPaymentRequest,
        adminId: Long
    ): PaymentDetailDto {
        validateIdempotencyKey(idempotencyKey)
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow { ResourceNotFoundException("Payment $paymentId not found") }
        val fingerprint = BillingFingerprint.confirmation(request)

        if (payment.status == PaymentStatus.PAID) {
            if (payment.confirmationKey == idempotencyKey && payment.confirmationFingerprint == fingerprint) {
                return detail(payment)
            }
            throw ResourceConflictException("Payment $paymentId is already confirmed")
        }
        if (payment.status == PaymentStatus.CANCELLED) {
            throw ResourceConflictException("Cancelled payment $paymentId cannot be confirmed")
        }

        val now = LocalDateTime.now()
        val receiptNumber = receiptNumber(paymentId, request.paymentDate.year)
        val confirmed = paymentRepository.save(
            payment.copy(
                paymentDate = request.paymentDate,
                status = PaymentStatus.PAID,
                paymentMethod = request.paymentMethod,
                receiptNumber = receiptNumber,
                confirmationKey = idempotencyKey,
                confirmationFingerprint = fingerprint,
                confirmedAt = now,
                confirmedBy = adminId,
                updatedAt = now
            )
        )

        receiptRepository.save(
            PaymentReceipt(
                payment = confirmed,
                receiptNumber = receiptNumber,
                payerName = request.payerName.trim(),
                amount = confirmed.amount,
                currency = confirmed.currency,
                concept = confirmed.concept,
                issuedAt = now,
                issuedBy = adminId,
                createdAt = now
            )
        )

        return detail(confirmed)
    }

    @Transactional(readOnly = true)
    fun get(paymentId: Long): PaymentDetailDto {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { ResourceNotFoundException("Payment $paymentId not found") }
        return detail(payment)
    }

    @Transactional(readOnly = true)
    fun getReceipt(paymentId: Long): PaymentReceiptDto = receiptRepository.findByPaymentId(paymentId)
        .orElseThrow { ResourceNotFoundException("Receipt for payment $paymentId not found") }
        .toDto()

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): PageResponse<PaymentDto> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 100),
            Sort.by(Sort.Direction.DESC, "createdAt")
        )
        val result = paymentRepository.findAll(pageable)
        return PageResponse(
            content = result.content.map(Payment::toDto),
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    private fun detail(payment: Payment): PaymentDetailDto {
        val paymentId = requireNotNull(payment.id)
        val receipt = receiptRepository.findByPaymentId(paymentId).orElse(null)
        val invoice = invoiceRepository.findByPaymentId(paymentId).orElse(null)
        val outboxStatus = invoice?.id?.let { invoiceId ->
            outboxRepository.findByInvoiceIdAndEventType(
                invoiceId,
                com.sigep.payments.domain.model.BillingOutboxEventType.AUTHORIZE_INVOICE
            ).orElse(null)?.status
        }
        return PaymentDetailDto(
            payment = payment.toDto(),
            receipt = receipt?.toDto(),
            invoice = invoice?.toDto(outboxStatus)
        )
    }

    private fun receiptNumber(paymentId: Long, year: Int): String = "RX-$year-${paymentId.toString().padStart(8, '0')}"

    private fun validateIdempotencyKey(key: String) {
        if (key.isBlank() || key.length > 128) {
            throw ValidationException("Idempotency-Key is required and must have at most 128 characters")
        }
    }
}
