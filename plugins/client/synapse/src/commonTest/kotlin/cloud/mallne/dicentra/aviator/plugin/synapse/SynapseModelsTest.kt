package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynapseModelsTest {
    @Test
    fun accountDecodesServerFieldsAndScopes() {
        val user = Json.decodeFromString<SynapseUser>(
            """{"name":"Name","email":"email","username":"user","access":{"user":true,"superAdmin":false},"scopes":{"group":"ADMIN"},"userScope":{"first":"user:user","second":"USER"},"valid":true}""",
        )
        assertTrue(user.isAdminOf("group"))
        assertTrue(user.canWriteTo("group"))
        assertEquals("user:user" to SynapseScopeType.USER, user.userScope)
        assertEquals("user", Json.parseToJsonElement(Json.encodeToString(user)).jsonObject["username"]?.toString()?.trim('"'))
    }

    @Test
    fun discoveryAcceptsAnonymousAndAuthenticatedResponses() {
        val legacy = Json.decodeFromString<SynapseDiscoveryResponse>("""{"services":[]}""")
        assertEquals(emptyList(), legacy.services)
        assertEquals(emptyList(), legacy.workflows)
        val authenticated = Json.decodeFromString<SynapseDiscoveryResponse>(
            """{"services":[],"workflows":[{"id":"weather","document":{"arazzo":"1.1.0","info":{"title":"Weather","version":"1"},"sourceDescriptions":[{"name":"api","url":"weather.json","type":"openapi"}],"workflows":[]}}],"principal":{"name":"Name","email":"email","username":"user","access":{}}}""",
        )
        assertEquals("user", authenticated.principal?.username)
        assertEquals("weather", authenticated.workflows.single().id)
        assertEquals("1.1.0", authenticated.workflows.single().document.arazzo)
    }

    @Test
    fun workflowDiscoveryDecodesStoredArazzoDocuments() {
        val workflows = Json.decodeFromString<List<SynapseWorkflow>>(
            """[{"id":"builtin-weather","scope":null,"builtin":true,"document":{"arazzo":"1.1.0","info":{"title":"Weather","version":"1"},"sourceDescriptions":[{"name":"api","url":"weather.json","type":"openapi"}],"workflows":[]}}]""",
        )
        assertEquals("builtin-weather", workflows.single().id)
        assertTrue(workflows.single().builtin)
        assertEquals("1.1.0", workflows.single().document.arazzo)
    }

    @Test
    fun workflowDiscoveryPreservesExtensionsInTypedDocuments() {
        val source = """{"id":"weather","document":{"arazzo":"1.1.0","info":{"title":"Weather","version":"1","x-info":2},"sourceDescriptions":[{"name":"api","url":"weather.json","type":"openapi"}],"components":{"x-component":"kept"},"workflows":[{"workflowId":"forecast","steps":[{"stepId":"fetch","operationId":"getData","parameters":[{"name":"lat","in":"query","value":1,"x-parameter":"kept"}],"successCriteria":[{"condition":"${'$'}statusCode == 200","x-criterion":"kept"}],"x-step":true}]}],"x-note":"preserved"}}"""
        val workflow = Json.decodeFromString<SynapseWorkflow>(source)
        val result = Json.parseToJsonElement(Json.encodeToString(workflow)).jsonObject.getValue("document").jsonObject
        assertEquals("\"preserved\"", result.getValue("x-note").toString())
        assertEquals("2", result.getValue("info").jsonObject.getValue("x-info").toString())
        assertTrue(result.toString().contains("\"x-step\":true"))
        assertTrue(result.toString().contains("\"x-component\":\"kept\""))
        assertTrue(result.toString().contains("\"x-parameter\":\"kept\""))
        assertTrue(result.toString().contains("\"x-criterion\":\"kept\""))
    }

    @Test
    fun transformationTypePreservesWireValuesAndParsing() {
        ServiceDefinitionTransformationType.entries.forEach { type ->
            assertEquals(type, ServiceDefinitionTransformationType.fromString(type.name.lowercase()))
            assertEquals(type.name, discoveryRequestParameters(type).getValue("transformationType").asType<String>())
        }
        assertEquals(ServiceDefinitionTransformationType.Auto, ServiceDefinitionTransformationType.fromString("unknown"))
    }
}
