package cloud.mallne.dicentra.aviator.plugin.weaver

import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.weaver.core.Weaver
import cloud.mallne.dicentra.weaver.core.specification.LimboObjectDeclaration
import cloud.mallne.dicentra.weaver.core.specification.ObjectType
import cloud.mallne.dicentra.weaver.core.specification.PropertyDeclaration
import cloud.mallne.dicentra.weaver.core.specification.WeaverSchema
import cloud.mallne.dicentra.weaver.core.specification.compute.ComputeDeclaration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeaverServiceObjectTest {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun missingSchemaMeansNoTransformationIsConfigured() {
        val options = buildJsonObject {
            put("customExtension", "retained by the service definition")
        }

        val decoded = lenientJson.decodeFromJsonElement<WeaverServiceObject>(options)

        assertNull(decoded.schema)
    }

    @Test
    fun optionsMetadataCanBeIgnoredWhileTheConfiguredSchemaTransformsData() {
        val schema = WeaverSchema(
            key = "temperature-projection",
            root = PropertyDeclaration(name = "result", value = "#temperature#"),
            transforms = listOf(
                LimboObjectDeclaration(
                    key = "temperature",
                    accessor = "current.temperature",
                    compute = ComputeDeclaration(type = ObjectType.Number),
                ),
            ),
        )
        val options = buildJsonObject {
            put("customExtension", "provider configuration remains outside Weaver")
            put("x-dicentra-weaver-schema", lenientJson.encodeToJsonElement(WeaverSchema.serializer(), schema))
        }

        val decoded = lenientJson.decodeFromJsonElement<WeaverServiceObject>(options)
        val input = buildJsonObject {
            put("current", buildJsonObject { put("temperature", 18) })
        }

        val transformed = Weaver().engine(assertNotNull(decoded.schema)).executeElement(input)

        assertEquals(JsonPrimitive(18), transformed)
    }

    @Test
    fun requestParameterMetadataMapsDropsAndDefaultsOperationParameters() {
        val options = WeaverServiceObject(
            requestParameterNames = mapOf("lat" to "latitude", "lon" to "longitude"),
            requestDefaults = mapOf("timezone" to "auto", "forecast_days" to "7"),
            ignoredRequestParameters = setOf("date"),
        )
        val parameters = RequestParameters(mutableMapOf(
            "lat" to RequestParameter.Single(52.52),
            "lon" to RequestParameter.Single(13.41),
            "timezone" to RequestParameter.Single("Europe/Berlin"),
            "date" to RequestParameter.Single("2026-10-19"),
        ))

        options.adaptRequestParameters(parameters)

        assertEquals(RequestParameter.Single(52.52), parameters["latitude"])
        assertEquals(RequestParameter.Single(13.41), parameters["longitude"])
        assertEquals(RequestParameter.Single("Europe/Berlin"), parameters["timezone"])
        assertEquals(RequestParameter.Single("7"), parameters["forecast_days"])
        assertEquals(null, parameters["lat"])
        assertEquals(null, parameters["date"])
    }
}
