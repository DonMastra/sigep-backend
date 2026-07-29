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
import com.sigep.common.application.service.StudentProfileCreateRequest
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.tuition.application.dto.CreateTuitionApplicationRequest
import com.sigep.tuition.application.dto.TuitionApplicationDto
import com.sigep.tuition.application.dto.TuitionDecisionRequest
import com.sigep.tuition.application.dto.TuitionFeePlanDto
import com.sigep.tuition.application.dto.TuitionLedgerEntryDto
import com.sigep.tuition.application.dto.TuitionSeatReservationDto
import com.sigep.tuition.domain.model.TuitionAcademicYear
import com.sigep.tuition.domain.model.TuitionAcademicYearStatus
import com.sigep.tuition.domain.model.TuitionApplication
import com.sigep.tuition.domain.model.TuitionApplicationStatus
import com.sigep.tuition.domain.model.TuitionApplicationType
import com.sigep.tuition.domain.model.TuitionDiscount
import com.sigep.tuition.domain.model.TuitionFeePlan
import com.sigep.tuition.domain.model.TuitionFeePlanStatus
import com.sigep.tuition.domain.model.TuitionLedgerConcept
import com.sigep.tuition.domain.model.TuitionLedgerEntry
import com.sigep.tuition.domain.model.TuitionLedgerStatus
import com.sigep.tuition.domain.model.TuitionLevel
import com.sigep.tuition.domain.model.TuitionProgressionRule
import com.sigep.tuition.domain.model.TuitionSeatReservation
import com.sigep.tuition.domain.model.TuitionSeatReservationStatus
import com.sigep.tuition.domain.repository.TuitionAcademicYearRepository
import com.sigep.tuition.domain.repository.TuitionApplicationRepository
import com.sigep.tuition.domain.repository.TuitionDiscountRepository
import com.sigep.tuition.domain.repository.TuitionFeePlanRepository
import com.sigep.tuition.domain.repository.TuitionLedgerEntryRepository
import com.sigep.tuition.domain.repository.TuitionLevelProgressionRepository
import com.sigep.tuition.domain.repository.TuitionLevelRepository
import com.sigep.tuition.domain.repository.TuitionSeatReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
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
    private val discountRepository: TuitionDiscountRepository,
    private val seatReservationRepository: TuitionSeatReservationRepository,
    private val ledgerEntryRepository: TuitionLedgerEntryRepository,
    private val studentProfileProvider: StudentProfileProvider,
    private val courseEnrollmentCommandProvider: CourseEnrollmentCommandProvider,
    private val guardianAccountProvider: GuardianAccountProvider,
    private val billingChargeProvider: BillingChargeProvider
) {

    private val logger = LoggerFactory.getLogger(TuitionApplicationService::class.java)
    private val reservationHours = 48L

    fun createApplication(guardianUserId: Long, request: CreateTuitionApplicationRequest): TuitionApplicationDto {
        val guardian = guardianAccountProvider.getGuardianAccount(guardianUserId)
            ?: throw ForbiddenException("Only GUARDIAN users can create tuition applications")

        val academicYear = getOpenAcademicYear(request.academicYearId)
        val requestedLevel = getActiveLevel(request.requestedLevelId)
        val seatAvailability = courseEnrollmentCommandProvider.getCourseSeatAvailability(request.requestedCourseId)
        val mappedCourseLevel = resolveCourseLevel(requestedLevel)
            ?: throw ValidationException("Requested tuition level is not mapped to a course level", field = "requestedLevelId")
        if (seatAvailability.courseLevel != mappedCourseLevel) {
            throw ValidationException(
                message = "Requested level does not match course level",
                field = "requestedLevelId",
                details = "Course level=${seatAvailability.courseLevel}, requestedLevel=$mappedCourseLevel"
            )
        }

        validateStudentInput(guardianUserId, request)
        val progression = evaluateProgression(request, requestedLevel)
        val feePlan = resolveFeePlan(request.feePlanId, academicYear, requestedLevel)
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
            academicYear = academicYear,
            requestedLevel = requestedLevel,
            requestedCourseId = request.requestedCourseId,
            applicationType = request.applicationType,
            status = TuitionApplicationStatus.SUBMITTED,
            feePlan = feePlan,
            warningMessage = progression.warning,
            progressionRule = progression.rule,
            requiresAdminOverride = progression.requiresAdminOverride,
            submittedAt = now,
            createdAt = now,
            updatedAt = now
        )

        val saved = applicationRepository.save(application)
        logger.info("Tuition application {} created for guardian {}", saved.id, guardianUserId)
        return saved.toDto()
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

    fun reserveSeat(applicationId: Long, guardianUserId: Long): TuitionApplicationDto {
        val application = getOwnedApplication(applicationId, guardianUserId)
        if (application.status !in setOf(TuitionApplicationStatus.SUBMITTED, TuitionApplicationStatus.SEAT_RESERVED, TuitionApplicationStatus.PAYMENT_PENDING)) {
            throw BusinessException("Tuition application cannot reserve a seat from status ${application.status}")
        }

        val existingReservation = seatReservationRepository.findByApplicationId(applicationId).orElse(null)
        if (existingReservation?.status == TuitionSeatReservationStatus.ACTIVE && existingReservation.expiresAt.isAfter(LocalDateTime.now())) {
            ensureEnrollmentLedger(application)
            val refreshed = applicationRepository.save(application.copy(status = TuitionApplicationStatus.PAYMENT_PENDING, updatedAt = LocalDateTime.now()))
            return refreshed.toDto()
        }

        ensureCourseHasAvailableSeat(application.requestedCourseId)
        val now = LocalDateTime.now()
        val updatedApplication = applicationRepository.save(
            application.copy(status = TuitionApplicationStatus.PAYMENT_PENDING, updatedAt = now)
        )

        if (existingReservation == null) {
            seatReservationRepository.save(
                TuitionSeatReservation(
                    application = updatedApplication,
                    courseId = updatedApplication.requestedCourseId,
                    quantity = 1,
                    expiresAt = now.plusHours(reservationHours),
                    status = TuitionSeatReservationStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            seatReservationRepository.save(
                existingReservation.copy(
                    application = updatedApplication,
                    courseId = updatedApplication.requestedCourseId,
                    quantity = 1,
                    expiresAt = now.plusHours(reservationHours),
                    status = TuitionSeatReservationStatus.ACTIVE,
                    updatedAt = now
                )
            )
        }

        ensureEnrollmentLedger(updatedApplication)
        logger.info("Seat reserved for tuition application {}", applicationId)
        return updatedApplication.toDto()
    }

    fun approveApplication(applicationId: Long, adminUserId: Long, request: TuitionDecisionRequest): TuitionApplicationDto {
        val application = getApplication(applicationId)
        if (application.status != TuitionApplicationStatus.READY_FOR_ADMIN_APPROVAL) {
            throw BusinessException("Tuition application must be READY_FOR_ADMIN_APPROVAL before approval")
        }

        val reservation = seatReservationRepository.findByApplicationId(applicationId)
            .orElseThrow { ResourceConflictException("Tuition application has no seat reservation") }
        if (reservation.status != TuitionSeatReservationStatus.ACTIVE || reservation.expiresAt.isBefore(LocalDateTime.now())) {
            throw ResourceConflictException("Tuition application seat reservation is not active")
        }

        val enrollmentFeePaid = ledgerEntryRepository.existsByApplicationIdAndConceptAndStatus(
            applicationId,
            TuitionLedgerConcept.TUITION_ENROLLMENT,
            TuitionLedgerStatus.PAID
        )
        if (!enrollmentFeePaid) {
            throw BusinessException("Initial tuition enrollment fee must be paid before approval")
        }
        if (application.requiresAdminOverride && request.adminNotes.isNullOrBlank()) {
            throw ValidationException(
                message = "Administrative notes are required to approve this progression exception",
                field = "adminNotes"
            )
        }

        guardianAccountProvider.activateGuardianForTuition(application.guardianUserId, adminUserId, request.adminNotes)
        val studentId = resolveOrCreateStudent(application)
        val enrollment = courseEnrollmentCommandProvider.createActiveEnrollment(
            studentId = studentId,
            courseId = application.requestedCourseId,
            notes = "Created by tuition application ${application.id}"
        )
        studentProfileProvider.updateCurrentLevel(studentId, application.requestedLevel.code)

        val now = LocalDateTime.now()
        val approvedApplication = applicationRepository.save(
            application.copy(
                studentId = studentId,
                enrollmentId = enrollment.enrollmentId,
                status = TuitionApplicationStatus.APPROVED,
                adminNotes = request.adminNotes,
                approvedAt = now,
                approvedBy = adminUserId,
                updatedAt = now
            )
        )

        ledgerEntryRepository.findByApplicationId(applicationId)
            .filter { it.studentId == null }
            .forEach {
                val updatedEntry = ledgerEntryRepository.save(it.copy(studentId = studentId, updatedAt = now))
                syncBillingCharge(approvedApplication, updatedEntry, studentId)
            }
        ensureMonthlyLedgers(approvedApplication, studentId)
        seatReservationRepository.save(
            reservation.copy(
                application = approvedApplication,
                status = TuitionSeatReservationStatus.CONFIRMED,
                updatedAt = now
            )
        )

        logger.info("Tuition application {} approved by admin {}", applicationId, adminUserId)
        return approvedApplication.toDto()
    }

    fun rejectApplication(applicationId: Long, adminUserId: Long, request: TuitionDecisionRequest): TuitionApplicationDto {
        val application = getApplication(applicationId)
        if (application.status in setOf(TuitionApplicationStatus.APPROVED, TuitionApplicationStatus.REJECTED, TuitionApplicationStatus.CANCELLED)) {
            throw BusinessException("Tuition application cannot be rejected from status ${application.status}")
        }

        val now = LocalDateTime.now()
        seatReservationRepository.findByApplicationId(applicationId).ifPresent { reservation ->
            seatReservationRepository.save(
                reservation.copy(status = TuitionSeatReservationStatus.RELEASED, updatedAt = now)
            )
        }
        ledgerEntryRepository.findByApplicationId(applicationId).forEach { entry ->
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

    @Scheduled(fixedDelayString = "\${app.tuition.reservation-expiration-fixed-delay-ms:600000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun expireUnpaidReservations() {
        val now = LocalDateTime.now()
        val expirableStatuses = setOf(
            TuitionApplicationStatus.SUBMITTED,
            TuitionApplicationStatus.SEAT_RESERVED,
            TuitionApplicationStatus.PAYMENT_PENDING
        )

        try {
            seatReservationRepository.findExpiredActiveReservations(now = now).forEach { reservation ->
                val application = reservation.application
                if (application.status in expirableStatuses) {
                    seatReservationRepository.save(reservation.copy(status = TuitionSeatReservationStatus.EXPIRED, updatedAt = now))
                    ledgerEntryRepository.findByApplicationId(application.id!!).forEach { entry ->
                        ledgerEntryRepository.save(entry.copy(status = TuitionLedgerStatus.CANCELLED, updatedAt = now))
                        billingChargeProvider.cancelCharge(BILLING_SOURCE_TYPE, requireNotNull(entry.id))
                    }
                    applicationRepository.save(application.copy(status = TuitionApplicationStatus.EXPIRED, updatedAt = now))
                    logger.info("Expired tuition application {} due to unpaid reservation", application.id)
                }
            }
        } catch (ex: DataAccessException) {
            logger.warn(
                "Skipping unpaid tuition reservation expiration because the tuition schema is not ready. " +
                    "Apply scripts/migrations/V13__create_tuition_module.sql before using tuition workflows.",
                ex
            )
        }
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

    private fun evaluateProgression(request: CreateTuitionApplicationRequest, requestedLevel: TuitionLevel): ProgressionEvaluation {
        if (request.applicationType != TuitionApplicationType.REGULAR_PROMOTION || request.studentId == null) {
            return ProgressionEvaluation()
        }

        val latestCompleted = courseEnrollmentCommandProvider.getLatestCompletedEnrollment(request.studentId)
            ?: throw ValidationException("Student must pass the previous level before requesting promotion", field = "studentId")

        val currentLevel = studentProfileProvider.getStudentProfile(request.studentId)?.currentLevel
        val fromLevel = currentLevel?.let { levelRepository.findByCode(it).orElse(null) }
            ?: levelRepository.findAll()
                .filter { it.active && resolveCourseLevel(it) == latestCompleted.courseLevel }
                .minByOrNull { it.levelOrder }
            ?: throw ValidationException(
                "Completed course level ${latestCompleted.courseLevel} is not mapped to tuition levels",
                field = "studentId"
            )

        val progression = progressionRepository.findByFromLevelIdAndActiveTrue(fromLevel.id!!).orElse(null)
            ?: throw ValidationException("No active tuition progression exists from level ${fromLevel.code}", field = "requestedLevelId")

        if (progression.toLevel.id != requestedLevel.id) {
            throw ValidationException(
                "Requested level ${requestedLevel.code} does not match allowed destination ${progression.toLevel.code}",
                field = "requestedLevelId"
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

    private fun resolveFeePlan(feePlanId: Long?, academicYear: TuitionAcademicYear, requestedLevel: TuitionLevel): TuitionFeePlan {
        val today = LocalDate.now()
        if (feePlanId != null) {
            val plan = feePlanRepository.findById(feePlanId)
                .orElseThrow { ResourceNotFoundException("Tuition fee plan not found with id: $feePlanId") }
            validateFeePlan(plan, academicYear, requestedLevel, today)
            return plan
        }

        val candidates = feePlanRepository.findActiveCandidates(academicYear.id!!, TuitionFeePlanStatus.ACTIVE, today)
            .filter { plan ->
                (plan.level == null || plan.level.id == requestedLevel.id) &&
                    (plan.segment == null || plan.segment == requestedLevel.segment)
            }

        return candidates
            .sortedWith(
                compareByDescending<TuitionFeePlan> { it.level?.id == requestedLevel.id }
                    .thenByDescending { it.segment == requestedLevel.segment }
                    .thenByDescending { it.validFrom }
            )
            .firstOrNull()
            ?: throw ResourceNotFoundException("No active tuition fee plan matches academic year ${academicYear.id} and level ${requestedLevel.code}")
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

    private fun ensureCourseHasAvailableSeat(courseId: Long) {
        val availability = courseEnrollmentCommandProvider.getCourseSeatAvailability(courseId)
        if (!availability.enrollmentOpen) {
            throw BusinessException("Course is not open for tuition enrollment")
        }

        val reservedSeats = seatReservationRepository.countActiveReservedSeats(courseId)
        val availableAfterReservations = availability.availableSeats - reservedSeats.toInt()
        if (availableAfterReservations <= 0) {
            throw ResourceConflictException("Course has no available seats after active tuition reservations")
        }
    }

    private fun ensureEnrollmentLedger(application: TuitionApplication): TuitionLedgerEntry {
        val existing = ledgerEntryRepository.findByApplicationIdAndConcept(application.id!!, TuitionLedgerConcept.TUITION_ENROLLMENT)
            .firstOrNull()
        if (existing != null) {
            syncBillingCharge(application, existing)
            return existing
        }

        val appliedDiscount = calculateDiscount(
            grossAmount = application.feePlan.enrollmentFee,
            studentId = application.studentId,
            requestedLevel = application.requestedLevel
        )
        val now = LocalDateTime.now()
        val saved = ledgerEntryRepository.save(
            TuitionLedgerEntry(
                application = application,
                studentId = application.studentId,
                discount = appliedDiscount.discount,
                concept = TuitionLedgerConcept.TUITION_ENROLLMENT,
                grossAmount = application.feePlan.enrollmentFee,
                discountAmount = appliedDiscount.amount,
                netAmount = (application.feePlan.enrollmentFee - appliedDiscount.amount).normalizeMoney(),
                dueDate = LocalDate.now().plusDays(3),
                status = TuitionLedgerStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
        syncBillingCharge(application, saved)
        return saved
    }

    private fun ensureMonthlyLedgers(application: TuitionApplication, studentId: Long) {
        val existingMonthly = ledgerEntryRepository.findByApplicationIdAndConcept(application.id!!, TuitionLedgerConcept.MONTHLY_FEE)
        if (existingMonthly.isNotEmpty()) {
            existingMonthly.forEach { syncBillingCharge(application, it, studentId) }
            return
        }

        val now = LocalDateTime.now()
        val academicYear = application.academicYear.startDate.year
        val installments = application.feePlan.installments.coerceAtMost(12)
        val entries = (0 until installments).map { index ->
            val appliedDiscount = calculateDiscount(
                grossAmount = application.feePlan.monthlyFee,
                studentId = studentId,
                requestedLevel = application.requestedLevel
            )
            TuitionLedgerEntry(
                application = application,
                studentId = studentId,
                discount = appliedDiscount.discount,
                concept = TuitionLedgerConcept.MONTHLY_FEE,
                grossAmount = application.feePlan.monthlyFee,
                discountAmount = appliedDiscount.amount,
                netAmount = (application.feePlan.monthlyFee - appliedDiscount.amount).normalizeMoney(),
                dueDate = LocalDate.of(academicYear, index + 1, 20),
                status = TuitionLedgerStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        }
        ledgerEntryRepository.saveAll(entries).forEach { syncBillingCharge(application, it, studentId) }
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
                "Matricula ${application.academicYear.name} - $studentName"
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
                currency = application.feePlan.currency,
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
                receiverDocumentNumber = guardian.documentNumber
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

    private fun resolveOrCreateStudent(application: TuitionApplication): Long {
        application.studentId?.let { return it }
        return studentProfileProvider.createStudentForTuition(
            guardianUserId = application.guardianUserId,
            request = StudentProfileCreateRequest(
                firstName = requireNotNull(application.studentFirstName) { "studentFirstName is required" },
                lastName = requireNotNull(application.studentLastName) { "studentLastName is required" },
                email = requireNotNull(application.studentEmail) { "studentEmail is required" },
                documentNumber = requireNotNull(application.studentDocumentNumber) { "studentDocumentNumber is required" },
                dateOfBirth = requireNotNull(application.studentDateOfBirth) { "studentDateOfBirth is required" },
                address = requireNotNull(application.studentAddress) { "studentAddress is required" },
                phoneNumber = requireNotNull(application.studentPhoneNumber) { "studentPhoneNumber is required" },
                emergencyContact = requireNotNull(application.studentEmergencyContact) { "studentEmergencyContact is required" },
                medicalNotes = application.studentMedicalNotes,
                currentLevel = application.requestedLevel.code
            )
        ).id
    }

    private fun TuitionApplication.toDto(): TuitionApplicationDto {
        val reservation = id?.let { seatReservationRepository.findByApplicationId(it).orElse(null) }
        val ledgerEntries = id?.let { ledgerEntryRepository.findByApplicationId(it) }.orEmpty()
        val displayLedgerEntries = normalizeLegacyMonthlyDates(this, ledgerEntries)
        return TuitionApplicationDto(
            id = id!!,
            guardianUserId = guardianUserId,
            studentId = studentId,
            studentFirstName = studentFirstName,
            studentLastName = studentLastName,
            studentEmail = studentEmail,
            studentDocumentNumber = studentDocumentNumber,
            academicYearId = academicYear.id!!,
            academicYearName = academicYear.name,
            requestedLevelId = requestedLevel.id!!,
            requestedLevelCode = requestedLevel.code,
            requestedLevelName = requestedLevel.name,
            requestedCourseId = requestedCourseId,
            applicationType = applicationType,
            status = status,
            feePlan = feePlan.toDto(),
            enrollmentId = enrollmentId,
            warningMessage = warningMessage,
            progressionRule = progressionRule,
            requiresAdminOverride = requiresAdminOverride,
            adminNotes = adminNotes,
            seatReservation = reservation?.toDto(),
            ledgerEntries = displayLedgerEntries.map { it.toDto() },
            submittedAt = submittedAt,
            approvedAt = approvedAt,
            approvedBy = approvedBy,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Legacy approvals were generated from the current month and could cross
     * into the following year. Keep persisted rows untouched, but expose the
     * academic-year calendar consistently in read DTOs.
     */
    private fun normalizeLegacyMonthlyDates(
        application: TuitionApplication,
        entries: List<TuitionLedgerEntry>
    ): List<TuitionLedgerEntry> {
        val monthly = entries
            .filter { it.concept == TuitionLedgerConcept.MONTHLY_FEE }
            .sortedWith(compareBy<TuitionLedgerEntry> { it.dueDate }.thenBy { it.id })
            .take(12)
        if (monthly.isEmpty()) {
            return entries
        }

        val year = application.academicYear.startDate.year
        val normalized = monthly.mapIndexed { index, entry ->
            entry.id to entry.copy(dueDate = LocalDate.of(year, index + 1, 20))
        }.toMap()
        return entries.map { normalized[it.id] ?: it }
    }

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

    private fun TuitionSeatReservation.toDto() = TuitionSeatReservationDto(
        id = id!!,
        applicationId = application.id!!,
        courseId = courseId,
        quantity = quantity,
        expiresAt = expiresAt,
        status = status,
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
    }
}
