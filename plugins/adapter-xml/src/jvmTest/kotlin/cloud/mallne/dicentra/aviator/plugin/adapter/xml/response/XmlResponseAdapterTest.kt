package cloud.mallne.dicentra.aviator.plugin.adapter.xml.response

import io.ktor.http.ContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class XmlResponseAdapterTest {
    @Test
    fun defaultsToUtf8WhenNoCharsetIsDeclared() {
        val xml = "<root>Grüße</root>"

        assertEquals(
            xml,
            XmlResponseAdapter.decodeXml(xml.encodeToByteArray(), ContentType.Application.Xml)
        )
    }

    @Test
    fun honorsHttpDeclaredCharset() {
        val bytes = "<root>Gr".encodeToByteArray() +
            byteArrayOf(0xFC.toByte(), 0xDF.toByte()) +
            "e".encodeToByteArray() +
            "</root>".encodeToByteArray()
        val contentType = ContentType.parse("application/xml; charset=ISO-8859-1")

        assertEquals("<root>Grüße</root>", XmlResponseAdapter.decodeXml(bytes, contentType))
    }

    @Test
    fun rejectsMalformedUtf8() {
        assertFails {
            XmlResponseAdapter.decodeXml(byteArrayOf(0xC3.toByte(), 0x28), ContentType.Application.Xml)
        }
    }
}
