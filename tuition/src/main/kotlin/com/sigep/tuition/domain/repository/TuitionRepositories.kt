package com.sigep.tuition.domain.repository

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
import com.sigep.tuition.domain.model.TuitionPlacementAssessment
import com.sigep.tuition.domain.model.TuitionLedgerConcept
import com.sigep.tuition.domain.model.TuitionLedgerEntry
import com.sigep.tuition.domain.model.TuitionLedgerStatus
import com.sigep.tuition.domain.model.TuitionLevel
import com.sigep.tuition.domain.model.TuitionLevelProgression
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface TuitionAcademicYearRepository : JpaRepository<TuitionAcademicYear, Long> {
    fun findByStatus(status: TuitionAcademicYearStatus, pageable: Pageable): Page<TuitionAcademicYear>
    fun existsByName(name: String): Boolean
}

@Repository
interface TuitionLevelRepository : JpaRepository<TuitionLevel, Long> {
    fun findByCode(code: String): Optional<TuitionLevel>
    fun existsByCode(code: String): Boolean
    fun findByActiveTrue(pageable: Pageable): Page<TuitionLevel>
}

@Repository
interface TuitionLevelProgressionRepository : JpaRepository<TuitionLevelProgression, Long> {
    fun findByFromLevelIdAndActiveTrue(fromLevelId: Long): Optional<TuitionLevelProgression>
    fun existsByFromLevelIdAndActiveTrue(fromLevelId: Long): Boolean
}

@Repository
interface TuitionFeePlanRepository : JpaRepository<TuitionFeePlan, Long> {
    @Query(
        """
        SELECT p FROM TuitionFeePlan p
        WHERE p.academicYear.id = :academicYearId
        AND p.status = :status
        AND p.validFrom <= :date
        AND (p.validTo IS NULL OR p.validTo >= :date)
        """
    )
    fun findActiveCandidates(
        @Param("academicYearId") academicYearId: Long,
        @Param("status") status: TuitionFeePlanStatus = TuitionFeePlanStatus.ACTIVE,
        @Param("date") date: LocalDate = LocalDate.now()
    ): List<TuitionFeePlan>
}

@Repository
interface TuitionEnrollmentFeePolicyRepository : JpaRepository<TuitionEnrollmentFeePolicy, Long> {
    @Query(
        """
        SELECT p FROM TuitionEnrollmentFeePolicy p
        WHERE p.status = :status
        AND p.validFrom <= :date
        AND (p.validTo IS NULL OR p.validTo >= :date)
        ORDER BY p.defaultPolicy DESC, p.validFrom DESC, p.id DESC
        """
    )
    fun findActiveCandidates(
        @Param("status") status: TuitionEnrollmentFeePolicyStatus = TuitionEnrollmentFeePolicyStatus.ACTIVE,
        @Param("date") date: LocalDate = LocalDate.now()
    ): List<TuitionEnrollmentFeePolicy>

    fun findByDefaultPolicyTrue(): List<TuitionEnrollmentFeePolicy>
}

@Repository
interface TuitionDiscountRepository : JpaRepository<TuitionDiscount, Long> {
    @Query(
        """
        SELECT d FROM TuitionDiscount d
        WHERE d.active = true
        AND d.validFrom <= :date
        AND (d.validTo IS NULL OR d.validTo >= :date)
        AND (
            (:studentId IS NULL AND d.studentId IS NULL)
            OR (:studentId IS NOT NULL AND (d.studentId IS NULL OR d.studentId = :studentId))
        )
        """
    )
    fun findActiveCandidates(
        @Param("studentId") studentId: Long?,
        @Param("date") date: LocalDate = LocalDate.now()
    ): List<TuitionDiscount>
}

@Repository
interface TuitionApplicationRepository : JpaRepository<TuitionApplication, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<TuitionApplication>
    fun findFirstByGuardianUserIdAndStudentIdAndApplicationTypeAndStatusIn(
        guardianUserId: Long,
        studentId: Long,
        applicationType: TuitionApplicationType,
        statuses: Collection<TuitionApplicationStatus>
    ): Optional<TuitionApplication>
    fun findByGuardianUserId(guardianUserId: Long, pageable: Pageable): Page<TuitionApplication>
    fun findByStatus(status: TuitionApplicationStatus, pageable: Pageable): Page<TuitionApplication>

    @Query(
        """
        SELECT a FROM TuitionApplication a
        WHERE (:status IS NULL OR a.status = :status)
        AND (:academicYearId IS NULL OR a.academicYear.id = :academicYearId)
        """
    )
    fun findByFilters(
        @Param("status") status: TuitionApplicationStatus?,
        @Param("academicYearId") academicYearId: Long?,
        pageable: Pageable
    ): Page<TuitionApplication>
}

@Repository
interface TuitionPlacementAssessmentRepository : JpaRepository<TuitionPlacementAssessment, Long> {
    fun findByApplicationId(applicationId: Long): Optional<TuitionPlacementAssessment>
}

@Repository
interface TuitionLedgerEntryRepository : JpaRepository<TuitionLedgerEntry, Long> {
    fun findByApplicationId(applicationId: Long): List<TuitionLedgerEntry>
    fun findByApplicationIdAndConcept(applicationId: Long, concept: TuitionLedgerConcept): List<TuitionLedgerEntry>
    fun existsByApplicationIdAndConceptAndStatus(
        applicationId: Long,
        concept: TuitionLedgerConcept,
        status: TuitionLedgerStatus
    ): Boolean
}
