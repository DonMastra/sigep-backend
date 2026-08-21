package com.sigep.guardians.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(name = "guardian_client_profiles")
data class GuardianClientProfile(
    @Id
    @Column(name = "guardian_user_id", nullable = false)
    val guardianUserId: Long,

    @Column(name = "client_number", nullable = false, unique = true, length = 32)
    val clientNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_channel", nullable = false, length = 20)
    val preferredContactChannel: GuardianContactChannel = GuardianContactChannel.EMAIL,

    @Column(name = "administrative_notes", length = 1000)
    val administrativeNotes: String? = null,

    @Column(name = "updated_by")
    val updatedBy: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    @Column(nullable = false)
    val version: Long = 0
)

enum class GuardianContactChannel { EMAIL, PHONE, WHATSAPP }
