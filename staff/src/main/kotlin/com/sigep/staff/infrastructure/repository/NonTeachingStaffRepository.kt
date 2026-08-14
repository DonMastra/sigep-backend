package com.sigep.staff.infrastructure.repository

import com.sigep.staff.domain.model.NonTeachingStaff
import com.sigep.staff.domain.model.NonTeachingRole
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface NonTeachingStaffRepository : JpaRepository<NonTeachingStaff, Long> {

    fun findByIsActiveTrue(pageable: Pageable): Page<NonTeachingStaff>

    fun findByEmail(email: String): NonTeachingStaff?

    fun findByDocumentNumber(documentNumber: String): NonTeachingStaff?

    fun findByRoleAndIsActiveTrue(role: NonTeachingRole, pageable: Pageable): Page<NonTeachingStaff>

    fun findByCompanyNameAndIsActiveTrue(companyName: String, pageable: Pageable): Page<NonTeachingStaff>

    @Query("""
        SELECT n FROM NonTeachingStaff n 
        WHERE n.isActive = true 
        AND (LOWER(n.firstName) LIKE LOWER(CONCAT('%', :query, '%')) 
        OR LOWER(n.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(n.email) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(n.documentNumber) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(n.companyName) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    fun searchByQuery(@Param("query") query: String, pageable: Pageable): Page<NonTeachingStaff>

    @Query("""
        SELECT COUNT(n) FROM NonTeachingStaff n 
        WHERE n.isActive = true
    """)
    fun countActiveStaff(): Long
}

