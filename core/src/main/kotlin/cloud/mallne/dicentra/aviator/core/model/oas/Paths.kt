package cloud.mallne.dicentra.aviator.core.model.oas

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
class Paths(
    override var extensions: MutableMap<String, JsonElement> = mutableMapOf(),
) : LinkedHashMap<String, PathItem>(), Extendable {

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

