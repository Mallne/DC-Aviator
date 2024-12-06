package cloud.mallne.dicentra.aviator.core.model.oas.media

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.model.oas.headers.Header
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class Encoding(
    var contentType: String? = null,
    var headers: MutableMap<String, Header>? = null,
    var style: StyleEnum? = null,
    var explode: Boolean? = null,
    var allowReserved: Boolean? = null,
    var extensions: MutableMap<String, JsonObject>? = null
) {

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val encoding = o as Encoding
        return this.contentType == encoding.contentType &&
                this.headers == encoding.headers &&
                this.style == encoding.style &&
                this.explode == encoding.explode &&
                this.extensions == encoding.extensions &&
                this.allowReserved == encoding.allowReserved
    }

    override fun hashCode(): Int {
        return hashAll(contentType, headers, style, explode, allowReserved, extensions)
    }

    override fun toString(): String {
        return "Encoding{" +
                "contentType='" + contentType + '\'' +
                ", headers=" + headers +
                ", style='" + style + '\'' +
                ", explode=" + explode +
                ", allowReserved=" + allowReserved +
                ", extensions=" + extensions +
                '}'
    }

    enum class StyleEnum(private val value: String) {
        @SerialName("form") FORM("form"),
        @SerialName("spaceDelimited") SPACE_DELIMITED("spaceDelimited"),
        @SerialName("pipeDelimited") PIPE_DELIMITED("pipeDelimited"),
        @SerialName("deepObject") DEEP_OBJECT("deepObject");

        companion object {
            fun fromString(value: String): StyleEnum? {
                for (e in entries) {
                    if (e.value == value) {
                        return e
                    }
                }
                return null
            }
        }
    }
}
