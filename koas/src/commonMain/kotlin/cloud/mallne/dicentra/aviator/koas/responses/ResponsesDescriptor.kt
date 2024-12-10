package cloud.mallne.dicentra.aviator.koas.responses

import cloud.mallne.dicentra.aviator.koas.extensions.ReferenceOr
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

@OptIn(ExperimentalSerializationApi::class)
internal object ResponsesDescriptor : SerialDescriptor {
    override val serialName: String = "cloud.mallne.dicentra.aviator.koas.responses.Responses"
    override val kind: SerialKind = StructureKind.MAP
    override val elementsCount: Int = 2
    private val keyDescriptor: SerialDescriptor = Int.serializer().descriptor
    private val valueDescriptor: SerialDescriptor =
        ReferenceOr.Companion.serializer(Response.serializer()).descriptor

    override fun getElementName(index: Int): String = index.toString()

    override fun getElementIndex(name: String): Int =
        name.toIntOrNull() ?: throw IllegalArgumentException("$name is not a valid list index")

    override fun isElementOptional(index: Int): Boolean {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
        return false
    }

    override fun getElementAnnotations(index: Int): List<Annotation> {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
        return emptyList()
    }

    override fun getElementDescriptor(index: Int): SerialDescriptor {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices" }
        return when (index % 2) {
            0 -> keyDescriptor
            1 -> valueDescriptor
            else -> error("Unreached")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResponsesDescriptor) return false
        if (serialName != serialName) return false
        if (keyDescriptor != keyDescriptor) return false
        if (valueDescriptor != valueDescriptor) return false
        return true
    }

    override fun hashCode(): Int {
        var result = serialName.hashCode()
        result = 31 * result + keyDescriptor.hashCode()
        result = 31 * result + valueDescriptor.hashCode()
        return result
    }

    override fun toString(): String = "$serialName($keyDescriptor, $valueDescriptor)"
}
