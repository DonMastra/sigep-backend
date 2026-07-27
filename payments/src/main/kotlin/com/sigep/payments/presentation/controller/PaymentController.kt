package com.sigep.payments.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.payments.application.dto.ConfirmPaymentRequest
import com.sigep.payments.application.dto.CreateFiscalInvoiceRequest
import com.sigep.payments.application.dto.CreatePaymentRequest
import com.sigep.payments.application.dto.FiscalInvoiceDetailDto
import com.sigep.payments.application.dto.PaymentDetailDto
import com.sigep.payments.application.dto.PaymentDto
import com.sigep.payments.application.dto.PaymentReceiptDto
import com.sigep.payments.application.dto.BillingWorkflowDto
import com.sigep.payments.application.dto.RegisterPaymentAndInvoiceRequest
import com.sigep.payments.application.service.BillingApplicationService
import com.sigep.payments.application.service.BillingDocumentService
import com.sigep.payments.application.service.BillingWorkflowService
import com.sigep.payments.application.service.PaymentApplicationService
import com.sigep.security.application.annotation.RequireAdmin
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
@RequireAdmin
class PaymentController(
    private val paymentService: PaymentApplicationService,
    private val billingService: BillingApplicationService,
    private val workflowService: BillingWorkflowService,
    private val documentService: BillingDocumentService
) {

    @PostMapping("/register")
    fun register(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: RegisterPaymentAndInvoiceRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<BillingWorkflowDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                workflowService.registerPaymentAndInvoice(
                    idempotencyKey,
                    request,
                    httpRequest.requireUserId()
                ),
                "Payment, non-fiscal receipt and fiscal invoice draft created"
            )
        )

    @PostMapping
    fun create(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreatePaymentRequest
    ): ResponseEntity<ApiResponse<PaymentDetailDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(paymentService.create(idempotencyKey, request), "Payment created"))

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<PaymentDto>>> = ResponseEntity.ok(
        ApiResponse.success(paymentService.list(page, limit))
    )

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<ApiResponse<PaymentDetailDto>> = ResponseEntity.ok(
        ApiResponse.success(paymentService.get(id))
    )

    @PostMapping("/{id}/confirm")
    fun confirm(
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: ConfirmPaymentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PaymentDetailDto>> = ResponseEntity.ok(
        ApiResponse.success(
            paymentService.confirm(id, idempotencyKey, request, httpRequest.requireUserId()),
            "Payment confirmed and non-fiscal receipt issued"
        )
    )

    @GetMapping("/{id}/receipt")
    fun receipt(@PathVariable id: Long): ResponseEntity<ApiResponse<PaymentReceiptDto>> = ResponseEntity.ok(
        ApiResponse.success(paymentService.getReceipt(id))
    )

    @GetMapping("/{id}/receipt/document", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun receiptDocument(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val document = documentService.receipt(id)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(document.filename).build().toString())
            .body(document.content)
    }

    @PostMapping("/{id}/fiscal-invoice")
    fun createFiscalInvoice(
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateFiscalInvoiceRequest
    ): ResponseEntity<ApiResponse<FiscalInvoiceDetailDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                billingService.createInvoice(id, idempotencyKey, request),
                "Fiscal invoice draft created"
            )
        )

    private fun HttpServletRequest.requireUserId(): Long = getAttribute("userId") as? Long
        ?: throw UnauthorizedException("Token invalid or missing userId")
}
