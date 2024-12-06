package cloud.mallne.dicentra.aviator.core.model.oas.media

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.examples.Example
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class MediaType(
    var schema: Schema? = null,
    var examples: MutableMap<String, Example>? = null,
    var example: JsonObject? = null,
    var encoding: MutableMap<String, Encoding>? = null,
    var extensions: MutableMap<String, JsonObject>? = null,
) {

    val exampleSetFlag: Boolean
        get() = this.example != null

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val mediaType = o as MediaType
        return this.schema == mediaType.schema &&
                this.examples == mediaType.examples &&
                this.example == mediaType.example &&
                this.encoding == mediaType.encoding &&
                this.extensions == mediaType.extensions
    }

    override fun hashCode(): Int {
        return hashAll(schema, examples, example, encoding, extensions)
    }

    override fun toString(): String {
        val sb = """class MediaType {
    schema: ${toIndentedString(schema)}
    examples: ${toIndentedString(examples)}
    example: ${toIndentedString(example)}
    encoding: ${toIndentedString(encoding)}
}"""
        return sb
    }
}

