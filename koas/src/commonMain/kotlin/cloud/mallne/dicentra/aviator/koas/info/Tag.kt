package cloud.mallne.dicentra.aviator.koas.info

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Allows adding metadata to a single tag that is used by @Operation@. It is not mandatory to have
 * a @Tag@ per tag used there.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Tag.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Tag(
    /** The name of the tag. */
    val name: String,
    /**
     * A short description for the tag. [CommonMark syntax](https://spec.commonmark.org/) MAY be used
     * for rich text representation.
     */
    val description: String? = null,
    /**
     * The kind field allows you to use different sets of tags for different purposes. The goal here is to enable
     * arbitrary grouping and labeling of endpoints, for multiple use cases.
     */
    val kind: TagKind? = null,
    /** The summary field provides a short display name for tags, useful in navigation and lists. */
    val summary: String? = null,
    /** Create tag hierarchies using the parent field, which enables a tag to be nested inside another tag. */
    val parent: String? = null,
    /** Additional external documentation for this tag. */
    val externalDocs: ExternalDocs? = null,
    override var extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    companion object {
        internal object Serializer : KSerializerWithExtensions<Tag>(
            generatedSerializer(), Tag::extensions, { op, extensions -> op.copy(extensions = extensions) })
    }
}