package com.sigep.tuition.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.CourseEnrollmentCommandProvider
import com.sigep.common.application.service.BillingChargeCommand
import com.sigep.common.application.service.BillingChargeProvider
import com.sigep.common.application.service.GuardianAccountProvider
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.tuition.application.dto.CreateTuitionApplicationRequest
import com.sigep.tuition.application.dto.CreateTuitionEnrollmentChargeRequest
import com.sigep.tuition.application.dto.TuitionAcademicAssignmentRequest
import com.sigep.tuition.application.dto.TuitionApplicationDto
import com.sigep.tuition.application.dto.TuitionDecisionRequest
import com.sigep.tuition.application.dto.TuitionEnrollmentFeePolicyDto
import com.sigep.tuition.application.dto.TuitionFeePlanDto
import com.sigep.tuition.application.dto.TuitionLedgerEntryDto
import com.sigep.tuition.application.dto.TuitionPlacementAssessmentDto
import com.sigep.tuition.application.dto.TuitionPlacementRequest
import com.sigep.tuition.domain.model.TuitionAcademicYear
import com.sigep.tuition.domain.model.TuitionAcademicYearStatus
import com.sigep.tuition.domain.model.TuitionApplication
import com.sigep.tuition.domain.model.TuitionApplicationStatus
import com.sigep.tuition.domain.model.TuitionApplicationType
import com.sigep.tuition.domain.model.TuitionDiscount
import com.sigep.tuition.domain.model.TuitionFeePlan
import com.sigep.tuition.domain.model.TuitionFeePlanStatus
import com.sigep.tuition.domain.model.TuitionEnrollmentFeePolicy
import com.sigep.tuition.domain.model.TuitionEnrollmentFeePolicyStatus
import com.sigep.tuition.domain.model.TuitionLedgerConcept
import com.sigep.tuition.domain.model.TuitionLedgerEntry
import com.sigep.tuition.domain.model.TuitionLedgerStatus
import com.sigep.tuition.domain.model.TuitionLevel
import com.sigep.tuition.domain.model.TuitionProgressionRule
import com.sigep.tuition.domain.model.TuitionPlacementAssessment
import com.sigep.tuition.domain.model.TuitionPlacementStatus
import com.sigep.tuition.domain.repository.TuitionAcademicYearRepository
import com.sigep.tuition.domain.repository.TuitionApplicationRepository
import com.sigep.tuition.domain.repository.TuitionDiscountRepository
import com.sigep.tuition.domain.repository.TuitionFeePlanRepository
import com.sigep.tuition.domain.repository.TuitionEnrollmentFeePolicyRepository
import com.sigep.tuition.domain.repository.TuitionLedgerEntryRepository
import com.sigep.tuition.domain.repository.TuitionLevelProgressionRepository
import com.sigep.tuition.domain.repository.TuitionLevelRepository
import com.sigep.tuition.domain.repository.TuitionPlacementAssessmentRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Service
@Transactional
class TuitionApplicationService(
    private val applicationRepository: TuitionApplicationRepository,
    private val academicYearRepository: TuitionAcademicYearRepository,
    private val levelRepository: TuitionLevelRepository,
    private val progressionRepository: TuitionLevelProgressionRepository,
    private val feePlanRepository: TuitionFeePlanRepository,
    private val enrollmentFeePolicyRepository: TuitionEnrollmentFeePolicyRepository,
    private val discountRepository: TuitionDiscountRepository,
    private val ledgerEntryRepository: TuitionLedgerEntryRepository,
    private val placementAssessmentRepository: TuitionPlacementAssessmentRepository,
    private val studentProfileProvider: StudentProfileProvider,
    private val courseEnrollmentCommandProvider: CourseEnrollmentCommandProvider,
    private val guardianAccountProvider: GuardianAccountProvider,
    private val billingChargeProvider: BillingChargeProvider
) {

    private val logger = LoggerFactory.getLogger(TuitionApplicationService::class.java)

    fun createApplication(guardianUserId: Long, request: CreateTuitionApplicationRequest): TuitionApplicationDto {
        val guardian = guardianAccountProvider.getGuardianAccount(guardianUserId)
            ?: throw ForbiddenException("Only GUARDIAN users can create tuition applications")

        validateStudentInput(guardianUserId, request)
        val now = LocalDateTime.now()

        val application = TuitionApplication(
            guardianUserId = guardian.id,
            studentId = request.studentId,
            studentFirstName = request.studentFirstName?.trim(),
            studentLastName = request.studentLastName?.trim(),
            studentEmail = request.studentEmail?.trim(),
            studentDocumentNumber = request.studentDocumentNumber?.trim(),
            studentDateOfBirth = request.studentDateOfBirth,
            studentAddress = request.studentAddress?.trim(),
            studentPhoneNumber = request.studentPhoneNumber?.trim(),
            studentEmergencyContact = request.studentEmergencyContact?.trim(),
            studentMedicalNotes = request.studentMedicalNotes?.trim(),
            applicationType = request.applicationType,
            status = TuitionApplicationStatus.SUBMITTED,
            submittedAt = now,
            createdAt = now,
            updatedAt = now
        )

        val saved = applicationRepository.save(application)
        logger.info("Tuition application {} created for guardian {}", saved.id, guardianUserId)
        return saved.toDto()
    }

    fun createEnrollmentCharge(
        applicationId: Long,
        request: CreateTuitionEnrollmentChargeRequest
    ): TuitionApplicationDto {
        val application = getApplication(applicationId)
        if (application.status !in setOf(TuitionApplicationStatus.SUBMITTED, TuitionApplicationStatus.PAYMENT_PENDING)) {
            throw BusinessException("Enrollment charge cannot be created from status ${application.status}")
        }

        val existingEntry = ledgerEntryRepository
            .findByApplicationIdAndConcept(applicationId, TuitionLedgerConcept.TUITION_ENROLLMENT)
            .firstOrNull()
        if (existingEntry != null) {
            syncBillingCharge(application, existingEntry)
            return application.toDto()
        }

        val policy = resolveEnrollmentFeePolicy(request.enrollmentFeePolicyId)
        val now = LocalDateTime.now()
        val updated = applicationRepository.save(
            application.copy(
                enrollmentFeePolicy = policy,
                status = TuitionApplicationStatus.PAYMENT_PENDING,
                updatedAt = now
            )
        )
        ensureEnrollmentLedger(updated)
        logger.info("Enrollment charge created for tuition application {} with policy {}", applicationId, policy.id)
        return updated.toDto()
    }

    @Transactional(readOnly = true)
    fun getMyApplications(guardianUserId: Long, page: Int, size: Int): PageResponse<TuitionApplicationDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        return applicationRepository.findByGuardianUserId(guardianUserId, pageable).toPageResponse { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun listApplications(
        status: TuitionApplicationStatus?,
        academicYearId: Long?,
        page: Int,
        size: Int
    ): PageResponse<TuitionApplicationDto> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        return applicationRepository.findByFilters(status, academicYearId, pageable).toPageResponse { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getApplicationDetail(applicationId: Long): TuitionApplicationDto =
        applicationRepository.findById(applicationId)
            .orElseThrow { ResourceNotFoundException("Tuition application not found with id: $applicationId") }
            .toDto()

    fun recordPlacement(
        applicationId: Long,
        evaluatorUserId: Long,
        request: TuitionPlacementRequest
    ): TuitionApplicationDto {
        val application = getApplication(applicationId)
        if (!isEnrollmentPaid(applicationId)) {
            throw BusinessException("Enrollment fee must be fully paid before placement")
        }
        if (application.status !in setOf(
                TuitionApplicationStatus.ENROLLED_PENDING_PLACEMENT,
                TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT,
                TuitionApplicationStatus.WAITLISTED
            )
        ) {
            throw BusinessException("Placement cannot be recorded from status ${application.status}")
        }
        if (request.status == TuitionPlacementStatus.PENDING) {
            throw ValidationException("Placement result must be COMPLETED or WAIVED", field = "status")
        }
        if (request.status == TuitionPlacementStatus.COMPLETED && request.recommendedLevelId == null) {
            throw ValidationException("recommendedLevelId is required for a completed placement", field = "recommendedLevelId")
        }
        val recommendedLevel = request.recommendedLevelId?.let(::getActiveLevel)
        val now = LocalDateTime.now()
        val existing = placementAssessmentRepository.findByApplicationId(applicationId).orElse(null)
        val assessment = if (existing == null) {
            TuitionPlacementAssessment(
                application = application,
                status = request.status,
                recommendedLevel = recommendedLevel,
                evaluatorUserId = evaluatorUserId,
                notes = request.notes?.trim(),
                assessedAt = now,
                createdAt = now,
                updatedAt = now
            )
        } else {
            existing.copy(
                status = request.status,
                recommendedLevel = recommendedLevel,
                evaluatorUserId = evaluatorUserId,
                notes = request.notes?.trim(),
                assessedAt = now,
                updatedAt = now
            )
        }
        placementAssessmentRepository.save(assessment)
        applicationRepository.save(
            application.copy(
                status = TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT,
                updatedAt = now
            )
        )
        return getApplication(applicationId).toDto()
    }

    fun assignAcademicPlacement(
        applicationId: Long,
        adminUserId: Long,
        request: TuitionAcademicAssignmentRequest
    ): TuitionApplicationDto {
        val application = getApplication(applicationId)
        if (application.status == TuitionApplicationStatus.APPROVED) {
            if (application.academicYear?.id == request.academicYearId &&
                application.assignedLevel?.id == request.levelId &&
                application.assignedCourseId == request.courseId &&
                application.feePlan?.id == request.feePlanId
            ) {
                return application.toDto()
            }
            throw ResourceConflictException("Academic assignment is already completed")
        }
        if (application.status !in setOf(TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT, TuitionApplicationStatus.WAITLISTED)) {
            throw BusinessException("Academic assignment cannot be completed from status ${application.status}")
        }
        if (!isEnrollmentPaid(applicationId)) {
            throw BusinessException("Enrollment fee must remain fully paid before academic assignment")
        }

        val placement = placementAssessmentRepository.findByApplicationId(applicationId)
            .orElseThrow { ResourceConflictException("Placement assessment is required before academic assignment") }
        if (placement.status !in setOf(TuitionPlacementStatus.COMPLETED, TuitionPlacementStatus.WAIVED)) {
            throw BusinessException("Placement assessment is not complete")
        }

        val academicYear = getOpenAcademicYear(request.academicYearId)
        val level = getActiveLevel(request.levelId)
        val plan = feePlanRepository.findById(request.feePlanId)
            .orElseThrow { ResourceNotFoundException("Tuition fee plan not found with id: ${request.feePlanId}") }
        validateFeePlan(plan, academicYear, level, LocalDate.now())

        val availability = courseEnrollmentCommandProvider.getCourseSeatAvailability(request.courseId)
        val mappedCourseLevel = resolveCourseLevel(level)
            ?: throw ValidationException("Selected tuition level is not mapped to a course level", field = "levelId")
        if (availability.courseLevel != mappedCourseLevel) {
            throw ValidationException("Selected level does not match course level", field = "courseId")
        }
        if (placement.recommendedLevel != null && placement.recommendedLevel.id != level.id && request.adminNotes.isNullOrBlank()) {
            throw ValidationException("Administrative notes are required when assigning a level different from placement", field = "adminNotes")
        }

        val progression = evaluateProgression(application, level)
        if (progression.requiresAdminOverride && request.adminNotes.isNullOrBlank()) {
            throw ValidationException("Administrative notes are required for this progression exception", field = "adminNotes")
        }

        val now = LocalDateTime.now()
        val assigned = applicationRepository.save(
            application.copy(
                academicYear = academicYear,
                assignedLevel = level,
                assignedCourseId = request.courseId,
                feePlan = plan,
                warningMessage = progression.warning,
                progressionRule = progression.rule,
                requiresAdminOverride = progression.requiresAdminOverride,
                adminNotes = request.adminNotes,
                status = if (availability.availableSeats <= 0) TuitionApplicationStatus.WAITLISTED else application.status,
                updatedAt = now
            )
        )
        if (availability.availableSeats <= 0) {
            return assigned.toDto()
        }
        if (!availability.enrollmentOpen) {
            throw BusinessException("Course is not active, published, staffed and scheduled for enrollment")
        }

        val installmentDueDates = calculateMonthlyInstallmentDueDates(
            plan = plan,
            academicYear = academicYear,
            enrolledOn = now.toLocalDate()
        )

        val studentId = assigned.studentId
            ?: throw ResourceConflictException("Student must be created before academic assignment")
        val enrollment = courseEnrollmentCommandProvider.createActiveEnrollment(
            studentId = studentId,
            courseId = request.courseId,
            notes = "Created by tuition application ${application.id}"
        )
        studentProfileProvider.updateCurrentLevel(studentId, level.code)
        val approved = applicationRepository.save(
            assigned.copy(
                enrollmentId = enrollment.enrollmentId,
                status = TuitionApplicationStatus.APPROVED,
                approvedAt = now,
                approvedBy = adminUserId,
                updatedAt = now
            )
        )
        ensureMonthlyLedgers(approved, studentId, installmentDueDates)
        logger.info("Tuition application {} assigned to course {} by admin {}", applicationId, request.courseId, adminUserId)
        return approved.toDto()
    }

    fun rejectApplication(applicationId: Long, adminUserId: Long, request: TuitionDecisionRequest): TuitionApplicationDto {
        val application = getApplication(applicationId)
        if (application.status in setOf(TuitionApplicationStatus.APPROVED, TuitionApplicationStatus.REJECTED, TuitionApplicationStatus.CANCELLED)) {
            throw BusinessException("Tuition application cannot be rejected from status ${application.status}")
        }

        val ledgerEntries = ledgerEntryRepository.findByApplicationId(applicationId)
        if (ledgerEntries.any { it.paidAmount > BigDecimal.ZERO || it.status in setOf(TuitionLedgerStatus.PARTIALLY_PAID, TuitionLedgerStatus.PAID) }) {
            throw BusinessException("Tuition application with confirmed payments must be reversed or refunded before rejection")
        }

        val now = LocalDateTime.now()
        ledgerEntries.forEach { entry ->
            ledgerEntryRepository.save(entry.copy(status = TuitionLedgerStatus.CANCELLED, updatedAt = now))
            billingChargeProvider.cancelCharge(BILLING_SOURCE_TYPE, requireNotNull(entry.id))
        }

        val rejected = applicationRepository.save(
            application.copy(
                status = TuitionApplicationStatus.REJECTED,
                adminNotes = request.adminNotes,
                approvedBy = adminUserId,
                updatedAt = now
            )
        )
        logger.info("Tuition application {} rejected by admin {}", applicationId, adminUserId)
        return rejected.toDto()
    }

    private fun getOpenAcademicYear(id: Long): TuitionAcademicYear {
        val academicYear = academicYearRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Academic year not found with id: $id") }
        if (academicYear.status != TuitionAcademicYearStatus.OPEN) {
            throw BusinessException("Academic year must be OPEN to receive tuition applications")
        }
        return academicYear
    }

    private fun getActiveLevel(id: Long): TuitionLevel {
        val level = levelRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Tuition level not found with id: $id") }
        if (!level.active) {
            throw BusinessException("Tuition level ${level.code} is not active")
        }
        return level
    }

    private fun resolveEnrollmentFeePolicy(policyId: Long?): TuitionEnrollmentFeePolicy {
        val today = LocalDate.now()
        val policy = if (policyId != null) {
            enrollmentFeePolicyRepository.findById(policyId)
                .orElseThrow { ResourceNotFoundException("Enrollment fee policy not found with id: $policyId") }
        } else {
            enrollmentFeePolicyRepository.findActiveCandidates(
                TuitionEnrollmentFeePolicyStatus.ACTIVE,
                today
            ).firstOrNull()
                ?: throw ResourceNotFoundException("No active enrollment fee policy is configured")
        }
        if (policy.status != TuitionEnrollmentFeePolicyStatus.ACTIVE ||
            policy.validFrom.isAfter(today) ||
            (policy.validTo != null && policy.validTo.isBefore(today))
        ) {
            throw ValidationException("Enrollment fee policy is not active for the current date", field = "enrollmentFeePolicyId")
        }
        return policy
    }

    private fun isEnrollmentPaid(applicationId: Long): Boolean =
        ledgerEntryRepository.existsByApplicationIdAndConceptAndStatus(
            applicationId,
            TuitionLedgerConcept.TUITION_ENROLLMENT,
            TuitionLedgerStatus.PAID
        )

    private fun getApplication(id: Long): TuitionApplication =
        applicationRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Tuition application not found with id: $id") }

    private fun getOwnedApplication(applicationId: Long, guardianUserId: Long): TuitionApplication {
        val application = getApplication(applicationId)
        if (application.guardianUserId != guardianUserId) {
            throw ForbiddenException("Guardian can only access own tuition applications")
        }
        return application
    }

    private fun validateStudentInput(guardianUserId: Long, request: CreateTuitionApplicationRequest) {
        when (request.applicationType) {
            TuitionApplicationType.REGULAR_PROMOTION -> {
                val studentId = request.studentId
                    ?: throw ValidationException("studentId is required for REGULAR_PROMOTION", field = "studentId")
                if (!studentProfileProvider.validateGuardianOwnsStudent(guardianUserId, studentId)) {
                    throw ForbiddenException("Guardian does not own student $studentId")
                }
            }

            TuitionApplicationType.NEW_STUDENT,
            TuitionApplicationType.ADDITIONAL_STUDENT -> {
                if (request.studentId != null) {
                    if (!studentProfileProvider.validateGuardianOwnsStudent(guardianUserId, request.studentId)) {
                        throw ForbiddenException("Guardian does not own student ${request.studentId}")
                    }
                    return
                }
                val missing = listOfNotNull(
                    "studentFirstName".takeIf { request.studentFirstName.isNullOrBlank() },
                    "studentLastName".takeIf { request.studentLastName.isNullOrBlank() },
                    "studentEmail".takeIf { request.studentEmail.isNullOrBlank() },
                    "studentDocumentNumber".takeIf { request.studentDocumentNumber.isNullOrBlank() },
                    "studentDateOfBirth".takeIf { request.studentDateOfBirth == null },
                    "studentAddress".takeIf { request.studentAddress.isNullOrBlank() },
                    "studentPhoneNumber".takeIf { request.studentPhoneNumber.isNullOrBlank() },
                    "studentEmergencyContact".takeIf { request.studentEmergencyContact.isNullOrBlank() }
                )
                if (missing.isNotEmpty()) {
                    throw ValidationException("Missing student fields for new tuition application", missing)
                }
            }
        }
    }

    private fun evaluateProgression(application: TuitionApplication, requestedLevel: TuitionLevel): ProgressionEvaluation {
        if (application.applicationType != TuitionApplicationType.REGULAR_PROMOTION || application.studentId == null) {
            return ProgressionEvaluation()
        }

        val latestCompleted = courseEnrollmentCommandProvider.getLatestCompletedEnrollment(application.studentId)
            ?: throw ValidationException("Student must pass the previous level before requesting promotion", field = "studentId")

        val currentLevel = studentProfileProvider.getStudentProfile(application.studentId)?.currentLevel
        val fromLevel = currentLevel?.let { levelRepository.findByCode(it).orElse(null) }
            ?: levelRepository.findAll()
                .filter { it.active && resolveCourseLevel(it) == latestCompleted.courseLevel }
                .minByOrNull { it.levelOrder }
            ?: throw ValidationException(
                "Completed course level ${latestCompleted.courseLevel} is not mapped to tuition levels",
                field = "studentId"
            )

        val progression = progressionRepository.findByFromLevelIdAndActiveTrue(fromLevel.id!!).orElse(null)
            ?: throw ValidationException("No active tuition progression exists from level ${fromLevel.code}", field = "levelId")

        if (progression.toLevel.id != requestedLevel.id) {
            throw ValidationException(
                "Requested level ${requestedLevel.code} does not match allowed destination ${progression.toLevel.code}",
                field = "levelId"
            )
        }

        if (progression.rule == TuitionProgressionRule.PASS_PREVIOUS_LEVEL &&
            resolveCourseLevel(fromLevel) != latestCompleted.courseLevel) {
            throw ValidationException("Student has not passed the configured origin level ${fromLevel.code}", field = "studentId")
        }

        return if (progression.rule == TuitionProgressionRule.ADMIN_APPROVAL) {
            ProgressionEvaluation(
                rule = progression.rule,
                requiresAdminOverride = true,
                warning = "Esta progresión es una excepción y requiere aprobación administrativa con una nota."
            )
        } else {
            ProgressionEvaluation(rule = progression.rule)
        }
    }

    /**
     * Resolves the course enum used by the courses module from a tuition level.
     * Legacy catalogs may still store A1/A2 in code or leave courseLevel null.
     */
    private fun resolveCourseLevel(level: TuitionLevel): String? {
        val value = level.courseLevel?.trim()?.uppercase()
            ?: level.code.trim().uppercase()
        return when (value) {
            "A1" -> "BEGINNER"
            "A2" -> "ELEMENTARY"
            else -> value.takeIf { it.isNotBlank() }
        }
    }

    private fun validateFeePlan(
        plan: TuitionFeePlan,
        academicYear: TuitionAcademicYear,
        requestedLevel: TuitionLevel,
        today: LocalDate
    ) {
        if (plan.academicYear.id != academicYear.id) {
            throw ValidationException("Fee plan does not belong to selected academic year", field = "feePlanId")
        }
        if (plan.status != TuitionFeePlanStatus.ACTIVE || plan.validFrom.isAfter(today) || (plan.validTo != null && plan.validTo.isBefore(today))) {
            throw ValidationException("Fee plan is not active for the current date", field = "feePlanId")
        }
        if (plan.level != null && plan.level.id != requestedLevel.id) {
            throw ValidationException("Fee plan level does not match requested level", field = "feePlanId")
        }
        if (plan.segment != null && plan.segment != requestedLevel.segment) {
            throw ValidationException("Fee plan segment does not match requested level segment", field = "feePlanId")
        }
    }

    private fun ensureEnrollmentLedger(application: TuitionApplication): TuitionLedgerEntry {
        val existing = ledgerEntryRepository.findByApplicationIdAndConcept(application.id!!, TuitionLedgerConcept.TUITION_ENROLLMENT)
            .firstOrNull()
        if (existing != null) {
            syncBillingCharge(application, existing)
            return existing
        }

        val policy = application.enrollmentFeePolicy
            ?: throw ResourceConflictException("Enrollment fee policy is required before creating the charge")
        val now = LocalDateTime.now()
        val saved = ledgerEntryRepository.save(
            TuitionLedgerEntry(
                application = application,
                studentId = application.studentId,
                concept = TuitionLedgerConcept.TUITION_ENROLLMENT,
                grossAmount = policy.amount,
                discountAmount = BigDecimal.ZERO,
                netAmount = policy.amount.normalizeMoney(),
                dueDate = LocalDate.now().plusDays(policy.paymentDueDays.toLong()),
                status = TuitionLedgerStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
        syncBillingCharge(application, saved)
        return saved
    }

    private fun ensureMonthlyLedgers(
        application: TuitionApplication,
        studentId: Long,
        installmentDueDates: List<LocalDate>
    ) {
        val plan = application.feePlan
            ?: throw ResourceConflictException("Fee plan is required before generating monthly charges")
        val level = application.assignedLevel
            ?: throw ResourceConflictException("Assigned level is required before generating monthly charges")
        application.academicYear
            ?: throw ResourceConflictException("Academic year is required before generating monthly charges")
        val existingMonthly = ledgerEntryRepository.findByApplicationIdAndConcept(application.id!!, TuitionLedgerConcept.MONTHLY_FEE)
        if (existingMonthly.isNotEmpty()) {
            existingMonthly.forEach { syncBillingCharge(application, it, studentId) }
            return
        }

        val now = LocalDateTime.now()
        val entries = installmentDueDates.map { dueDate ->
            val appliedDiscount = calculateDiscount(
                grossAmount = plan.monthlyFee,
                studentId = studentId,
                requestedLevel = level
            )
            TuitionLedgerEntry(
                application = application,
                studentId = studentId,
                discount = appliedDiscount.discount,
                concept = TuitionLedgerConcept.MONTHLY_FEE,
                grossAmount = plan.monthlyFee,
                discountAmount = appliedDiscount.amount,
                netAmount = (plan.monthlyFee - appliedDiscount.amount).normalizeMoney(),
                dueDate = dueDate,
                status = TuitionLedgerStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        }
        ledgerEntryRepository.saveAll(entries).forEach { syncBillingCharge(application, it, studentId) }
    }

    private fun calculateMonthlyInstallmentDueDates(
        plan: TuitionFeePlan,
        academicYear: TuitionAcademicYear,
        enrolledOn: LocalDate
    ): List<LocalDate> {
        val firstMonth = maxOf(
            YearMonth.from(enrolledOn),
            YearMonth.from(plan.validFrom),
            YearMonth.from(academicYear.startDate)
        )
        val lastMonth = minOf(
            YearMonth.from(plan.validTo ?: academicYear.endDate),
            YearMonth.from(academicYear.endDate)
        )
        if (firstMonth.isAfter(lastMonth)) {
            throw BusinessException("No monthly installments remain in the selected plan")
        }

        val availableMonths = ((lastMonth.year - firstMonth.year) * 12 +
            lastMonth.monthValue - firstMonth.monthValue + 1)
        val installmentCount = minOf(plan.installments, availableMonths)
        return (0 until installmentCount).map { index ->
            val month = firstMonth.plusMonths(index.toLong())
            val scheduledDueDate = month.atDay(plan.monthlyDueDay)
            if (index == 0 && scheduledDueDate.isBefore(enrolledOn)) enrolledOn else scheduledDueDate
        }
    }

    private fun syncBillingCharge(
        application: TuitionApplication,
        entry: TuitionLedgerEntry,
        resolvedStudentId: Long? = entry.studentId
    ) {
        val guardian = guardianAccountProvider.getGuardianAccount(application.guardianUserId)
            ?: throw ResourceNotFoundException("Guardian account ${application.guardianUserId} not found")
        val student = resolvedStudentId?.let(studentProfileProvider::getStudentProfile)
        val studentName = listOfNotNull(
            student?.firstName ?: application.studentFirstName,
            student?.lastName ?: application.studentLastName
        ).joinToString(" ").trim().ifEmpty { "Estudiante solicitud ${application.id}" }
        val monthlyPeriod = YearMonth.from(entry.dueDate)
        val description = when (entry.concept) {
            TuitionLedgerConcept.TUITION_ENROLLMENT ->
                "Matricula - $studentName"
            TuitionLedgerConcept.MONTHLY_FEE ->
                "Cuota ${entry.dueDate.monthValue}/${entry.dueDate.year} - $studentName"
        }
        billingChargeProvider.upsertCharge(
            BillingChargeCommand(
                guardianUserId = application.guardianUserId,
                studentId = resolvedStudentId,
                studentName = studentName,
                sourceType = BILLING_SOURCE_TYPE,
                sourceId = requireNotNull(entry.id),
                concept = entry.concept.name,
                description = description,
                amount = entry.netAmount,
                currency = when (entry.concept) {
                    TuitionLedgerConcept.TUITION_ENROLLMENT -> requireNotNull(application.enrollmentFeePolicy).currency
                    TuitionLedgerConcept.MONTHLY_FEE -> requireNotNull(application.feePlan).currency
                },
                dueDate = entry.dueDate,
                serviceFrom = if (entry.concept == TuitionLedgerConcept.MONTHLY_FEE) {
                    monthlyPeriod.atDay(1)
                } else {
                    entry.dueDate
                },
                serviceTo = if (entry.concept == TuitionLedgerConcept.MONTHLY_FEE) {
                    monthlyPeriod.atEndOfMonth()
                } else {
                    entry.dueDate
                },
                receiverName = "${guardian.firstName} ${guardian.lastName}".trim(),
                receiverAddress = guardian.address,
                receiverDocumentNumber = guardian.documentNumber,
                lateFeePercentage = if (entry.concept == TuitionLedgerConcept.MONTHLY_FEE) {
                    requireNotNull(application.feePlan).lateFeePercentage
                } else BigDecimal.ZERO,
                lateFeeEligible = entry.concept == TuitionLedgerConcept.MONTHLY_FEE,
                automaticDebitEligible = when (entry.concept) {
                    TuitionLedgerConcept.TUITION_ENROLLMENT -> requireNotNull(application.enrollmentFeePolicy).automaticDebitEligible
                    TuitionLedgerConcept.MONTHLY_FEE -> requireNotNull(application.feePlan).automaticDebitMonthly
                }
            )
        )
    }

    private fun calculateDiscount(
        grossAmount: BigDecimal,
        studentId: Long?,
        requestedLevel: TuitionLevel
    ): AppliedDiscount {
        if (grossAmount <= BigDecimal.ZERO) {
            return AppliedDiscount(null, BigDecimal.ZERO)
        }

        val today = LocalDate.now()
        val discount = discountRepository.findActiveCandidates(studentId, today)
            .filter { candidate ->
                (candidate.studentId == null || candidate.studentId == studentId) &&
                    (candidate.level == null || candidate.level.id == requestedLevel.id) &&
                    (candidate.segment == null || candidate.segment == requestedLevel.segment)
            }
            .maxByOrNull { candidate ->
                var score = 0
                if (candidate.studentId != null) score += 100
                if (candidate.level != null) score += 10
                if (candidate.segment != null) score += 1
                score
            }
            ?: return AppliedDiscount(null, BigDecimal.ZERO)

        val rawAmount = discount.percentage
            ?.let { grossAmount.multiply(it).divide(BigDecimal("100.00"), 2, RoundingMode.HALF_UP) }
            ?: discount.amount
        return AppliedDiscount(discount, rawAmount.min(grossAmount).normalizeMoney())
    }

    private fun TuitionApplication.toDto(): TuitionApplicationDto {
        val placement = id?.let { placementAssessmentRepository.findByApplicationId(it).orElse(null) }
        val ledgerEntries = id?.let { ledgerEntryRepository.findByApplicationId(it) }.orEmpty()
        return TuitionApplicationDto(
            id = id!!,
            guardianUserId = guardianUserId,
            studentId = studentId,
            studentFirstName = studentFirstName,
            studentLastName = studentLastName,
            studentEmail = studentEmail,
            studentDocumentNumber = studentDocumentNumber,
            assignedAcademicYearId = academicYear?.id,
            assignedAcademicYearName = academicYear?.name,
            assignedLevelId = assignedLevel?.id,
            assignedLevelCode = assignedLevel?.code,
            assignedLevelName = assignedLevel?.name,
            assignedCourseId = assignedCourseId,
            applicationType = applicationType,
            status = status,
            feePlan = feePlan?.toDto(),
            enrollmentFeePolicy = enrollmentFeePolicy?.toDto(),
            placement = placement?.toDto(),
            enrollmentId = enrollmentId,
            warningMessage = warningMessage,
            progressionRule = progressionRule,
            requiresAdminOverride = requiresAdminOverride,
            adminNotes = adminNotes,
            ledgerEntries = ledgerEntries.map { it.toDto() },
            submittedAt = submittedAt,
            approvedAt = approvedAt,
            approvedBy = approvedBy,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun TuitionFeePlan.toDto() = TuitionFeePlanDto(
        id = id!!,
        academicYearId = academicYear.id!!,
        academicYearName = academicYear.name,
        name = name,
        segment = segment,
        levelId = level?.id,
        levelCode = level?.code,
        monthlyFee = monthlyFee,
        installments = installments,
        monthlyDueDay = monthlyDueDay,
        lateFeePercentage = lateFeePercentage,
        automaticDebitMonthly = automaticDebitMonthly,
        currency = currency,
        validFrom = validFrom,
        validTo = validTo,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TuitionEnrollmentFeePolicy.toDto() = TuitionEnrollmentFeePolicyDto(
        id = id!!,
        name = name,
        amount = amount,
        currency = currency,
        paymentDueDays = paymentDueDays,
        automaticDebitEligible = automaticDebitEligible,
        validFrom = validFrom,
        validTo = validTo,
        status = status,
        defaultPolicy = defaultPolicy,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TuitionPlacementAssessment.toDto() = TuitionPlacementAssessmentDto(
        id = id!!,
        applicationId = application.id!!,
        status = status,
        recommendedLevelId = recommendedLevel?.id,
        recommendedLevelCode = recommendedLevel?.code,
        recommendedLevelName = recommendedLevel?.name,
        evaluatorUserId = evaluatorUserId,
        notes = notes,
        assessedAt = assessedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun TuitionLedgerEntry.toDto() = TuitionLedgerEntryDto(
        id = id!!,
        applicationId = application.id!!,
        studentId = studentId,
        discountId = discount?.id,
        concept = concept,
        grossAmount = grossAmount,
        discountAmount = discountAmount,
        netAmount = netAmount,
        paidAmount = paidAmount,
        lateFeeAmount = lateFeeAmount,
        totalAmount = netAmount + lateFeeAmount,
        outstandingAmount = (netAmount + lateFeeAmount - paidAmount).max(BigDecimal.ZERO),
        dueDate = dueDate,
        status = status,
        billingReference = billingReference,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun <T, R> Page<T>.toPageResponse(mapper: (T) -> R): PageResponse<R> =
        PageResponse(
            content = content.map(mapper),
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages
        )

    private fun BigDecimal.normalizeMoney(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

    private data class AppliedDiscount(
        val discount: TuitionDiscount?,
        val amount: BigDecimal
    )

    private data class ProgressionEvaluation(
        val rule: TuitionProgressionRule? = null,
        val requiresAdminOverride: Boolean = false,
        val warning: String? = null
    )

    private companion object {
        const val BILLING_SOURCE_TYPE = "TUITION_LEDGER"
        const val PENDING_PLACEMENT_LEVEL = "PENDING_PLACEMENT"
    }
}
