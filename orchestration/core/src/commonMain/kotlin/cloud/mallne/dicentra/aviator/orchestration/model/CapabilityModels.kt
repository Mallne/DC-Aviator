package cloud.mallne.dicentra.aviator.orchestration.model

import cloud.mallne.dicentra.aviator.model.ServiceLocator
import cloud.mallne.dicentra.aviator.orchestration.policy.CapabilityPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Locator-native target. Null restrictions allow all eligible matches; an empty set allows none. */
@Serializable
data class CapabilityInvocation(
    val locator: ServiceLocator,
    val policy: CapabilityPolicy,
    val constituents: Set<ConstituentIdentity>? = null,
) {
    /** A targeting check, not an authorization decision. Both locator and flavour must match. */
    fun accepts(identity: ConstituentIdentity): Boolean =
        identity.locator == locator && (constituents?.contains(identity) ?: true)
}

/** Operation fields identify provenance/restrictions, not the core invocation key. */
@Serializable
data class ConstituentIdentity(
    val locator: ServiceLocator,
    val providerId: String,
    val sourceId: String,
    val sourceRevision: String,
    val operationId: String? = null,
    val operationPath: String? = null,
)

/** No synthetic aggregate HTTP metadata. Real HTTP snapshots belong to constituent results. */
@Serializable
data class CapabilityResult(
    val locator: ServiceLocator,
    val outcome: CapabilityOutcome,
    val constituents: List<ConstituentResult>,
)

@Serializable
sealed interface CapabilityOutcome {
    @Serializable
    data class Succeeded(val aggregate: BodyValue) : CapabilityOutcome

    @Serializable
    data class Failed(val failure: ExecutionFailure) : CapabilityOutcome
}

/** Retained outcomes are in policy order, not incidental network completion order. */
@Serializable
data class ConstituentResult(
    val identity: ConstituentIdentity,
    val outcome: ConstituentOutcome,
    val response: ResponseSnapshot? = null,
)

@Serializable
sealed interface ConstituentOutcome {
    @Serializable
    data class Succeeded(
        val value: BodyValue,
        val representation: ResultRepresentation = ResultRepresentation.UNKNOWN,
    ) : ConstituentOutcome

    @Serializable
    data class Failed(val failure: ExecutionFailure) : ConstituentOutcome
}

/** Describes the successful value; source HTTP bytes remain separately in [ResponseSnapshot]. */
@Serializable
enum class ResultRepresentation { SOURCE_DECODED, TRANSFORMED, UNKNOWN }

/** Source response, not a transformed output pretending to be the source contract. */
@Serializable
data class ResponseSnapshot(
    val body: BodyValue,
    val contentType: String? = null,
    val http: HttpResponseMetadata? = null,
)

/** Headers retain multiple values; readers must compare HTTP header names case-insensitively. */
@Serializable
data class HttpResponseMetadata(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
)

/** Preserve body availability independently of transport status and decoded output. */
@Serializable
sealed interface BodyValue {
    @Serializable
    data object Unavailable : BodyValue

    @Serializable
    data object Empty : BodyValue

    @Serializable
    data class JsonValue(val value: JsonElement) : BodyValue

    /** Signed octets with structural equality; encoders must not reinterpret them as text. */
    @Serializable
    data class BytesValue(val bytes: List<Byte>) : BodyValue

    @Serializable
    data class DecodeFailure(val failure: ExecutionFailure) : BodyValue
}
