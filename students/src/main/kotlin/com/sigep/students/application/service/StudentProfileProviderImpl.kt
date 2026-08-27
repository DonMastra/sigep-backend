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
import com.sigep.students.domain.model.StudentGuardianRelationship
import com.sigep.students.domain.repository.StudentGuardianLinkEventRepository
import com.sigep.students.domain.repository.StudentGuardianRelationshipRepository
import com.sigep.students.domain.repository.StudentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class StudentProfileProviderImpl(
    private val studentRepository: StudentRepository,
    private val guardianLinkEventRepository: StudentGuardianLinkEventRepository,
    private val guardianRelationshipRepository: StudentGuardianRelationshipRepository,
    private val identityNormalizer: StudentIdentityNormalizer
) : StudentProfileProvider {

    override fun getStudentProfile(studentId: Long): StudentProfileInfo? =
        studentRepository.findById(studentId).map { it.toInfo() }.orElse(null)

    override fun getStudentProfiles(studentIds: Collection<Long>): Map<Long, StudentProfileInfo> {
        if (studentIds.isEmpty()) return emptyMap()

        val students = studentRepository.findAllById(studentIds.distinct())
        if (students.isEmpty()) return emptyMap()
        val relationshipIdsByStudent = guardianRelationshipRepository
            .findByStudentIdInAndActiveTrue(students.mapNotNull { it.id })
            .filter { it.canViewAcademic }
            .groupBy({ it.studentId }, { it.guardianUserId })
        return students.associate { student ->
            val guardianIds = relationshipIdsByStudent[student.id].orEmpty().toMutableSet()
            student.guardianId?.let(guardianIds::add)
            student.id!! to student.toInfo(guardianIds)
        }
    }

    override fun validateGuardianOwnsStudent(guardianUserId: Long, studentId: Long): Boolean {
        val student = studentRepository.findById(studentId)
            .orElseThrow { ResourceNotFoundException("Student not found with id: $studentId") }
        return student.guardianId == guardianUserId ||
            guardianRelationshipRepository.existsByStudentIdAndGuardianUserIdAndActiveTrueAndCanViewAcademicTrue(
                studentId,
                guardianUserId
            )
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
        ensureGuardianRelationship(
            saved,
            guardianUserId,
            actorUserId,
            primary = true,
            reason = "Guardian linked during tuition application"
        )
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

    private fun Student.toInfo(guardianIds: Set<Long> = activeGuardianIds(this)) = StudentProfileInfo(
        id = id!!,
        guardianId = guardianId,
        guardianIds = guardianIds,
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
        if (validateGuardianOwnsStudent(guardianUserId, student.id!!)) {
            return StudentProfileResolution(student.toInfo(), StudentProfileResolutionType.EXISTING)
        }
        if (!actorIsAdmin) {
            throw UnprocessableEntityException(
                message = "The student identity requires administrative verification",
                code = "STUDENT_MATCH_REQUIRES_VERIFICATION"
            )
        }
        val hasActiveGuardians = activeGuardianIds(student).isNotEmpty()
        val becomesPrimary = student.guardianId == null && !hasActiveGuardians
        val linked = if (becomesPrimary) {
            studentRepository.save(student.copy(guardianId = guardianUserId, updatedAt = LocalDateTime.now()))
        } else {
            student
        }
        ensureGuardianRelationship(
            linked,
            guardianUserId,
            actorUserId,
            primary = becomesPrimary || linked.guardianId == guardianUserId,
            reason = "Guardian linked during admin tuition application"
        )
        guardianLinkEventRepository.save(
            StudentGuardianLinkEvent(
                studentId = linked.id!!,
                previousGuardianUserId = null,
                guardianUserId = guardianUserId,
                action = StudentGuardianLinkAction.LINKED,
                origin = StudentGuardianLinkOrigin.TUITION,
                actorUserId = actorUserId,
                reason = "Guardian linked during admin tuition application"
            )
        )
        return StudentProfileResolution(linked.toInfo(), StudentProfileResolutionType.EXISTING)
    }

    private fun activeGuardianIds(student: Student): Set<Long> {
        val relationshipIds = guardianRelationshipRepository
            .findByStudentIdAndActiveTrueOrderByPrimaryDescIdAsc(student.id!!)
            .filter { it.canViewAcademic }
            .mapTo(linkedSetOf()) { it.guardianUserId }
        student.guardianId?.let(relationshipIds::add)
        return relationshipIds
    }

    private fun ensureGuardianRelationship(
        student: Student,
        guardianUserId: Long,
        actorUserId: Long,
        primary: Boolean,
        reason: String
    ) {
        val now = LocalDateTime.now()
        val existing = guardianRelationshipRepository.findByStudentIdAndGuardianUserId(student.id!!, guardianUserId)
        guardianRelationshipRepository.save(
            existing?.copy(
                primary = primary,
                canViewAcademic = true,
                active = true,
                verifiedBy = actorUserId,
                verifiedAt = now,
                updatedAt = now
            ) ?: StudentGuardianRelationship(
                studentId = student.id,
                guardianUserId = guardianUserId,
                primary = primary,
                canViewAcademic = true,
                active = true,
                sourceSystem = "APPLICATION",
                sourceReference = reason,
                verifiedBy = actorUserId,
                verifiedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
