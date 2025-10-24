package com.sigep.courses.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.courses.application.dto.*
import com.sigep.courses.application.service.CertificateService
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.annotation.RequireAdminOrTeacher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Course certificates management")
class CertificateController(
    private val certificateService: CertificateService
) {

    @GetMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get certificate by ID")
    fun getCertificateById(@PathVariable id: Long): ResponseEntity<ApiResponse<CertificateDto>> {
        val certificate = certificateService.getCertificateById(id)
        return ResponseEntity.ok(ApiResponse.success(certificate))
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get certificate by code")
    fun getCertificateByCode(@PathVariable code: String): ResponseEntity<ApiResponse<CertificateDto>> {
        val certificate = certificateService.getCertificateByCode(code)
        return ResponseEntity.ok(ApiResponse.success(certificate))
    }

    @GetMapping("/student/{studentId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get certificates by student")
    fun getCertificatesByStudent(
        @PathVariable studentId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CertificateDto>>> {
        val certificates = certificateService.getCertificatesByStudent(studentId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(certificates))
    }

    @GetMapping("/course/{courseId}")
    @RequireAdminOrTeacher
    @Operation(summary = "Get certificates by course")
    fun getCertificatesByCourse(
        @PathVariable courseId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<CertificateDto>>> {
        val certificates = certificateService.getCertificatesByCourse(courseId, page, limit)
        return ResponseEntity.ok(ApiResponse.success(certificates))
    }

    @PostMapping
    @RequireAdminOrTeacher
    @Operation(summary = "Issue a certificate")
    fun issueCertificate(
        @Valid @RequestBody request: CreateCertificateRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<CertificateDto>> {
        val issuedBy = authentication.name.toLongOrNull() ?: 1L
        val certificate = certificateService.issueCertificate(request, issuedBy)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(certificate, "Certificate issued successfully"))
    }

    @PutMapping("/{id}")
    @RequireAdminOrTeacher
    @Operation(summary = "Update certificate")
    fun updateCertificate(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCertificateRequest
    ): ResponseEntity<ApiResponse<CertificateDto>> {
        val certificate = certificateService.updateCertificate(id, request)
        return ResponseEntity.ok(ApiResponse.success(certificate, "Certificate updated successfully"))
    }

    @PostMapping("/{id}/revoke")
    @RequireAdmin
    @Operation(summary = "Revoke a certificate")
    fun revokeCertificate(
        @PathVariable id: Long,
        @Valid @RequestBody request: RevokeCertificateRequest,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<CertificateDto>> {
        val revokedBy = authentication.name.toLongOrNull() ?: 1L
        val certificate = certificateService.revokeCertificate(id, request, revokedBy)
        return ResponseEntity.ok(ApiResponse.success(certificate, "Certificate revoked successfully"))
    }

    @GetMapping("/verify/{code}")
    @Operation(summary = "Verify a certificate (public endpoint)")
    fun verifyCertificate(@PathVariable code: String): ResponseEntity<ApiResponse<VerifyCertificateDto>> {
        val verification = certificateService.verifyCertificate(code)
        return ResponseEntity.ok(ApiResponse.success(verification))
    }

    @GetMapping("/statistics")
    @RequireAdmin
    @Operation(summary = "Get certificate statistics")
    fun getCertificateStatistics(): ResponseEntity<ApiResponse<CertificateStatisticsDto>> {
        val statistics = certificateService.getCertificateStatistics()
        return ResponseEntity.ok(ApiResponse.success(statistics))
    }

    @PostMapping("/process-expired")
    @RequireAdmin
    @Operation(summary = "Process expired certificates (manual trigger)")
    fun processExpiredCertificates(): ResponseEntity<ApiResponse<Int>> {
        val count = certificateService.processExpiredCertificates()
        return ResponseEntity.ok(ApiResponse.success(count, "$count certificates processed"))
    }
}

