package cloud.mallne.dicentra.aviator.orchestration.arazzo

import cloud.mallne.dicentra.aviator.orchestration.arazzo.execution.atJsonPointer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ArazzoJsonPointerTest {
    private val document = Json.parseToJsonElement("""{"a/b":{"~key":[null,{"value":7}]}}""")

    @Test
    fun resolvesEscapedObjectKeysAndArrayIndexes() {
        assertEquals(JsonPrimitive(7), document.atJsonPointer("/a~1b/~0key/1/value"))
    }

    @Test
    fun distinguishesExplicitNullFromMissingTarget() {
        assertEquals(JsonNull, document.atJsonPointer("/a~1b/~0key/0"))
        assertNull(document.atJsonPointer("/a~1b/~0key/9"))
    }

    @Test
    fun rejectsMalformedPointersAndEscapes() {
        assertFailsWith<IllegalArgumentException> { document.atJsonPointer("a/b") }
        assertFailsWith<IllegalArgumentException> { document.atJsonPointer("/a~2b") }
        assertNull(document.atJsonPointer("/a~1b/~0key/01"))
    }
}
