package cloud.mallne.dicentra.aviator.koas.info

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Allows referencing an external resource for extended documentation. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(ExternalDocs.Companion.Serializer::class)
@KeepGeneratedSerializer
data class ExternalDocs(
    /**
     * A short description of the target documentation. CommonMark syntax MAY be used for rich text
     * representation.
     */
    val description: String? = null,
    /** The URL for the target documentation. Value MUST be in the format of a URL. */
    val url: String,
    override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<ExternalDocs>(
                generatedSerializer(),
                ExternalDocs::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}