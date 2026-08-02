package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.FiscalEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class DisabledFiscalAuthorityAdapterTest {

    @Test
    fun `exposes local reference catalogs while fiscal authorization is disabled`() {
        val clock = Clock.fixed(Instant.parse("2026-07-31T15:00:00Z"), ZoneOffset.UTC)
        val adapter = DisabledFiscalAuthorityAdapter(
            providerName = "disabled",
            fiscalEnvironment = FiscalEnvironment.HOMOLOGATION,
            reason = "Fiscal provider disabled",
            clock = clock
        )

        val catalogs = adapter.referenceData()

        assertEquals(listOf("1", "6", "11"), catalogs.voucherTypes.map { it.id })
        assertEquals("DNI", catalogs.documentTypes.first { it.id == "96" }.description)
        assertEquals("Consumidor Final", catalogs.receiverVatConditions.first { it.id == "5" }.description)
        assertEquals("PES", catalogs.currencies.single().id)
    }
}
