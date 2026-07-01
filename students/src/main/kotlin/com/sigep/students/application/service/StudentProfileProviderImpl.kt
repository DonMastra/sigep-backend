package com.sigep.students.application.service

import com.sigep.common.application.exception.DuplicateResourceException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.service.StudentProfileCreateRequest
import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.repository.StudentRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class StudentProfileProviderImpl(
    private val studentRepository: StudentRepository
) : StudentProfileProvider {

    override fun getStudentProfile(studentId: Long): StudentProfileInfo? =
        studentRepository.findById(studentId).map { it.toInfo() }.orElse(null)

    override fun validateGuardianOwnsStudent(guardianUserId: Long, studentId: Long): Boolean {
        val student = studentRepository.findById(studentId)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $studentId") }
        return student.guardianId == guardianUserId
    }

    override fun createStudentForTuition(
        guardianUserId: Long,
        request: StudentProfileCreateRequest
    ): StudentProfileInfo {
        if (studentRepository.existsByEmail(request.email)) {
            throw DuplicateResourceException("Student with email ${request.email} already exists")
        }
        if (studentRepository.existsByDocumentNumber(request.documentNumber)) {
            throw DuplicateResourceException("Student with document number ${request.documentNumber} already exists")
        }

        val now = LocalDateTime.now()
        val student = Student(
            firstName = request.firstName,
            lastName = request.lastName,
            email = request.email,
            dateOfBirth = request.dateOfBirth,
            address = request.address,
            phoneNumber = request.phoneNumber,
            emergencyContact = request.emergencyContact,
            documentNumber = request.documentNumber,
            guardianId = guardianUserId,
            enrollmentDate = LocalDate.now(),
            medicalNotes = request.medicalNotes,
            active = true,
            currentLevel = request.currentLevel,
            createdAt = now,
            updatedAt = now
        )

        return studentRepository.save(student).toInfo()
    }

    private fun Student.toInfo() = StudentProfileInfo(
        id = id!!,
        guardianId = guardianId,
        firstName = firstName,
        lastName = lastName,
        email = email,
        documentNumber = documentNumber,
        dateOfBirth = dateOfBirth,
        address = address,
        phoneNumber = phoneNumber,
        emergencyContact = emergencyContact,
        currentLevel = currentLevel,
        active = active
    )
}
