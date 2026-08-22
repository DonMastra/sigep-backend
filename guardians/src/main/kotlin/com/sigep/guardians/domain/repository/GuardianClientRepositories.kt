package com.sigep.guardians.domain.repository

import com.sigep.guardians.domain.model.GuardianClientChargeReadModel
import com.sigep.guardians.domain.model.GuardianClientDetailReadModel
import com.sigep.guardians.domain.model.GuardianClientPaymentReadModel
import com.sigep.guardians.domain.model.GuardianClientProfile
import com.sigep.guardians.domain.model.GuardianClientSearchCriteria
import com.sigep.guardians.domain.model.GuardianClientStatsReadModel
import com.sigep.guardians.domain.model.GuardianClientStudentReadModel
import com.sigep.guardians.domain.model.GuardianClientSummaryReadModel
import com.sigep.guardians.domain.model.GuardianClientTuitionReadModel
import org.springframework.data.domain.Page
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GuardianClientProfileRepository : JpaRepository<GuardianClientProfile, Long> {
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO guardian_client_profiles (
                guardian_user_id, client_number, preferred_contact_channel,
                updated_by, created_at, updated_at, version
            ) VALUES (
                :guardianUserId, :clientNumber, 'EMAIL',
                :updatedBy, NOW(), NOW(), 0
            )
            ON CONFLICT (guardian_user_id) DO NOTHING
        """
    )
    fun insertIfMissing(
        @Param("guardianUserId") guardianUserId: Long,
        @Param("clientNumber") clientNumber: String,
        @Param("updatedBy") updatedBy: Long?
    ): Int
}

interface GuardianClientReadRepository {
    fun search(criteria: GuardianClientSearchCriteria): Page<GuardianClientSummaryReadModel>
    fun getStats(): GuardianClientStatsReadModel
    fun findDetail(guardianUserId: Long): GuardianClientDetailReadModel?
    fun findStudents(guardianUserId: Long): List<GuardianClientStudentReadModel>
    fun findTuitionApplications(guardianUserId: Long): List<GuardianClientTuitionReadModel>
    fun findCharges(guardianUserId: Long): List<GuardianClientChargeReadModel>
    fun findPayments(guardianUserId: Long): List<GuardianClientPaymentReadModel>
    fun existsGuardian(guardianUserId: Long): Boolean
}
