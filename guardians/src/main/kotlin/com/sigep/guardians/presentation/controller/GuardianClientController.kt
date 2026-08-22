package com.sigep.guardians.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.common.application.exception.ValidationException
import com.sigep.guardians.application.dto.GuardianClientDetailDto
import com.sigep.guardians.application.dto.GuardianClientStatsDto
import com.sigep.guardians.application.dto.GuardianClientSummaryDto
import com.sigep.guardians.application.dto.UpdateGuardianClientProfileRequest
import com.sigep.guardians.application.service.GuardianClientService
import com.sigep.guardians.domain.model.GuardianBillingFilter
import com.sigep.guardians.domain.model.GuardianClientSearchCriteria
import com.sigep.guardians.domain.model.GuardianRelationshipFilter
import com.sigep.security.application.annotation.RequireAdmin
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/guardian-clients")
@Tag(name = "Admin Guardian Clients", description = "Administrative guardian/client directory and cross-domain account view")
@SecurityRequirement(name = "Bearer Authentication")
@RequireAdmin
class GuardianClientController(
    private val service: GuardianClientService
) {

    @GetMapping
    @Operation(summary = "List guardian clients", description = "Lists guardians with student, enrollment, tuition and billing summaries")
    fun list(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) accountStatus: String?,
        @RequestParam(required = false) relationship: String?,
        @RequestParam(required = false) billing: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "lastName") sort: String,
        @RequestParam(defaultValue = "ASC") order: String
    ): ResponseEntity<ApiResponse<PageResponse<GuardianClientSummaryDto>>> {
        val criteria = GuardianClientSearchCriteria(
            search = search?.trim()?.takeIf { it.isNotEmpty() },
            accountStatus = parseAccountStatus(accountStatus),
            relationship = parseEnum<GuardianRelationshipFilter>(relationship, "relationship"),
            billing = parseEnum<GuardianBillingFilter>(billing, "billing"),
            page = page.coerceAtLeast(0),
            size = limit.coerceIn(1, 100),
            sort = sort,
            order = order
        )
        return ResponseEntity.ok(ApiResponse.success(service.list(criteria)))
    }

    @GetMapping("/stats")
    @Operation(summary = "Get guardian client indicators")
    fun stats(): ResponseEntity<ApiResponse<GuardianClientStatsDto>> =
        ResponseEntity.ok(ApiResponse.success(service.getStats()))

    @GetMapping("/{guardianUserId}")
    @Operation(summary = "Get guardian client detail", description = "Includes current students, enrollments, tuition applications, charges and allocated payments")
    fun detail(@PathVariable guardianUserId: Long): ResponseEntity<ApiResponse<GuardianClientDetailDto>> =
        ResponseEntity.ok(ApiResponse.success(service.getDetail(guardianUserId)))

    @PatchMapping("/{guardianUserId}/profile")
    @Operation(summary = "Update guardian client metadata", description = "Updates only client-owned metadata with optimistic locking")
    fun updateProfile(
        @PathVariable guardianUserId: Long,
        @Valid @RequestBody request: UpdateGuardianClientProfileRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<GuardianClientDetailDto>> {
        val adminId = httpRequest.getAttribute("userId") as? Long
            ?: throw UnauthorizedException("Token invalid or missing userId")
        return ResponseEntity.ok(ApiResponse.success(service.updateProfile(guardianUserId, request, adminId), "Guardian client profile updated"))
    }

    private fun parseAccountStatus(raw: String?): String? {
        val value = raw?.trim()?.uppercase()?.takeUnless { it.isEmpty() || it == "ALL" }
            ?: return null
        if (value !in setOf("PENDING_APPROVAL", "ACTIVE", "REJECTED")) {
            throw ValidationException("Invalid accountStatus: $raw", field = "accountStatus")
        }
        return value
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, field: String): T? {
        val value = raw?.trim()?.uppercase()?.takeUnless { it.isEmpty() || it == "ALL" }
            ?: return null
        return enumValues<T>().firstOrNull { it.name == value }
            ?: throw ValidationException("Invalid $field: $raw", field = field)
    }
}
