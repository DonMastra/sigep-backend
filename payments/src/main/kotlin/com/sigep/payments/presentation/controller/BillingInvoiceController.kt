package com.sigep.payments.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.payments.application.dto.FiscalInvoiceDetailDto
import com.sigep.payments.application.dto.FiscalInvoiceDto
import com.sigep.payments.application.service.BillingApplicationService
import com.sigep.payments.application.service.BillingDocumentService
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.security.application.annotation.RequireAdmin
import org.springframework.http.HttpStatus
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/billing/invoices")
@RequireAdmin
class BillingInvoiceController(
    private val billingService: BillingApplicationService,
    private val documentService: BillingDocumentService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: FiscalInvoiceStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<FiscalInvoiceDto>>> = ResponseEntity.ok(
        ApiResponse.success(billingService.list(status, page, limit))
    )

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<ApiResponse<FiscalInvoiceDetailDto>> = ResponseEntity.ok(
        ApiResponse.success(billingService.get(id))
    )

    @GetMapping("/{id}/document", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun document(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val document = documentService.invoice(id)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(document.filename).build().toString())
            .body(document.content)
    }

    @PostMapping("/{id}/authorize")
    fun authorize(
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<ApiResponse<FiscalInvoiceDetailDto>> = ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(billingService.queueAuthorization(id, idempotencyKey), "Authorization queued"))

    @PostMapping("/{id}/reconcile")
    fun reconcile(@PathVariable id: Long): ResponseEntity<ApiResponse<FiscalInvoiceDetailDto>> = ResponseEntity.ok(
        ApiResponse.success(billingService.reconcile(id), "Reconciliation completed")
    )
}
