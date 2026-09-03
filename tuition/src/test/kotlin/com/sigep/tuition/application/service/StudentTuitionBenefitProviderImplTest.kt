package com.sigep.tuition.application.service

import com.sigep.tuition.domain.model.TuitionDiscount
import com.sigep.tuition.domain.model.TuitionDiscountType
import com.sigep.tuition.domain.repository.TuitionDiscountRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class StudentTuitionBenefitProviderImplTest {
    private val discountRepository = mockk<TuitionDiscountRepository>()
    private val provider = StudentTuitionBenefitProviderImpl(discountRepository)

    @Test
    fun `groups direct discounts and scholarships by student`() {
        every {
            discountRepository.findByStudentIdInOrderByCreatedAtDesc(listOf(10L, 20L))
        } returns listOf(
            benefit(1L, 10L, TuitionDiscountType.SCHOLARSHIP, BigDecimal("50.00")),
            benefit(2L, 20L, TuitionDiscountType.DISCOUNT, BigDecimal("10.00"))
        )

        val result = provider.getBenefitsByStudentIds(listOf(10L, 20L, 10L))

        assertEquals("SCHOLARSHIP", result.getValue(10L).single().type)
        assertEquals("DISCOUNT", result.getValue(20L).single().type)
        verify(exactly = 1) {
            discountRepository.findByStudentIdInOrderByCreatedAtDesc(listOf(10L, 20L))
        }
    }

    private fun benefit(
        id: Long,
        studentId: Long,
        type: TuitionDiscountType,
        percentage: BigDecimal
    ) = TuitionDiscount(
        id = id,
        studentId = studentId,
        type = type,
        percentage = percentage,
        validFrom = LocalDate.of(2026, 3, 1),
        reason = "Beneficio de prueba",
        active = true
    )
}
