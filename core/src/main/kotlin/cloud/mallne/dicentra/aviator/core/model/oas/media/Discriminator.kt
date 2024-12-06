package cloud.mallne.dicentra.aviator.core.model.oas.media

import cloud.mallne.dicentra.aviator.core.helper.hashAll
import cloud.mallne.dicentra.aviator.core.model.oas.Extendable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class Discriminator(
    var propertyName: String? = null,
    var mapping: MutableMap<String, String> = mutableMapOf(),
    override var extensions: MutableMap<String, JsonElement> = mutableMapOf(),
): Extendable {
    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is Discriminator) {
            return false
        }

        if (propertyName != o.propertyName) {
            return false
        }
        if (extensions != o.extensions) {
            return false
        }
        return mapping == o.mapping
    }

    override fun hashCode(): Int {
        return hashAll(propertyName, mapping, extensions)
    }

    override fun toString(): String {
        return "Discriminator{" +
                "propertyName='" + propertyName + '\'' +
                ", mapping=" + mapping +
                ", extensions=" + extensions +
                '}'
    }
}
