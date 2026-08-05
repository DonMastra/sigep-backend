package com.sigep.payments.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.payments.application.dto.BillingChargeDto
import com.sigep.payments.application.dto.BillingProfileDto
import com.sigep.payments.application.dto.BillingRunDto
import com.sigep.payments.application.dto.BillingRunPreviewDto
import com.sigep.payments.application.dto.ChargePaymentResultDto
import com.sigep.payments.application.dto.PrepareBillingRunRequest
import com.sigep.payments.application.dto.RegisterChargePaymentRequest
import com.sigep.payments.application.dto.UpdateBillingProfileRequest
import com.sigep.payments.application.service.BillingOperationsService
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingProfileStatus
import com.sigep.security.application.annotation.RequireAdmin
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/billing")
@RequireAdmin
class BillingOperationsController(
    private val billingOperationsService: BillingOperationsService
) {

    @GetMapping("/charges")
    fun listCharges(
        @RequestParam(required = false) status: BillingChargeStatus?,
        @RequestParam(required = false) studentId: Long?,
        @RequestParam(required = false) studentQuery: String?,
        @RequestParam(required = false) profileStatus: BillingProfileStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<BillingChargeDto>>> = ResponseEntity.ok(
        ApiResponse.success(
            billingOperationsService.listCharges(status, studentId, studentQuery, profileStatus, page, limit)
        )
    )

    @GetMapping("/accounts/{accountId}/profile")
    fun getProfile(@PathVariable accountId: Long): ResponseEntity<ApiResponse<BillingProfileDto>> =
        ResponseEntity.ok(ApiResponse.success(billingOperationsService.getProfile(accountId)))

    @PutMapping("/accounts/{accountId}/profile")
    fun updateProfile(
        @PathVariable accountId: Long,
        @Valid @RequestBody request: UpdateBillingProfileRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<BillingProfileDto>> = ResponseEntity.ok(
        ApiResponse.success(
            billingOperationsService.updateProfile(accountId, request, httpRequest.requireUserId()),
            "Billing profile updated"
        )
    )

    @PostMapping("/charges/{chargeId}/payments")
    fun registerPayment(
        @PathVariable chargeId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: RegisterChargePaymentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<ChargePaymentResultDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                billingOperationsService.registerChargePayment(
                    chargeId,
                    idempotencyKey,
                    request,
                    httpRequest.requireUserId()
                ),
                "Payment registered and non-fiscal receipt issued"
            )
        )

    @PostMapping("/runs/preview")
    fun previewRun(
        @Valid @RequestBody request: PrepareBillingRunRequest
    ): ResponseEntity<ApiResponse<BillingRunPreviewDto>> = ResponseEntity.ok(
        ApiResponse.success(billingOperationsService.preview(request))
    )

    @PostMapping("/runs")
    fun createRun(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: PrepareBillingRunRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<BillingRunDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                billingOperationsService.createRun(idempotencyKey, request, httpRequest.requireUserId()),
                "Fiscal invoice drafts created"
            )
        )

    @GetMapping("/runs/{runId}")
    fun getRun(@PathVariable runId: Long): ResponseEntity<ApiResponse<BillingRunDto>> =
        ResponseEntity.ok(ApiResponse.success(billingOperationsService.getRun(runId)))

    private fun HttpServletRequest.requireUserId(): Long = getAttribute("userId") as? Long
        ?: throw UnauthorizedException("Token invalid or missing userId")
}
