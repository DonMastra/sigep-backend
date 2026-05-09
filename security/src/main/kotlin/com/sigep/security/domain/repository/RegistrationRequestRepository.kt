package com.sigep.security.domain.repository

import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.RegistrationRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RegistrationRequestRepository : JpaRepository<RegistrationRequest, String> {
    fun findByUserId(userId: Long): Optional<RegistrationRequest>
    fun findByStatus(status: AccountStatus, pageable: Pageable): Page<RegistrationRequest>
}

