package com.sigep.students.application.service

import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.UnprocessableEntityException
import com.sigep.common.application.service.StudentProfileCreateRequest
import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.StudentProfileResolution
import com.sigep.common.application.service.StudentProfileResolutionType
import com.sigep.students.domain.model.Student
import com.sigep.students.domain.model.StudentDocumentType
import com.sigep.students.domain.model.StudentGuardianLinkAction
import com.sigep.students.domain.model.StudentGuardianLinkEvent
import com.sigep.students.domain.model.StudentGuardianLinkOrigin
import com.sigep.students.domain.repository.StudentGuardianLinkEventRepository
import com.sigep.students.domain.repository.StudentRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class StudentProfileProviderImpl(
    private val studentRepository: StudentRepository,
    private val guardianLinkEventRepository: StudentGuardianLinkEventRepository,
    private val identityNormalizer: StudentIdentityNormalizer
) : StudentProfileProvider {

    override fun getStudentProfile(studentId: Long): StudentProfileInfo? =
        studentRepository.findById(studentId).map { it.toInfo() }.orElse(null)

    override fun getStudentProfiles(studentIds: Collection<Long>): Map<Long, StudentProfileInfo> {
        if (studentIds.isEmpty()) return emptyMap()

        return studentRepository.findAllById(studentIds.distinct())
            .associate { student -> student.id!! to student.toInfo() }
    }

    override fun validateGuardianOwnsStudent(guardianUserId: Long, studentId: Long): Boolean {
        val student = studentRepository.findById(studentId)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $studentId") }
        return student.guardianId == guardianUserId
    }

    override fun createStudentForTuition(
        guardianUserId: Long,
        request: StudentProfileCreateRequest
    ): StudentProfileInfo {
        return createNewStudent(guardianUserId, guardianUserId, request).toInfo()
    }

    override fun resolveStudentForTuition(
        guardianUserId: Long,
        actorUserId: Long,
        actorIsAdmin: Boolean,
        existingStudentId: Long?,
        request: StudentProfileCreateRequest?
    ): StudentProfileResolution {
        if (existingStudentId != null) {
            val existing = studentRepository.findById(existingStudentId)
                .orElseThrow { ResourceNotFoundException("Student not found with id: $existingStudentId") }
            return resolveExisting(existing, guardianUserId, actorUserId, actorIsAdmin)
        }

        val createRequest = request
            ?: throw ResourceConflictException("Student profile is required", "STUDENT_NOT_RESOLVED")
        val identity = identityNormalizer.normalize(
            StudentDocumentType.valueOf(createRequest.documentType),
            createRequest.documentCountry,
            createRequest.documentNumber
        )
        val matches = identity.normalizedNumber?.let { normalized ->
            studentRepository.findByDocumentTypeAndDocumentCountryAndNormalizedDocumentNumber(
                identity.type,
                identity.country,
                normalized
            ).map(::listOf).orElse(emptyList())
        } ?: studentRepository.findPotentialMatches(
            createRequest.firstName.trim(),
            createRequest.lastName.trim(),
            createRequest.dateOfBirth
        )

        if (matches.size > 1) {
            throw UnprocessableEntityException(
                message = "The student identity requires administrative verification",
                code = "STUDENT_MATCH_REQUIRES_VERIFICATION"
            )
        }
        if (matches.size == 1) {
            return resolveExisting(matches.single(), guardianUserId, actorUserId, actorIsAdmin)
        }

        val saved = createNewStudent(guardianUserId, actorUserId, createRequest, identity)
        return StudentProfileResolution(saved.toInfo(), StudentProfileResolutionType.CREATED)
    }

    private fun createNewStudent(
        guardianUserId: Long,
        actorUserId: Long,
        request: StudentProfileCreateRequest,
        normalizedIdentity: NormalizedStudentDocument? = null
    ): Student {
        val identity = normalizedIdentity ?: identityNormalizer.normalize(
            StudentDocumentType.valueOf(request.documentType),
            request.documentCountry,
            request.documentNumber
        )
        identity.normalizedNumber?.let { normalized ->
            if (studentRepository.existsByDocumentTypeAndDocumentCountryAndNormalizedDocumentNumber(identity.type, identity.country, normalized)) {
                throw ResourceConflictException(
                    message = "A student with the same normalized identity already exists",
                    code = "STUDENT_IDENTITY_CONFLICT",
                    field = "studentDocumentNumber"
                )
            }
        }

        val now = LocalDateTime.now()
        val student = Student(
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            email = request.email.trim().lowercase(),
            dateOfBirth = request.dateOfBirth,
            address = request.address,
            phoneNumber = request.phoneNumber,
            emergencyContact = request.emergencyContact,
            documentType = identity.type,
            documentCountry = identity.country,
            documentNumber = identity.displayNumber,
            normalizedDocumentNumber = identity.normalizedNumber,
            guardianId = guardianUserId,
            enrollmentDate = LocalDate.now(),
            medicalNotes = request.medicalNotes,
            active = true,
            currentLevel = request.currentLevel,
            createdAt = now,
            updatedAt = now
        )

        val saved = studentRepository.save(student)
        guardianLinkEventRepository.save(
            StudentGuardianLinkEvent(
                studentId = saved.id!!,
                guardianUserId = guardianUserId,
                action = StudentGuardianLinkAction.LINKED,
                origin = StudentGuardianLinkOrigin.TUITION,
                actorUserId = actorUserId,
                reason = "Guardian linked during tuition application"
            )
        )
        return saved
    }

    override fun updateCurrentLevel(studentId: Long, currentLevel: String): StudentProfileInfo {
        val student = studentRepository.findById(studentId)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $studentId") }
        return studentRepository.save(
            student.copy(currentLevel = currentLevel, updatedAt = LocalDateTime.now())
        ).toInfo()
    }

    private fun Student.toInfo() = StudentProfileInfo(
        id = id!!,
        guardianId = guardianId,
        firstName = firstName,
        lastName = lastName,
        email = email,
        documentType = documentType.name,
        documentCountry = documentCountry,
        documentNumber = documentNumber,
        dateOfBirth = dateOfBirth,
        address = address,
        phoneNumber = phoneNumber,
        emergencyContact = emergencyContact,
        currentLevel = currentLevel,
        active = active
    )

    private fun resolveExisting(
        student: Student,
        guardianUserId: Long,
        actorUserId: Long,
        actorIsAdmin: Boolean
    ): StudentProfileResolution {
        if (student.guardianId == guardianUserId) {
            return StudentProfileResolution(student.toInfo(), StudentProfileResolutionType.EXISTING)
        }
        if (!actorIsAdmin) {
            throw UnprocessableEntityException(
                message = "The student identity requires administrative verification",
                code = "STUDENT_MATCH_REQUIRES_VERIFICATION"
            )
        }
        if (student.guardianId != null) {
            throw ResourceConflictException(
                message = "Student is linked to another guardian",
                code = "STUDENT_LINKED_TO_OTHER_GUARDIAN",
                field = "studentId"
            )
        }

        val linked = studentRepository.save(student.copy(guardianId = guardianUserId, updatedAt = LocalDateTime.now()))
        guardianLinkEventRepository.save(
            StudentGuardianLinkEvent(
                studentId = linked.id!!,
                previousGuardianUserId = null,
                guardianUserId = guardianUserId,
                action = StudentGuardianLinkAction.LINKED,
                origin = StudentGuardianLinkOrigin.TUITION,
                actorUserId = actorUserId,
                reason = "Unassigned student linked during admin tuition application"
            )
        )
        return StudentProfileResolution(linked.toInfo(), StudentProfileResolutionType.EXISTING)
    }
}
