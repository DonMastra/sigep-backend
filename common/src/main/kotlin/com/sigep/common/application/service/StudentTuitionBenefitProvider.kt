package com.sigep.common.application.service

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Read-only cross-module contract for discounts and scholarships assigned directly to students.
 */
interface StudentTuitionBenefitProvider {
    fun getBenefitsByStudentIds(studentIds: Collection<Long>): Map<Long, List<StudentTuitionBenefitInfo>>
}

data class StudentTuitionBenefitInfo(
    val id: Long,
    val studentId: Long,
    val type: String,
    val percentage: BigDecimal?,
    val amount: BigDecimal,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val reason: String,
    val active: Boolean
)
