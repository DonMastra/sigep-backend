package com.sigep.tuition.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.dto.PageResponse
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.tuition.application.dto.CreateTuitionAcademicYearRequest
import com.sigep.tuition.application.dto.CreateTuitionDiscountRequest
import com.sigep.tuition.application.dto.CreateTuitionFeePlanRequest
import com.sigep.tuition.application.dto.CreateTuitionEnrollmentFeePolicyRequest
import com.sigep.tuition.application.dto.CreateTuitionLevelProgressionRequest
import com.sigep.tuition.application.dto.CreateTuitionLevelRequest
import com.sigep.tuition.application.dto.TuitionAcademicYearDto
import com.sigep.tuition.application.dto.TuitionDiscountDto
import com.sigep.tuition.application.dto.TuitionFeePlanDto
import com.sigep.tuition.application.dto.TuitionEnrollmentFeePolicyDto
import com.sigep.tuition.application.dto.TuitionLevelDto
import com.sigep.tuition.application.dto.TuitionLevelProgressionDto
import com.sigep.tuition.application.dto.UpdateTuitionAcademicYearRequest
import com.sigep.tuition.application.dto.UpdateTuitionDiscountRequest
import com.sigep.tuition.application.dto.UpdateTuitionFeePlanRequest
import com.sigep.tuition.application.dto.UpdateTuitionEnrollmentFeePolicyRequest
import com.sigep.tuition.application.dto.UpdateTuitionLevelProgressionRequest
import com.sigep.tuition.application.dto.UpdateTuitionLevelRequest
import com.sigep.tuition.application.service.TuitionCatalogService
import com.sigep.tuition.domain.model.TuitionAcademicYearStatus
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
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
@Tag(name = "Tuition Catalogs", description = "Administrative catalogs for tuition workflow")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('ADMIN', 'GUARDIAN')")
class TuitionCatalogController(
    private val tuitionCatalogService: TuitionCatalogService
) {

    @GetMapping("/academic-years")
    fun listAcademicYears(
        @RequestParam(required = false) status: TuitionAcademicYearStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TuitionAcademicYearDto>>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.listAcademicYears(status, page, limit)))

    @PostMapping("/academic-years")
    @RequireAdmin
    fun createAcademicYear(@Valid @RequestBody request: CreateTuitionAcademicYearRequest): ResponseEntity<ApiResponse<TuitionAcademicYearDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tuitionCatalogService.createAcademicYear(request), "Academic year created"))

    @PutMapping("/academic-years/{id}")
    @RequireAdmin
    fun updateAcademicYear(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTuitionAcademicYearRequest
    ): ResponseEntity<ApiResponse<TuitionAcademicYearDto>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.updateAcademicYear(id, request), "Academic year updated"))

    @DeleteMapping("/academic-years/{id}")
    @RequireAdmin
    fun deleteAcademicYear(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tuitionCatalogService.deleteAcademicYear(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Academic year deleted"))
    }

    @GetMapping("/levels")
    fun listLevels(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TuitionLevelDto>>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.listLevels(activeOnly, page, limit)))

    @PostMapping("/levels")
    @RequireAdmin
    fun createLevel(@Valid @RequestBody request: CreateTuitionLevelRequest): ResponseEntity<ApiResponse<TuitionLevelDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tuitionCatalogService.createLevel(request), "Tuition level created"))

    @PutMapping("/levels/{id}")
    @RequireAdmin
    fun updateLevel(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTuitionLevelRequest
    ): ResponseEntity<ApiResponse<TuitionLevelDto>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.updateLevel(id, request), "Tuition level updated"))

    @DeleteMapping("/levels/{id}")
    @RequireAdmin
    fun deleteLevel(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tuitionCatalogService.deleteLevel(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Tuition level deleted"))
    }

    @GetMapping("/level-progressions")
    fun listProgressions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TuitionLevelProgressionDto>>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.listProgressions(page, limit)))

    @PostMapping("/level-progressions")
    @RequireAdmin
    fun createProgression(@Valid @RequestBody request: CreateTuitionLevelProgressionRequest): ResponseEntity<ApiResponse<TuitionLevelProgressionDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tuitionCatalogService.createProgression(request), "Tuition level progression created"))

    @PutMapping("/level-progressions/{id}")
    @RequireAdmin
    fun updateProgression(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTuitionLevelProgressionRequest
    ): ResponseEntity<ApiResponse<TuitionLevelProgressionDto>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.updateProgression(id, request), "Tuition level progression updated"))

    @DeleteMapping("/level-progressions/{id}")
    @RequireAdmin
    fun deleteProgression(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tuitionCatalogService.deleteProgression(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Tuition level progression deleted"))
    }

    @GetMapping("/fee-plans")
    fun listFeePlans(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TuitionFeePlanDto>>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.listFeePlans(page, limit)))

    @PostMapping("/fee-plans")
    @RequireAdmin
    fun createFeePlan(@Valid @RequestBody request: CreateTuitionFeePlanRequest): ResponseEntity<ApiResponse<TuitionFeePlanDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tuitionCatalogService.createFeePlan(request), "Tuition fee plan created"))

    @PutMapping("/fee-plans/{id}")
    @RequireAdmin
    fun updateFeePlan(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTuitionFeePlanRequest
    ): ResponseEntity<ApiResponse<TuitionFeePlanDto>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.updateFeePlan(id, request), "Tuition fee plan updated"))

    @DeleteMapping("/fee-plans/{id}")
    @RequireAdmin
    fun deleteFeePlan(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tuitionCatalogService.deleteFeePlan(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Tuition fee plan deleted"))
    }

    @GetMapping("/enrollment-fee-policies")
    @RequireAdmin
    fun listEnrollmentFeePolicies(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TuitionEnrollmentFeePolicyDto>>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.listEnrollmentFeePolicies(page, limit)))

    @PostMapping("/enrollment-fee-policies")
    @RequireAdmin
    fun createEnrollmentFeePolicy(
        @Valid @RequestBody request: CreateTuitionEnrollmentFeePolicyRequest
    ): ResponseEntity<ApiResponse<TuitionEnrollmentFeePolicyDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(tuitionCatalogService.createEnrollmentFeePolicy(request), "Enrollment fee policy created")
        )

    @PutMapping("/enrollment-fee-policies/{id}")
    @RequireAdmin
    fun updateEnrollmentFeePolicy(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTuitionEnrollmentFeePolicyRequest
    ): ResponseEntity<ApiResponse<TuitionEnrollmentFeePolicyDto>> =
        ResponseEntity.ok(
            ApiResponse.success(tuitionCatalogService.updateEnrollmentFeePolicy(id, request), "Enrollment fee policy updated")
        )

    @DeleteMapping("/enrollment-fee-policies/{id}")
    @RequireAdmin
    fun deleteEnrollmentFeePolicy(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tuitionCatalogService.deleteEnrollmentFeePolicy(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Enrollment fee policy deleted"))
    }

    @GetMapping("/discounts")
    @RequireAdmin
    fun listDiscounts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<ApiResponse<PageResponse<TuitionDiscountDto>>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.listDiscounts(page, limit)))

    @PostMapping("/discounts")
    @RequireAdmin
    fun createDiscount(@Valid @RequestBody request: CreateTuitionDiscountRequest): ResponseEntity<ApiResponse<TuitionDiscountDto>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tuitionCatalogService.createDiscount(request), "Tuition discount created"))

    @PutMapping("/discounts/{id}")
    @RequireAdmin
    fun updateDiscount(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTuitionDiscountRequest
    ): ResponseEntity<ApiResponse<TuitionDiscountDto>> =
        ResponseEntity.ok(ApiResponse.success(tuitionCatalogService.updateDiscount(id, request), "Tuition discount updated"))

    @DeleteMapping("/discounts/{id}")
    @RequireAdmin
    fun deleteDiscount(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tuitionCatalogService.deleteDiscount(id)
        return ResponseEntity.ok(ApiResponse.successNoContent("Tuition discount deleted"))
    }
}
