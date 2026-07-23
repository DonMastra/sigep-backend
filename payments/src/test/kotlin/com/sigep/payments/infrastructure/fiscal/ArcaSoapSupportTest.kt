package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.infrastructure.fiscal.ArcaXml.first
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArcaSoapSupportTest {

    @Test
    fun `accepts BOM and indentation before an XML declaration`() {
        val document = ArcaXml.parse(
            "\uFEFF   \r\n<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><value>ok</value></root>"
        )

        assertEquals("ok", document.first("value")?.textContent)
    }

    @Test
    fun `still rejects document type declarations`() {
        assertFailsWith<ArcaProtocolException> {
            ArcaXml.parse("<!DOCTYPE root [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><root>&xxe;</root>")
        }
    }
}
