package cloud.mallne.dicentra.aviator.koas.typed

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TemplateParserDefaultsTest {
    @Test
    fun `query defaults support scalars and arrays without interpreting objects as primitives`() {
        assertEquals(listOf("Auto"), TemplateParser.defaultQueryValues(Json.parseToJsonElement("\"Auto\"")))
        assertEquals(listOf("one", "two"), TemplateParser.defaultQueryValues(Json.parseToJsonElement("[\"one\",\"two\"]")))
        assertNull(TemplateParser.defaultQueryValues(Json.parseToJsonElement("null")))
        assertNull(TemplateParser.defaultQueryValues(Json.parseToJsonElement("{\"key\":\"value\"}")))
        assertNull(TemplateParser.defaultQueryValues(Json.parseToJsonElement("[null]")))
    }
}
