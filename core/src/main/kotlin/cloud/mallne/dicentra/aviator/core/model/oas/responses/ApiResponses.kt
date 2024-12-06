package cloud.mallne.dicentra.aviator.core.model.oas.responses

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import cloud.mallne.dicentra.aviator.core.model.oas.annotations.OpenAPI31
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class ApiResponses(
    private var extensions: MutableMap<String, JsonObject>? = null
) : LinkedHashMap<String, ApiResponse>() {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }
        val apiResponses = o as ApiResponses
        return this.extensions == apiResponses.extensions
    }

    override fun hashCode(): Int {
        return hashAll(super.hashCode(), extensions)
    }

    override fun toString(): String {
        val sb = """class ApiResponses {
    ${toIndentedString(super.toString())}
    extensions: ${toIndentedString(extensions)}
}"""
        return sb
    }

    companion object {
        const val DEFAULT: String = "default"
    }
}

