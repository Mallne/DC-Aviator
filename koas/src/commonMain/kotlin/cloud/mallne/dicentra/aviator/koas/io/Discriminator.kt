package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.extensions.Extendable
import cloud.mallne.dicentra.aviator.koas.extensions.KSerializerWithExtensions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
@Serializable(Discriminator.Companion.Serializer::class)
@KeepGeneratedSerializer
data class Discriminator(
    val propertyName: String,
    val mapping: Map<String, String> = emptyMap(),
    override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extendable {
    companion object {
        internal object Serializer :
            KSerializerWithExtensions<Discriminator>(
                generatedSerializer(),
                Discriminator::extensions,
                { op, extensions -> op.copy(extensions = extensions) }
            )
    }
}