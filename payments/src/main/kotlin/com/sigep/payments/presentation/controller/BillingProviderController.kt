package com.sigep.payments.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalCatalogEntry
import com.sigep.security.application.annotation.RequireAdmin
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/billing/provider")
class BillingProviderController(
    private val fiscalAuthorityPort: FiscalAuthorityPort
) {

    @GetMapping("/health")
    @RequireAdmin
    fun health(): ResponseEntity<ApiResponse<FiscalProviderHealthDto>> {
        val health = fiscalAuthorityPort.health()
        return ResponseEntity.ok(
            ApiResponse.success(
                FiscalProviderHealthDto(
                    provider = health.provider,
                    environment = health.environment,
                    configured = health.configured,
                    available = health.available,
                    checkedAt = health.checkedAt,
                    message = health.message
                )
            )
        )
    }

    @GetMapping("/reference-data")
    @RequireAdmin
    fun referenceData(): ResponseEntity<ApiResponse<FiscalReferenceDataDto>> {
        val referenceData = fiscalAuthorityPort.referenceData()
        return ResponseEntity.ok(
            ApiResponse.success(
                FiscalReferenceDataDto(
                    voucherTypes = referenceData.voucherTypes.map { it.toDto() },
                    documentTypes = referenceData.documentTypes.map { it.toDto() },
                    receiverVatConditions = referenceData.receiverVatConditions.map { it.toDto() },
                    currencies = referenceData.currencies.map { it.toDto() },
                    retrievedAt = referenceData.retrievedAt
                )
            )
        )
    }
}

data class FiscalReferenceDataDto(
    val voucherTypes: List<FiscalCatalogEntryDto>,
    val documentTypes: List<FiscalCatalogEntryDto>,
    val receiverVatConditions: List<FiscalCatalogEntryDto>,
    val currencies: List<FiscalCatalogEntryDto>,
    val retrievedAt: LocalDateTime
)

data class FiscalCatalogEntryDto(
    val id: String,
    val description: String,
    val validFrom: LocalDate?,
    val validTo: LocalDate?
)

private fun FiscalCatalogEntry.toDto() = FiscalCatalogEntryDto(id, description, validFrom, validTo)

data class FiscalProviderHealthDto(
    val provider: String,
    val environment: FiscalEnvironment,
    val configured: Boolean,
    val available: Boolean,
    val checkedAt: LocalDateTime,
    val message: String?
)
