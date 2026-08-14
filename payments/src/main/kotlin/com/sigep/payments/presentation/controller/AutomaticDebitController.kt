package com.sigep.payments.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.payments.application.dto.AutomaticDebitInstructionDto
import com.sigep.payments.application.dto.AutomaticDebitMandateDto
import com.sigep.payments.application.dto.CreateAutomaticDebitInstructionRequest
import com.sigep.payments.application.dto.CreateAutomaticDebitMandateRequest
import com.sigep.payments.application.dto.CreateAdminAutomaticDebitMandateRequest
import com.sigep.payments.application.dto.RecordAutomaticDebitResultRequest
import com.sigep.payments.application.dto.ReverseAutomaticDebitRequest
import com.sigep.payments.application.dto.ResolveAutomaticDebitRejectionRequest
import com.sigep.payments.application.dto.SubmitAutomaticDebitInstructionRequest
import com.sigep.payments.application.dto.UpdateAutomaticDebitMandateRequest
import com.sigep.payments.application.service.AutomaticDebitService
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireGuardian
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/billing/me/debit-mandates")
@RequireGuardian
class GuardianAutomaticDebitController(
    private val service: AutomaticDebitService
) {
    @GetMapping
    fun list(httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<List<AutomaticDebitMandateDto>>> =
        ResponseEntity.ok(ApiResponse.success(service.getMyMandates(httpRequest.requireUserId())))

    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateAutomaticDebitMandateRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitMandateDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.createMyMandate(httpRequest.requireUserId(), request)))

    @PatchMapping("/{mandateId}")
    fun update(
        @PathVariable mandateId: Long,
        @Valid @RequestBody request: UpdateAutomaticDebitMandateRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitMandateDto>> = ResponseEntity.ok(
        ApiResponse.success(service.updateMyMandate(httpRequest.requireUserId(), mandateId, request))
    )
}

@RestController
@RequestMapping("/api/v1/billing/automatic-debit")
@RequireAdmin
class AdminAutomaticDebitController(
    private val service: AutomaticDebitService
) {
    @GetMapping("/mandates")
    fun listMandates(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<AutomaticDebitMandateDto>>> =
        ResponseEntity.ok(ApiResponse.success(service.listMandates(page, limit)))

    @GetMapping("/instructions")
    fun listInstructions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<AutomaticDebitInstructionDto>>> =
        ResponseEntity.ok(ApiResponse.success(service.listInstructions(page, limit)))

    @PostMapping("/mandates")
    fun createMandate(
        @Valid @RequestBody request: CreateAdminAutomaticDebitMandateRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitMandateDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(service.createAdminMandate(request, httpRequest.requireUserId())))

    @PatchMapping("/mandates/{mandateId}")
    fun updateMandate(
        @PathVariable mandateId: Long,
        @Valid @RequestBody request: UpdateAutomaticDebitMandateRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitMandateDto>> = ResponseEntity.ok(
        ApiResponse.success(service.updateMandateByAdmin(mandateId, request))
    )

    @PostMapping("/instructions")
    fun createInstruction(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateAutomaticDebitInstructionRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitInstructionDto>> = ResponseEntity
        .status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                service.createInstruction(idempotencyKey, request, httpRequest.requireUserId()),
                "Automatic debit data prepared from authorized invoice"
            )
        )

    @PostMapping("/instructions/{instructionId}/submission")
    fun submitInstruction(
        @PathVariable instructionId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: SubmitAutomaticDebitInstructionRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitInstructionDto>> = ResponseEntity.ok(
        ApiResponse.success(
            service.submitInstruction(instructionId, idempotencyKey, request),
            "Automatic debit marked as submitted"
        )
    )

    @PostMapping("/instructions/{instructionId}/results")
    fun recordResult(
        @PathVariable instructionId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: RecordAutomaticDebitResultRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitInstructionDto>> = ResponseEntity.ok(
        ApiResponse.success(
            service.recordResult(instructionId, idempotencyKey, request, httpRequest.requireUserId()),
            "Automatic debit result recorded"
        )
    )

    @PostMapping("/instructions/{instructionId}/resolution")
    fun resolveRejection(
        @PathVariable instructionId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: ResolveAutomaticDebitRejectionRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitInstructionDto>> = ResponseEntity.ok(
        ApiResponse.success(
            service.resolveRejection(instructionId, idempotencyKey, request, httpRequest.requireUserId()),
            "Automatic debit accounting resolution recorded"
        )
    )

    @PostMapping("/instructions/{instructionId}/cancellation")
    fun cancelInstruction(
        @PathVariable instructionId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: ReverseAutomaticDebitRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitInstructionDto>> = ResponseEntity.ok(
        ApiResponse.success(
            service.cancelInstruction(instructionId, idempotencyKey, request.reason),
            "Automatic debit preparation cancelled"
        )
    )

    @PostMapping("/instructions/{instructionId}/reversal")
    fun reverseInstruction(
        @PathVariable instructionId: Long,
        @Valid @RequestBody request: ReverseAutomaticDebitRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AutomaticDebitInstructionDto>> = ResponseEntity.ok(
        ApiResponse.success(
            service.reverseInstruction(instructionId, request.reason, httpRequest.requireUserId()),
            "Automatic debit reversed"
        )
    )

}

private fun HttpServletRequest.requireUserId(): Long = getAttribute("userId") as? Long
    ?: throw UnauthorizedException("Token invalid or missing userId")
