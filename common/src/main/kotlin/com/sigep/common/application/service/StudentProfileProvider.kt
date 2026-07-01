package com.sigep.common.application.service

import java.time.LocalDate

interface StudentProfileProvider {
    fun getStudentProfile(studentId: Long): StudentProfileInfo?
    fun validateGuardianOwnsStudent(guardianUserId: Long, studentId: Long): Boolean
    fun createStudentForTuition(guardianUserId: Long, request: StudentProfileCreateRequest): StudentProfileInfo
}

data class StudentProfileInfo(
    val id: Long,
    val guardianId: Long?,
    val firstName: String,
    val lastName: String,
    val email: String,
    val documentNumber: String,
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
    val documentNumber: String,
    val dateOfBirth: LocalDate,
    val address: String,
    val phoneNumber: String,
    val emergencyContact: String,
    val medicalNotes: String?,
    val currentLevel: String
)
