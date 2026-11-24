package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals(emptyList(), Json.decodeFromString<SynapseDiscoveryResponse>("""{"services":[]}""").services)
        val authenticated = Json.decodeFromString<SynapseDiscoveryResponse>(
            """{"services":[],"principal":{"name":"Name","email":"email","username":"user","access":{}}}""",
        )
        assertEquals("user", authenticated.principal?.username)
    }

    @Test
    fun workflowDiscoveryDecodesStoredArazzoDocuments() {
        val workflows = Json.decodeFromString<List<SynapseWorkflow>>(
            """[{"id":"builtin-weather","scope":null,"builtin":true,"document":{"arazzo":"1.1.0","workflows":[]}}]""",
        )
        assertEquals("builtin-weather", workflows.single().id)
        assertTrue(workflows.single().builtin)
        assertEquals("1.1.0", workflows.single().document.jsonObject["arazzo"]?.jsonPrimitive?.content)
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
