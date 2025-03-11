package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import kotlinx.serialization.Serializable

@Serializable
enum class AviatorExecutionStages {
    @InternalAviatorAPI
    Unstarted,
    Invocation, // Service gets called
    ConstraintValidation, //Constraints of the OpenAPI Schema get addressed
    PathMatching, //The Url is built
    FormingRequest, //The Request is built
    Requesting,
    PaintingResponse,
    Finished
}