package cloud.mallne.dicentra.aviator.koas.servers

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** An object representing a Server Variable for server URL template substitution. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(Variable.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Variable(
    /**
     * An enumeration of string values to be used if the substitution options are from a limited
     * set.
     */
    val enum: List<String> = emptyList(),
    /**
     * The default value to use for substitution, which SHALL be sent if an alternate value is not
     * supplied. Note this behavior is different than the Schema Object's treatment of default
     * values, because in those cases parameter values are optional. If the enum is defined, the
     * value SHOULD exist in the enum's values.
     */
    val default: String,
    /**
     * An optional description for the server variable. CommonMark syntax MAY be used for rich text
     * representation.
     */
    val description: String? = null,
    override val extensions: Map<String, JsonElement> = emptyMap()
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Variable>(
                generatedSerializer(),
                Variable::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}