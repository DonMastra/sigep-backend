package com.sigep.tuition.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import com.sigep.security.application.annotation.RequireGuardian
import com.sigep.tuition.application.dto.CreateTuitionApplicationRequest
import com.sigep.tuition.application.dto.CreateTuitionEnrollmentChargeRequest
import com.sigep.tuition.application.dto.TuitionAcademicAssignmentRequest
import com.sigep.tuition.application.dto.TuitionApplicationDto
import com.sigep.tuition.application.dto.TuitionDecisionRequest
import com.sigep.tuition.application.dto.TuitionPlacementRequest
import com.sigep.tuition.application.service.TuitionApplicationService
import com.sigep.tuition.domain.model.TuitionApplicationStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tuition")
@Tag(name = "Tuition", description = "Tuition and matriculation workflow")
@SecurityRequirement(name = "Bearer Authentication")
class TuitionApplicationController(
    private val tuitionApplicationService: TuitionApplicationService
) {

    @PostMapping("/applications")
    @RequireGuardian
    @Operation(summary = "Create tuition application", description = "Creates a submitted tuition application for the authenticated guardian")
    fun createApplication(
        @Valid @RequestBody request: CreateTuitionApplicationRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<TuitionApplicationDto>> {
        val guardianId = httpRequest.requireUserId()
        val application = tuitionApplicationService.createApplication(guardianId, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(application, "Tuition application created"))
    }

    @GetMapping("/my-applications")
    @RequireGuardian
    @Operation(summary = "List own tuition applications", description = "Lists tuition applications for the authenticated guardian")
    fun myApplications(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<PageResponse<TuitionApplicationDto>>> {
        val guardianId = httpRequest.requireUserId()
        return ResponseEntity.ok(ApiResponse.success(tuitionApplicationService.getMyApplications(guardianId, page, limit)))
    }

    @GetMapping("/applications")
    @RequireAdmin
    @Operation(summary = "List tuition applications", description = "Admin list with optional status and academic year filters")
    fun listApplications(
        @RequestParam(required = false) status: TuitionApplicationStatus?,
        @RequestParam(required = false) academicYearId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TuitionApplicationDto>>> {
        return ResponseEntity.ok(ApiResponse.success(tuitionApplicationService.listApplications(status, academicYearId, page, limit)))
    }

    @GetMapping("/applications/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get tuition application", description = "Returns one tuition application with placement and ledger details")
    fun getApplication(@PathVariable id: Long): ResponseEntity<ApiResponse<TuitionApplicationDto>> =
        ResponseEntity.ok(ApiResponse.success(tuitionApplicationService.getApplicationDetail(id)))

    @PostMapping("/applications/{id}/enrollment-charge")
    @RequireAdmin
    @Operation(summary = "Create enrollment charge", description = "Creates the idempotent enrollment charge using a separate institutional policy")
    fun createEnrollmentCharge(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateTuitionEnrollmentChargeRequest
    ): ResponseEntity<ApiResponse<TuitionApplicationDto>> =
        ResponseEntity.ok(ApiResponse.success(tuitionApplicationService.createEnrollmentCharge(id, request), "Enrollment charge created"))

    @PutMapping("/applications/{id}/placement")
    @RequireAdminOrTeacher
    @Operation(summary = "Record placement", description = "Records or replaces the audited placement result after the enrollment fee is paid")
    fun recordPlacement(
        @PathVariable id: Long,
        @Valid @RequestBody request: TuitionPlacementRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<TuitionApplicationDto>> =
        ResponseEntity.ok(
            ApiResponse.success(
                tuitionApplicationService.recordPlacement(id, httpRequest.requireUserId(), request),
                "Placement recorded"
            )
        )

    @PutMapping("/applications/{id}/assignment")
    @RequireAdmin
    @Operation(summary = "Assign academic placement", description = "Assigns academic year, level, fee plan and course after payment and placement")
    fun assignAcademicPlacement(
        @PathVariable id: Long,
        @Valid @RequestBody request: TuitionAcademicAssignmentRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<TuitionApplicationDto>> =
        ResponseEntity.ok(
            ApiResponse.success(
                tuitionApplicationService.assignAcademicPlacement(id, httpRequest.requireUserId(), request),
                "Academic assignment completed"
            )
        )

    @PutMapping("/applications/{id}/reject")
    @RequireAdmin
    @Operation(summary = "Reject tuition application", description = "Rejects an unpaid tuition application and cancels its ledger")
    fun rejectApplication(
        @PathVariable id: Long,
        @RequestBody(required = false) request: TuitionDecisionRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<TuitionApplicationDto>> {
        val adminId = httpRequest.requireUserId()
        val application = tuitionApplicationService.rejectApplication(id, adminId, request ?: TuitionDecisionRequest())
        return ResponseEntity.ok(ApiResponse.success(application, "Tuition application rejected"))
    }

    private fun HttpServletRequest.requireUserId(): Long =
        getAttribute("userId") as? Long
            ?: throw UnauthorizedException("Token invalid or missing userId")
}
