package cloud.mallne.dicentra.aviator.core.model.oas.callbacks

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.PathItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class Callback(
    private var extensions: MutableMap<String, JsonElement>? = null,
    private var `$ref`: String? = null
) : LinkedHashMap<String, PathItem>() {


    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val callback = o as Callback
        if (`$ref` != callback.`$ref`) {
            return false
        }
        return this.extensions == callback.extensions &&
                super.equals(o)
    }

    override fun hashCode(): Int {
        return hashAll(extensions, `$ref`, super.hashCode())
    }

    override fun toString(): String {
        val sb = """class Callback {
    ${"$"}ref: ${toIndentedString(`$ref`)}
    ${toIndentedString(super.toString())}
}"""
        return sb
    }
}

