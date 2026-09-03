package com.sigep.tuition.application.service

import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.tuition.domain.model.TuitionDiscount
import com.sigep.tuition.domain.model.TuitionDiscountType
import com.sigep.tuition.domain.repository.TuitionAcademicYearRepository
import com.sigep.tuition.domain.repository.TuitionDiscountRepository
import com.sigep.tuition.domain.repository.TuitionEnrollmentFeePolicyRepository
import com.sigep.tuition.domain.repository.TuitionFeePlanRepository
import com.sigep.tuition.domain.repository.TuitionLevelProgressionRepository
import com.sigep.tuition.domain.repository.TuitionLevelRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import kotlin.test.assertEquals

class TuitionCatalogServiceDiscountTest {
    private val discountRepository = mockk<TuitionDiscountRepository>()
    private val studentProfileProvider = mockk<StudentProfileProvider>()
    private val service = TuitionCatalogService(
        mockk<TuitionAcademicYearRepository>(relaxed = true),
        mockk<TuitionLevelRepository>(relaxed = true),
        mockk<TuitionLevelProgressionRepository>(relaxed = true),
        mockk<TuitionFeePlanRepository>(relaxed = true),
        mockk<TuitionEnrollmentFeePolicyRepository>(relaxed = true),
        discountRepository,
        studentProfileProvider
    )

    @Test
    fun `discount list enriches direct student scope with the student name`() {
        every { discountRepository.findAll(any<Pageable>()) } returns PageImpl(
            listOf(
                TuitionDiscount(
                    id = 5L,
                    studentId = 42L,
                    type = TuitionDiscountType.SCHOLARSHIP,
                    percentage = 50.toBigDecimal(),
                    validFrom = LocalDate.of(2026, 3, 1),
                    reason = "Beca institucional"
                )
            )
        )
        val profile = mockk<StudentProfileInfo>()
        every { profile.firstName } returns "Juana"
        every { profile.lastName } returns "Perez"
        every { studentProfileProvider.getStudentProfiles(listOf(42L)) } returns mapOf(42L to profile)

        val result = service.listDiscounts(0, 10).content.single()

        assertEquals("Juana", result.studentFirstName)
        assertEquals("Perez", result.studentLastName)
        verify(exactly = 1) { studentProfileProvider.getStudentProfiles(listOf(42L)) }
    }
}
