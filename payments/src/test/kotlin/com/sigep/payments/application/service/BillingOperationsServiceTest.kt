package com.sigep.payments.application.service

import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.payments.application.dto.BillingChargeFilterRequest
import com.sigep.payments.application.dto.PrepareBillingRunRequest
import com.sigep.payments.application.dto.UpdateBillingProfileRequest
import com.sigep.payments.domain.model.BillingAccount
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingSelectionMode
import com.sigep.payments.domain.model.FiscalAmountTreatment
import com.sigep.payments.domain.model.BillingProfile
import com.sigep.payments.domain.model.BillingProfileStatus
import com.sigep.payments.domain.repository.BillingAccountRepository
import com.sigep.payments.domain.repository.BillingChargeRepository
import com.sigep.payments.domain.repository.BillingProfileRepository
import com.sigep.payments.domain.repository.BillingRunItemRepository
import com.sigep.payments.domain.repository.BillingRunRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentAllocationRepository
import com.sigep.payments.domain.repository.PaymentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BillingOperationsServiceTest {

    private val accountRepository = mockk<BillingAccountRepository>(relaxed = true)
    private val profileRepository = mockk<BillingProfileRepository>()
    private val chargeRepository = mockk<BillingChargeRepository>(relaxed = true)
    private val allocationRepository = mockk<PaymentAllocationRepository>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val invoiceRepository = mockk<FiscalInvoiceRepository>(relaxed = true)
    private val runRepository = mockk<BillingRunRepository>(relaxed = true)
    private val runItemRepository = mockk<BillingRunItemRepository>(relaxed = true)
    private val paymentService = mockk<PaymentApplicationService>(relaxed = true)
    private val billingService = mockk<BillingApplicationService>(relaxed = true)
    private val service = BillingOperationsService(
        accountRepository,
        profileRepository,
        chargeRepository,
        allocationRepository,
        paymentRepository,
        invoiceRepository,
        runRepository,
        runItemRepository,
        paymentService,
        billingService,
        emptyList<BillingChargeSettlementObserver>()
    )

    @Test
    fun `charge listing trims student search and keeps server pagination`() {
        every {
            chargeRepository.findByFilters(any(), any(), any(), any(), any())
        } returns PageImpl(emptyList(), PageRequest.of(0, 25), 0)

        val result = service.listCharges(
            status = BillingChargeStatus.OPEN,
            studentId = null,
            studentQuery = "  Ana Perez  ",
            profileStatus = BillingProfileStatus.READY,
            page = 0,
            size = 25
        )

        assertEquals(0, result.totalElements)
        verify(exactly = 1) {
            chargeRepository.findByFilters(
                BillingChargeStatus.OPEN,
                null,
                "Ana Perez",
                BillingProfileStatus.READY,
                match { it.pageNumber == 0 && it.pageSize == 25 }
            )
        }
    }

    @Test
    fun `filtered billing preview keeps the student search`() {
        every {
            chargeRepository.findByFilters(any(), any(), any(), any(), any())
        } returns PageImpl(emptyList(), PageRequest.of(0, 1000), 0)

        val result = service.preview(
            PrepareBillingRunRequest(
                selectionMode = BillingSelectionMode.FILTERED,
                filters = BillingChargeFilterRequest(
                    status = BillingChargeStatus.OPEN,
                    studentQuery = "Ana Perez"
                ),
                issueDate = LocalDate.of(2026, 8, 5),
                amountTreatment = FiscalAmountTreatment.NON_TAXED
            )
        )

        assertEquals(0, result.selectedCount)
        verify(exactly = 1) {
            chargeRepository.findByFilters(
                BillingChargeStatus.OPEN,
                null,
                "Ana Perez",
                null,
                match { it.pageSize == 1000 }
            )
        }
    }

    @Test
    fun `validated profile becomes reusable while RG 5866 remains disabled`() {
        val account = BillingAccount(id = 10L, guardianUserId = 20L, displayName = "Tutor")
        val current = BillingProfile(
            id = 30L,
            account = account,
            receiverName = "Tutor",
            receiverDocumentNumber = "30111222",
            status = BillingProfileStatus.INCOMPLETE
        )
        val saved = slot<BillingProfile>()
        every { profileRepository.findByAccountId(10L) } returns Optional.of(current)
        every { profileRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.updateProfile(
            accountId = 10L,
            request = UpdateBillingProfileRequest(
                receiverName = "Tutor Responsable",
                receiverAddress = "Calle 123, Buenos Aires",
                receiverDocumentType = 96,
                receiverDocumentNumber = "30111222",
                receiverVatConditionId = 5,
                defaultVoucherType = 11,
                defaultFiscalConcept = 2,
                fiscalCurrency = "PES"
            ),
            adminId = 1L
        )

        assertEquals(BillingProfileStatus.READY, result.status)
        assertEquals(1L, saved.captured.updatedBy)
        assertFalse(result.rg5866Applicable)
        assertEquals(emptyList(), result.missingFields)
    }
}
