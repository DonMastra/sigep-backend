package com.sigep.security.domain.repository

import com.sigep.security.domain.model.GuardianInvitation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface GuardianInvitationRepository : JpaRepository<GuardianInvitation, String> {
    fun findByTokenHash(tokenHash: String): Optional<GuardianInvitation>
}
