package cloud.mallne.dicentra.aviator.plugin.synapse

import kotlinx.serialization.Serializable
import io.ktor.openapi.OpenApiDoc

@Serializable
data class SynapseUser(
    val name: String,
    val email: String,
    val username: String,
    val locked: Boolean = false,
    val access: SynapseAccessLevels,
    val scopes: Map<String, SynapseScopeType> = emptyMap(),
    val userScope: Pair<String, SynapseScopeType> = "user:$username" to SynapseScopeType.USER,
    val valid: Boolean = !locked && access.user,
) {
    fun isAdminOf(scopeName: String): Boolean =
        access.superAdmin || scopes.any { it.key == scopeName && it.value.canExpandOps }

    fun canWriteTo(scopeName: String): Boolean =
        access.superAdmin || scopes.any { it.key == scopeName && it.value.canWrite }

    fun isDirectMember(scopeName: String): Boolean = scopeName in scopes

    companion object {
        fun normalizeScope(scope: String): String? = scope.takeUnless { it.startsWith("user:") }
    }
}

@Serializable
data class SynapseAccessLevels(val user: Boolean = true, val superAdmin: Boolean = false)

@Serializable
enum class SynapseScopeType {
    READ, WRITE, ADMIN, USER;

    val canWrite: Boolean get() = this == WRITE || this == ADMIN || this == USER
    val canExpandOps: Boolean get() = this == ADMIN
}

@Serializable
data class SynapseDiscoveryResponse(
    val principal: SynapseUser? = null,
    val services: List<OpenApiDoc>,
)
