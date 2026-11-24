package cloud.mallne.dicentra.aviator.plugin.synapse

import cloud.mallne.dicentra.aviator.orchestration.arazzo.model.ArazzoDocument
import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidationResult
import cloud.mallne.dicentra.aviator.orchestration.arazzo.validation.ArazzoValidator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Preserves inert extension fields while exposing the typed Arazzo model to clients. */
object WorkflowDocumentSerializer : KSerializer<ArazzoDocument> {
    override val descriptor: SerialDescriptor = ArazzoDocument.serializer().descriptor

    override fun deserialize(decoder: Decoder): ArazzoDocument {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JSON")
        val raw = input.decodeJsonElement()
        return when (val parsed = ArazzoValidator.parseAndValidate(raw.toString())) {
            is ArazzoValidationResult.Valid -> parsed.document
            is ArazzoValidationResult.Invalid ->
                input.json.decodeFromJsonElement(ArazzoDocument.serializer(), raw)
        }
    }

    override fun serialize(encoder: Encoder, value: ArazzoDocument) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Expected JSON")
        val root = output.json.encodeToJsonElement(ArazzoDocument.serializer(), value).jsonObject.toMutableMap()
        root.putAll(value.extensions)
        root["info"] = withExtensions(root.getValue("info").jsonObject, value.info.extensions)
        root["sourceDescriptions"] = JsonArray((root.getValue("sourceDescriptions") as JsonArray).mapIndexed { index, item ->
            withExtensions(item.jsonObject, value.sourceDescriptions[index].extensions)
        })
        root["workflows"] = JsonArray((root.getValue("workflows") as JsonArray).mapIndexed { wi, item ->
            val workflow = value.workflows[wi]
            val fields = item.jsonObject.toMutableMap()
            fields.putAll(workflow.extensions)
            fields["steps"] = JsonArray((fields.getValue("steps") as JsonArray).mapIndexed { si, stepItem ->
                val step = workflow.steps[si]
                val stepFields = stepItem.jsonObject.toMutableMap()
                stepFields.putAll(step.extensions)
                (stepFields["parameters"] as? JsonArray)?.let { parameters ->
                    stepFields["parameters"] = JsonArray(parameters.mapIndexed { pi, parameter ->
                        withExtensions(parameter.jsonObject, step.parameters[pi].extensions)
                    })
                }
                (stepFields["successCriteria"] as? JsonArray)?.let { criteria ->
                    stepFields["successCriteria"] = JsonArray(criteria.mapIndexed { ci, criterion ->
                        withExtensions(criterion.jsonObject, requireNotNull(step.successCriteria)[ci].extensions)
                    })
                }
                JsonObject(stepFields)
            })
            JsonObject(fields)
        })
        value.components?.let { components ->
            root["components"] = withExtensions(root.getValue("components").jsonObject, components.extensions)
        }
        output.encodeJsonElement(JsonObject(root))
    }

    private fun withExtensions(value: JsonObject, extensions: Map<String, kotlinx.serialization.json.JsonElement>): JsonObject =
        JsonObject(value + extensions)
}
