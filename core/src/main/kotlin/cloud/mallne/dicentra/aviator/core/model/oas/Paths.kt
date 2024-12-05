package cloud.mallne.dicentra.aviator.core.model.oas

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject


/**
 * Paths
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md.pathsObject"
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.1.0/versions/3.1.0.md.pathsObject"
 */
@Serializable
class Paths(
    var extensions: MutableMap<String, JsonObject>? = null
) : LinkedHashMap<String, PathItem>() {

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val paths = o as Paths
        return this.extensions == paths.extensions &&
                super.equals(o)
    }

    override fun hashCode(): Int = hashAll(extensions, super.hashCode())

    override fun toString(): String {
        val sb = "class Paths {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "}"
        return sb
    }
}

