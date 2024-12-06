package cloud.mallne.dicentra.aviator.core.model.oas.media

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.helper.toIndentedString
import kotlinx.serialization.Serializable

@Serializable
@Deprecated("use a simple map instead", replaceWith = ReplaceWith("MutableMap<String, MediaType>"))
class Content : LinkedHashMap<String, MediaType>() {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        return super.equals(o)
    }

    override fun hashCode(): Int {
        return hashAll(super.hashCode())
    }

    override fun toString(): String {
        val sb = """class Content {
    ${toIndentedString(super.toString())}
}"""
        return sb
    }
}

