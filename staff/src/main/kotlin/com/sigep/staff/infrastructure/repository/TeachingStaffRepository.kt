package com.sigep.staff.infrastructure.repository

import com.sigep.staff.domain.model.TeachingStaff
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TeachingStaffRepository : JpaRepository<TeachingStaff, Long> {

    fun findByIsActiveTrue(pageable: Pageable): Page<TeachingStaff>

    fun findAllByIsActiveTrueOrderByLastNameAscFirstNameAsc(): List<TeachingStaff>

    fun findByEmail(email: String): TeachingStaff?

    fun findByDocumentNumber(documentNumber: String): TeachingStaff?

    fun findByIdAndIsActiveTrue(id: Long): TeachingStaff?

    fun findAllByIdInAndIsActiveTrue(ids: Collection<Long>): List<TeachingStaff>

    fun findByLinkedUserId(linkedUserId: Long): TeachingStaff?

    fun findAllByLinkedUserIdInAndIsActiveTrue(ids: Collection<Long>): List<TeachingStaff>

    @Query("""
        SELECT t FROM TeachingStaff t 
        WHERE t.isActive = true 
        AND (LOWER(t.firstName) LIKE LOWER(CONCAT('%', :query, '%')) 
        OR LOWER(t.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(t.email) LIKE LOWER(CONCAT('%', :query, '%'))
        OR LOWER(t.documentNumber) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    fun searchByQuery(@Param("query") query: String, pageable: Pageable): Page<TeachingStaff>

    @Query("""
        SELECT COUNT(t) FROM TeachingStaff t 
        WHERE t.isActive = true
    """)
    fun countActiveStaff(): Long
}

