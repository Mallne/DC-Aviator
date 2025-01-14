package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Header fields have the same meaning as for 'Param'. Style is always treated as [cloud.mallne.dicentra.aviator.koas.Style.simple], as
 * it is the only value allowed for headers.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Header.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Header(
    /** A short description of the header. */
    val description: String? = null,
    val required: Boolean? = null,
    val deprecated: Boolean = false,
    val allowEmptyValue: Boolean? = null,
    val explode: Boolean? = null,
    val example: ExampleValue? = null,
    val examples: Map<String, ReferenceOr<Example>>? = null,
    val content: Map<String, MediaType> = emptyMap(),
    val schema: ReferenceOr<Schema>? = null,
    override var extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Header>(
                generatedSerializer(),
                Header::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}