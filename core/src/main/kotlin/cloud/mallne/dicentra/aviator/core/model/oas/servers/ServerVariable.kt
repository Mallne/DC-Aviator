package cloud.mallne.dicentra.aviator.core.model.oas.servers

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class ServerVariable(
    var enum: MutableList<String>? = null,
    var default: String,
    var description: String? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val serverVariable = o as ServerVariable
        return this.enum == serverVariable.enum &&
                this.default == serverVariable.default &&
                this.description == serverVariable.description &&
                this.extensions == serverVariable.extensions
    }

    override fun hashCode(): Int {
        return hashAll(enum, default, description, extensions)
    }
    override fun toString(): String {
        val sb = """class ServerVariable {
    _enum: ${toIndentedString(enum)}
    _default: ${toIndentedString(default)}
    description: ${toIndentedString(description)}
}"""
        return sb
    }

}

