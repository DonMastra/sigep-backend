package com.sigep.guardians.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.service.GuardianClientProfileProvisioner
import com.sigep.guardians.application.dto.GuardianClientChargeDto
import com.sigep.guardians.application.dto.GuardianClientDetailDto
import com.sigep.guardians.application.dto.GuardianClientPaymentDto
import com.sigep.guardians.application.dto.GuardianClientStatsDto
import com.sigep.guardians.application.dto.GuardianClientStudentDto
import com.sigep.guardians.application.dto.GuardianClientSummaryDto
import com.sigep.guardians.application.dto.GuardianClientTuitionDto
import com.sigep.guardians.application.dto.UpdateGuardianClientProfileRequest
import com.sigep.guardians.domain.model.GuardianClientSearchCriteria
import com.sigep.guardians.domain.repository.GuardianClientProfileRepository
import com.sigep.guardians.domain.repository.GuardianClientReadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class GuardianClientService(
    private val profileRepository: GuardianClientProfileRepository,
    private val readRepository: GuardianClientReadRepository
) : GuardianClientProfileProvisioner {

    @Transactional(readOnly = true)
    fun list(criteria: GuardianClientSearchCriteria): PageResponse<GuardianClientSummaryDto> {
        val page = readRepository.search(criteria)
        return PageResponse(
            content = page.content.map { it.toDto() },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getStats(): GuardianClientStatsDto = readRepository.getStats().let {
        GuardianClientStatsDto(
            totalClients = it.totalClients,
            withStudents = it.withStudents,
            withoutStudents = it.withoutStudents,
            withBillingAccount = it.withBillingAccount,
            withOpenDebt = it.withOpenDebt,
            missingContactData = it.missingContactData
        )
    }

    @Transactional(readOnly = true)
    fun getDetail(guardianUserId: Long): GuardianClientDetailDto {
        val detail = readRepository.findDetail(guardianUserId)
            ?: throw ResourceNotFoundException("Guardian client not found with id: $guardianUserId")
        return GuardianClientDetailDto(
            summary = detail.summary.toDto(),
            address = detail.address,
            dateOfBirth = detail.dateOfBirth,
            emergencyContact = detail.emergencyContact,
            administrativeNotes = detail.administrativeNotes,
            profileUpdatedAt = detail.updatedAt,
            students = readRepository.findStudents(guardianUserId).map {
                GuardianClientStudentDto(
                    studentId = it.studentId,
                    studentNumber = it.studentNumber,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    active = it.active,
                    currentLevel = it.currentLevel,
                    enrollmentId = it.enrollmentId,
                    courseId = it.courseId,
                    courseName = it.courseName,
                    enrollmentStatus = it.enrollmentStatus,
                    tuitionApplicationId = it.tuitionApplicationId,
                    tuitionApplicationStatus = it.tuitionApplicationStatus,
                    openChargeCount = it.openChargeCount,
                    outstandingAmount = it.outstandingAmount
                )
            },
            tuitionApplications = readRepository.findTuitionApplications(guardianUserId).map {
                GuardianClientTuitionDto(
                    applicationId = it.applicationId,
                    studentId = it.studentId,
                    studentName = it.studentName,
                    applicationType = it.applicationType,
                    status = it.status,
                    origin = it.origin,
                    submittedAt = it.submittedAt,
                    enrollmentId = it.enrollmentId,
                    assignedCourseId = it.assignedCourseId,
                    assignedCourseName = it.assignedCourseName
                )
            },
            charges = readRepository.findCharges(guardianUserId).map {
                GuardianClientChargeDto(
                    chargeId = it.chargeId,
                    studentId = it.studentId,
                    studentName = it.studentName,
                    concept = it.concept,
                    description = it.description,
                    amount = it.amount,
                    paidAmount = it.paidAmount,
                    outstandingAmount = it.outstandingAmount,
                    currency = it.currency,
                    dueDate = it.dueDate,
                    status = it.status,
                    overdue = it.overdue,
                    fiscalDisposition = it.fiscalDisposition,
                    fiscalInvoiceId = it.fiscalInvoiceId,
                    fiscalInvoiceStatus = it.fiscalInvoiceStatus
                )
            },
            payments = readRepository.findPayments(guardianUserId).map {
                GuardianClientPaymentDto(
                    paymentId = it.paymentId,
                    paymentDate = it.paymentDate,
                    amount = it.amount,
                    allocatedAmount = it.allocatedAmount,
                    currency = it.currency,
                    status = it.status,
                    paymentMethod = it.paymentMethod,
                    receiptId = it.receiptId,
                    receiptNumber = it.receiptNumber,
                    invoiceId = it.invoiceId,
                    invoiceStatus = it.invoiceStatus
                )
            }
        )
    }

    fun updateProfile(
        guardianUserId: Long,
        request: UpdateGuardianClientProfileRequest,
        updatedBy: Long
    ): GuardianClientDetailDto {
        if (!readRepository.existsGuardian(guardianUserId)) {
            throw ResourceNotFoundException("Guardian client not found with id: $guardianUserId")
        }
        provisionGuardianClient(guardianUserId, updatedBy)
        val current = profileRepository.findById(guardianUserId)
            .orElseThrow { ResourceNotFoundException("Guardian client profile not found with id: $guardianUserId") }
        if (current.version != request.version) {
            throw ResourceConflictException(
                message = "Guardian client profile was modified by another user",
                code = "GUARDIAN_CLIENT_VERSION_CONFLICT",
                field = "version"
            )
        }
        profileRepository.saveAndFlush(
            current.copy(
                preferredContactChannel = request.preferredContactChannel,
                administrativeNotes = request.administrativeNotes?.trim()?.takeIf { it.isNotEmpty() },
                updatedBy = updatedBy,
                updatedAt = LocalDateTime.now()
            )
        )
        return getDetail(guardianUserId)
    }

    override fun provisionGuardianClient(guardianUserId: Long, updatedBy: Long?) {
        profileRepository.insertIfMissing(guardianUserId, clientNumber(guardianUserId), updatedBy)
    }

    private fun clientNumber(guardianUserId: Long): String = "CLI-${guardianUserId.toString().padStart(12, '0')}"

    private fun com.sigep.guardians.domain.model.GuardianClientSummaryReadModel.toDto() = GuardianClientSummaryDto(
        guardianUserId = guardianUserId,
        clientNumber = clientNumber,
        firstName = firstName,
        lastName = lastName,
        email = email,
        phoneNumber = phoneNumber,
        documentNumber = documentNumber,
        accountStatus = accountStatus,
        accountActive = accountActive,
        preferredContactChannel = preferredContactChannel,
        studentCount = studentCount,
        activeStudentCount = activeStudentCount,
        activeEnrollmentCount = activeEnrollmentCount,
        tuitionApplicationCount = tuitionApplicationCount,
        billingAccountId = billingAccountId,
        billingAccountStatus = billingAccountStatus,
        billingProfileStatus = billingProfileStatus,
        openChargeCount = openChargeCount,
        overdueChargeCount = overdueChargeCount,
        outstandingAmount = outstandingAmount,
        lastPaymentDate = lastPaymentDate,
        missingContactData = missingContactData,
        profileVersion = profileVersion
    )
}
