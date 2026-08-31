package com.sigep.security.domain.repository

import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.model.UserRoleAssignment
import com.sigep.security.domain.model.UserRoleContextEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRoleAssignmentRepository : JpaRepository<UserRoleAssignment, Long> {
    fun existsByUserId(userId: Long): Boolean
    fun findByUserIdAndRole(userId: Long, role: UserRole): Optional<UserRoleAssignment>
    fun findAllByUserIdAndRevokedAtIsNullOrderByRoleAsc(userId: Long): List<UserRoleAssignment>
    fun existsByUserIdAndRoleAndRevokedAtIsNull(userId: Long, role: UserRole): Boolean
    fun countByUserIdAndRevokedAtIsNull(userId: Long): Long

    @Query("select assignment.userId from UserRoleAssignment assignment where assignment.role = :role and assignment.revokedAt is null")
    fun findActiveUserIdsByRole(@Param("role") role: UserRole): List<Long>

    @Query("select distinct assignment.userId from UserRoleAssignment assignment")
    fun findAssignedUserIds(): List<Long>
}

@Repository
interface UserRoleContextEventRepository : JpaRepository<UserRoleContextEvent, Long>
