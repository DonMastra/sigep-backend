package com.sigep.tuition.application.service

import com.sigep.common.application.service.StudentTuitionBenefitInfo
import com.sigep.common.application.service.StudentTuitionBenefitProvider
import com.sigep.tuition.domain.model.TuitionDiscount
import com.sigep.tuition.domain.repository.TuitionDiscountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StudentTuitionBenefitProviderImpl(
    private val discountRepository: TuitionDiscountRepository
) : StudentTuitionBenefitProvider {

    override fun getBenefitsByStudentIds(
        studentIds: Collection<Long>
    ): Map<Long, List<StudentTuitionBenefitInfo>> {
        if (studentIds.isEmpty()) return emptyMap()

        return discountRepository.findByStudentIdInOrderByCreatedAtDesc(studentIds.distinct())
            .mapNotNull { discount ->
                discount.studentId?.let { studentId -> studentId to discount.toInfo(studentId) }
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun TuitionDiscount.toInfo(studentId: Long) = StudentTuitionBenefitInfo(
        id = id!!,
        studentId = studentId,
        type = type.name,
        percentage = percentage,
        amount = amount,
        validFrom = validFrom,
        validTo = validTo,
        reason = reason,
        active = active
    )
}
