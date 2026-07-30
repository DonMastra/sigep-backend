package com.sigep.payments.application.service

import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.payments.application.dto.UpdateBillingProfileRequest
import com.sigep.payments.domain.model.BillingAccount
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
import org.junit.jupiter.api.Test
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
