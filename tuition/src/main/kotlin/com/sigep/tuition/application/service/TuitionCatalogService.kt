package com.sigep.tuition.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.tuition.application.dto.CreateTuitionAcademicYearRequest
import com.sigep.tuition.application.dto.CreateTuitionDiscountRequest
import com.sigep.tuition.application.dto.CreateTuitionFeePlanRequest
import com.sigep.tuition.application.dto.CreateTuitionLevelProgressionRequest
import com.sigep.tuition.application.dto.CreateTuitionLevelRequest
import com.sigep.tuition.application.dto.TuitionAcademicYearDto
import com.sigep.tuition.application.dto.TuitionDiscountDto
import com.sigep.tuition.application.dto.TuitionFeePlanDto
import com.sigep.tuition.application.dto.TuitionLevelDto
import com.sigep.tuition.application.dto.TuitionLevelProgressionDto
import com.sigep.tuition.application.dto.UpdateTuitionAcademicYearRequest
import com.sigep.tuition.application.dto.UpdateTuitionDiscountRequest
import com.sigep.tuition.application.dto.UpdateTuitionFeePlanRequest
import com.sigep.tuition.application.dto.UpdateTuitionLevelProgressionRequest
import com.sigep.tuition.application.dto.UpdateTuitionLevelRequest
import com.sigep.tuition.domain.model.TuitionAcademicYear
import com.sigep.tuition.domain.model.TuitionAcademicYearStatus
import com.sigep.tuition.domain.model.TuitionDiscount
import com.sigep.tuition.domain.model.TuitionFeePlan
import com.sigep.tuition.domain.model.TuitionLevel
import com.sigep.tuition.domain.model.TuitionLevelProgression
import com.sigep.tuition.domain.repository.TuitionAcademicYearRepository
import com.sigep.tuition.domain.repository.TuitionDiscountRepository
import com.sigep.tuition.domain.repository.TuitionFeePlanRepository
import com.sigep.tuition.domain.repository.TuitionLevelProgressionRepository
import com.sigep.tuition.domain.repository.TuitionLevelRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
@Transactional
class TuitionCatalogService(
    private val academicYearRepository: TuitionAcademicYearRepository,
    private val levelRepository: TuitionLevelRepository,
    private val progressionRepository: TuitionLevelProgressionRepository,
    private val feePlanRepository: TuitionFeePlanRepository,
    private val discountRepository: TuitionDiscountRepository
) {

    fun listAcademicYears(status: TuitionAcademicYearStatus?, page: Int, size: Int): PageResponse<TuitionAcademicYearDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "startDate"))
        val result = if (status != null) {
            academicYearRepository.findByStatus(status, pageable)
        } else {
            academicYearRepository.findAll(pageable)
        }
        return result.toPageResponse { it.toDto() }
    }

    fun createAcademicYear(request: CreateTuitionAcademicYearRequest): TuitionAcademicYearDto {
        validateAcademicYearDates(
            request.startDate,
            request.firstTermStartDate,
            request.firstTermEndDate,
            request.secondTermStartDate,
            request.secondTermEndDate,
            request.endDate
        )
        if (academicYearRepository.existsByName(request.name)) {
            throw DuplicateResourceException("Academic year '${request.name}' already exists")
        }
        val now = LocalDateTime.now()
        return academicYearRepository.save(
            TuitionAcademicYear(
                name = request.name,
                startDate = request.startDate,
                firstTermStartDate = request.firstTermStartDate,
                firstTermEndDate = request.firstTermEndDate,
                secondTermStartDate = request.secondTermStartDate,
                secondTermEndDate = request.secondTermEndDate,
                endDate = request.endDate,
                status = request.status,
                createdAt = now,
                updatedAt = now
            )
        ).toDto()
    }

    fun updateAcademicYear(id: Long, request: UpdateTuitionAcademicYearRequest): TuitionAcademicYearDto {
        val existing = academicYearRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Academic year not found with id: $id") }

        val updated = existing.copy(
            name = request.name ?: existing.name,
            startDate = request.startDate ?: existing.startDate,
            firstTermStartDate = request.firstTermStartDate ?: existing.firstTermStartDate,
            firstTermEndDate = request.firstTermEndDate ?: existing.firstTermEndDate,
            secondTermStartDate = request.secondTermStartDate ?: existing.secondTermStartDate,
            secondTermEndDate = request.secondTermEndDate ?: existing.secondTermEndDate,
            endDate = request.endDate ?: existing.endDate,
            status = request.status ?: existing.status,
            updatedAt = LocalDateTime.now()
        )
        validateAcademicYearDates(
            updated.startDate,
            updated.firstTermStartDate,
            updated.firstTermEndDate,
            updated.secondTermStartDate,
            updated.secondTermEndDate,
            updated.endDate
        )
        return academicYearRepository.save(updated).toDto()
    }

    fun deleteAcademicYear(id: Long) {
        if (!academicYearRepository.existsById(id)) {
            throw ResourceNotFoundException("Academic year not found with id: $id")
        }
        academicYearRepository.deleteById(id)
    }

    fun listLevels(activeOnly: Boolean, page: Int, size: Int): PageResponse<TuitionLevelDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("levelOrder"))
        val result = if (activeOnly) levelRepository.findByActiveTrue(pageable) else levelRepository.findAll(pageable)
        return result.toPageResponse { it.toDto() }
    }

    fun createLevel(request: CreateTuitionLevelRequest): TuitionLevelDto {
        val normalizedCode = request.code.trim().uppercase()
        if (levelRepository.existsByCode(normalizedCode)) {
            throw DuplicateResourceException("Tuition level '$normalizedCode' already exists")
        }
        val now = LocalDateTime.now()
        return levelRepository.save(
            TuitionLevel(
                code = normalizedCode,
                name = request.name.trim(),
                segment = request.segment,
                levelOrder = request.levelOrder,
                active = request.active,
                createdAt = now,
                updatedAt = now
            )
        ).toDto()
    }

    fun updateLevel(id: Long, request: UpdateTuitionLevelRequest): TuitionLevelDto {
        val existing = levelRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Tuition level not found with id: $id") }
        val normalizedCode = request.code?.trim()?.uppercase()
        if (normalizedCode != null && normalizedCode != existing.code && levelRepository.existsByCode(normalizedCode)) {
            throw DuplicateResourceException("Tuition level '$normalizedCode' already exists")
        }
        return levelRepository.save(
            existing.copy(
                code = normalizedCode ?: existing.code,
                name = request.name?.trim() ?: existing.name,
                segment = request.segment ?: existing.segment,
                levelOrder = request.levelOrder ?: existing.levelOrder,
                active = request.active ?: existing.active,
                updatedAt = LocalDateTime.now()
            )
        ).toDto()
    }

    fun deleteLevel(id: Long) {
        if (!levelRepository.existsById(id)) {
            throw ResourceNotFoundException("Tuition level not found with id: $id")
        }
        levelRepository.deleteById(id)
    }

    fun listProgressions(page: Int, size: Int): PageResponse<TuitionLevelProgressionDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by("fromLevel.levelOrder"))
        return progressionRepository.findAll(pageable).toPageResponse { it.toDto() }
    }

    fun createProgression(request: CreateTuitionLevelProgressionRequest): TuitionLevelProgressionDto {
        if (request.fromLevelId == request.toLevelId) {
            throw ValidationException("fromLevelId and toLevelId must be different")
        }
        if (request.active && progressionRepository.existsByFromLevelIdAndActiveTrue(request.fromLevelId)) {
            throw BusinessException("An active progression already exists for fromLevelId ${request.fromLevelId}")
        }
        val fromLevel = getLevel(request.fromLevelId)
        val toLevel = getLevel(request.toLevelId)
        val now = LocalDateTime.now()
        return progressionRepository.save(
            TuitionLevelProgression(
                fromLevel = fromLevel,
                toLevel = toLevel,
                rule = request.rule,
                active = request.active,
                createdAt = now,
                updatedAt = now
            )
        ).toDto()
    }

    fun updateProgression(id: Long, request: UpdateTuitionLevelProgressionRequest): TuitionLevelProgressionDto {
        val existing = progressionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Tuition level progression not found with id: $id") }
        val toLevel = request.toLevelId?.let { getLevel(it) } ?: existing.toLevel
        if (existing.fromLevel.id == toLevel.id) {
            throw ValidationException("fromLevelId and toLevelId must be different")
        }
        return progressionRepository.save(
            existing.copy(
                toLevel = toLevel,
                rule = request.rule ?: existing.rule,
                active = request.active ?: existing.active,
                updatedAt = LocalDateTime.now()
            )
        ).toDto()
    }

    fun deleteProgression(id: Long) {
        if (!progressionRepository.existsById(id)) {
            throw ResourceNotFoundException("Tuition level progression not found with id: $id")
        }
        progressionRepository.deleteById(id)
    }

    fun listFeePlans(page: Int, size: Int): PageResponse<TuitionFeePlanDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        return feePlanRepository.findAll(pageable).toPageResponse { it.toDto() }
    }

    fun createFeePlan(request: CreateTuitionFeePlanRequest): TuitionFeePlanDto {
        validateValidity(request.validFrom, request.validTo)
        val academicYear = getAcademicYear(request.academicYearId)
        val level = request.levelId?.let { getLevel(it) }
        validatePlanScope(request.segment, level)
        val now = LocalDateTime.now()
        return feePlanRepository.save(
            TuitionFeePlan(
                academicYear = academicYear,
                name = request.name.trim(),
                segment = request.segment,
                level = level,
                enrollmentFee = request.enrollmentFee,
                monthlyFee = request.monthlyFee,
                installments = request.installments,
                currency = request.currency.uppercase(),
                validFrom = request.validFrom,
                validTo = request.validTo,
                status = request.status,
                createdAt = now,
                updatedAt = now
            )
        ).toDto()
    }

    fun updateFeePlan(id: Long, request: UpdateTuitionFeePlanRequest): TuitionFeePlanDto {
        val existing = feePlanRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Tuition fee plan not found with id: $id") }
        val level = request.levelId?.let { getLevel(it) } ?: existing.level
        val segment = request.segment ?: existing.segment
        val validFrom = request.validFrom ?: existing.validFrom
        val validTo = request.validTo ?: existing.validTo
        validateValidity(validFrom, validTo)
        validatePlanScope(segment, level)

        return feePlanRepository.save(
            existing.copy(
                name = request.name?.trim() ?: existing.name,
                segment = segment,
                level = level,
                enrollmentFee = request.enrollmentFee ?: existing.enrollmentFee,
                monthlyFee = request.monthlyFee ?: existing.monthlyFee,
                installments = request.installments ?: existing.installments,
                currency = request.currency?.uppercase() ?: existing.currency,
                validFrom = validFrom,
                validTo = validTo,
                status = request.status ?: existing.status,
                updatedAt = LocalDateTime.now()
            )
        ).toDto()
    }

    fun deleteFeePlan(id: Long) {
        if (!feePlanRepository.existsById(id)) {
            throw ResourceNotFoundException("Tuition fee plan not found with id: $id")
        }
        feePlanRepository.deleteById(id)
    }

    fun listDiscounts(page: Int, size: Int): PageResponse<TuitionDiscountDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        return discountRepository.findAll(pageable).toPageResponse { it.toDto() }
    }

    fun createDiscount(request: CreateTuitionDiscountRequest): TuitionDiscountDto {
        validateDiscount(request.percentage, request.amount, request.validFrom, request.validTo)
        val level = request.levelId?.let { getLevel(it) }
        val now = LocalDateTime.now()
        return discountRepository.save(
            TuitionDiscount(
                studentId = request.studentId,
                segment = request.segment,
                level = level,
                type = request.type,
                percentage = request.percentage,
                amount = request.amount,
                validFrom = request.validFrom,
                validTo = request.validTo,
                reason = request.reason,
                active = request.active,
                createdAt = now,
                updatedAt = now
            )
        ).toDto()
    }

    fun updateDiscount(id: Long, request: UpdateTuitionDiscountRequest): TuitionDiscountDto {
        val existing = discountRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Tuition discount not found with id: $id") }
        val validFrom = request.validFrom ?: existing.validFrom
        val validTo = request.validTo ?: existing.validTo
        val percentage = request.percentage ?: existing.percentage
        val amount = request.amount ?: existing.amount
        validateDiscount(percentage, amount, validFrom, validTo)
        val level = request.levelId?.let { getLevel(it) } ?: existing.level

        return discountRepository.save(
            existing.copy(
                studentId = request.studentId ?: existing.studentId,
                segment = request.segment ?: existing.segment,
                level = level,
                type = request.type ?: existing.type,
                percentage = percentage,
                amount = amount,
                validFrom = validFrom,
                validTo = validTo,
                reason = request.reason ?: existing.reason,
                active = request.active ?: existing.active,
                updatedAt = LocalDateTime.now()
            )
        ).toDto()
    }

    fun deleteDiscount(id: Long) {
        if (!discountRepository.existsById(id)) {
            throw ResourceNotFoundException("Tuition discount not found with id: $id")
        }
        discountRepository.deleteById(id)
    }

    private fun getAcademicYear(id: Long): TuitionAcademicYear =
        academicYearRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Academic year not found with id: $id") }

    private fun getLevel(id: Long): TuitionLevel =
        levelRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Tuition level not found with id: $id") }

    private fun validateAcademicYearDates(
        startDate: java.time.LocalDate,
        firstTermStartDate: java.time.LocalDate,
        firstTermEndDate: java.time.LocalDate,
        secondTermStartDate: java.time.LocalDate,
        secondTermEndDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ) {
        if (firstTermStartDate.isBefore(startDate) ||
            firstTermEndDate.isBefore(firstTermStartDate) ||
            secondTermStartDate.isBefore(firstTermEndDate) ||
            secondTermEndDate.isBefore(secondTermStartDate) ||
            endDate.isBefore(secondTermEndDate)
        ) {
            throw ValidationException("Academic year dates are not in a valid chronological order")
        }
    }

    private fun validateValidity(validFrom: java.time.LocalDate, validTo: java.time.LocalDate?) {
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw ValidationException("validTo cannot be before validFrom")
        }
    }

    private fun validatePlanScope(segment: com.sigep.tuition.domain.model.TuitionSegment?, level: TuitionLevel?) {
        if (segment != null && level != null && level.segment != segment) {
            throw ValidationException("Fee plan segment must match the selected level segment")
        }
    }

    private fun validateDiscount(
        percentage: BigDecimal?,
        amount: BigDecimal,
        validFrom: java.time.LocalDate,
        validTo: java.time.LocalDate?
    ) {
        validateValidity(validFrom, validTo)
        if (percentage == null && amount <= BigDecimal.ZERO) {
            throw ValidationException("Discount requires a positive percentage or amount")
        }
        if (percentage != null && (percentage <= BigDecimal.ZERO || percentage > BigDecimal("100.00"))) {
            throw ValidationException("Discount percentage must be greater than 0 and up to 100")
        }
    }

    private fun <T, R> org.springframework.data.domain.Page<T>.toPageResponse(mapper: (T) -> R): PageResponse<R> =
        PageResponse(
            content = content.map(mapper),
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages
        )

    private fun TuitionAcademicYear.toDto() = TuitionAcademicYearDto(
        id = id!!,
        name = name,
        startDate = startDate,
        firstTermStartDate = firstTermStartDate,
        firstTermEndDate = firstTermEndDate,
        secondTermStartDate = secondTermStartDate,
        secondTermEndDate = secondTermEndDate,
        endDate = endDate,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TuitionLevel.toDto() = TuitionLevelDto(
        id = id!!,
        code = code,
        name = name,
        segment = segment,
        levelOrder = levelOrder,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TuitionLevelProgression.toDto() = TuitionLevelProgressionDto(
        id = id!!,
        fromLevelId = fromLevel.id!!,
        fromLevelCode = fromLevel.code,
        toLevelId = toLevel.id!!,
        toLevelCode = toLevel.code,
        rule = rule,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TuitionFeePlan.toDto() = TuitionFeePlanDto(
        id = id!!,
        academicYearId = academicYear.id!!,
        academicYearName = academicYear.name,
        name = name,
        segment = segment,
        levelId = level?.id,
        levelCode = level?.code,
        enrollmentFee = enrollmentFee,
        monthlyFee = monthlyFee,
        installments = installments,
        currency = currency,
        validFrom = validFrom,
        validTo = validTo,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TuitionDiscount.toDto() = TuitionDiscountDto(
        id = id!!,
        studentId = studentId,
        segment = segment,
        levelId = level?.id,
        levelCode = level?.code,
        type = type,
        percentage = percentage,
        amount = amount,
        validFrom = validFrom,
        validTo = validTo,
        reason = reason,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
