package com.sigep.common.application.service

import java.time.LocalDate

interface StudentProfileProvider {
    fun getStudentProfile(studentId: Long): StudentProfileInfo?
    fun getStudentProfiles(studentIds: Collection<Long>): Map<Long, StudentProfileInfo> =
        studentIds.distinct().mapNotNull { id -> getStudentProfile(id)?.let { id to it } }.toMap()
    fun validateGuardianOwnsStudent(guardianUserId: Long, studentId: Long): Boolean
    fun createStudentForTuition(guardianUserId: Long, request: StudentProfileCreateRequest): StudentProfileInfo
    fun resolveStudentForTuition(
        guardianUserId: Long,
        actorUserId: Long,
        actorIsAdmin: Boolean,
        existingStudentId: Long?,
        request: StudentProfileCreateRequest?
    ): StudentProfileResolution
    fun updateCurrentLevel(studentId: Long, currentLevel: String): StudentProfileInfo
}

data class StudentProfileInfo(
    val id: Long,
    val guardianId: Long?,
    val guardianIds: Set<Long> = guardianId?.let(::setOf) ?: emptySet(),
    val firstName: String,
    val lastName: String,
    val email: String,
    val documentType: String,
    val documentCountry: String,
    val documentNumber: String?,
    val dateOfBirth: LocalDate,
    val address: String,
    val phoneNumber: String,
    val emergencyContact: String,
    val currentLevel: String,
    val active: Boolean
)

data class StudentProfileCreateRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val documentType: String,
    val documentCountry: String,
    val documentNumber: String?,
    val dateOfBirth: LocalDate,
    val address: String,
    val phoneNumber: String,
    val emergencyContact: String,
    val medicalNotes: String?,
    val currentLevel: String
)

data class StudentProfileResolution(
    val profile: StudentProfileInfo,
    val type: StudentProfileResolutionType
)

enum class StudentProfileResolutionType { EXISTING, CREATED }
