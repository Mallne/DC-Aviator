package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalystParameterCodecsTest {
    private val json = Json

    @Serializable
    data class Coordinate(val latitude: Double, val longitude: Double)

    @Test
    fun `typed scalar and structured parameters round trip with multiplicity`() {
        val parameters = RequestParameters(
            mutableMapOf(
                "enabled" to RequestParameter.Single(true),
                "coordinates" to RequestParameter.Single(
                    Coordinate(51.0, 10.0), valueSerializer = Coordinate.serializer()
                ),
                "names" to RequestParameter.Multi(listOf("one", "two")),
            )
        )
        val wire = CatalystRequest(
            typedParameters = parameters.toCatalystParameters(json),
        )
        val decodedWire = json.decodeFromJsonElement(
            CatalystRequest.serializer(),
            json.encodeToJsonElement(CatalystRequest.serializer(), wire),
        )
        val decoded = decodedWire.toRequestParameters(
            json,
            CatalystParameterCodecs(listOf(Coordinate.serializer())),
        )

        assertEquals(true, decoded.getValue("enabled").asType<Boolean>())
        assertEquals(Coordinate(51.0, 10.0), decoded.getValue("coordinates").asType<Coordinate>())
        assertEquals(listOf("one", "two"), decoded.getValue("names").asType<List<String>>())
    }

    @Test
    fun `unsupported codec fails rather than degrading to strings`() {
        val request = CatalystRequest(
            typedParameters = RequestParameters(
                mutableMapOf(
                    "value" to RequestParameter.Single(
                        Coordinate(1.0, 2.0), valueSerializer = Coordinate.serializer()
                    )
                )
            ).toCatalystParameters(json),
        )

        assertFailsWith<IllegalArgumentException> {
            request.toRequestParameters(json, CatalystParameterCodecs(emptyList()))
        }
    }
}
