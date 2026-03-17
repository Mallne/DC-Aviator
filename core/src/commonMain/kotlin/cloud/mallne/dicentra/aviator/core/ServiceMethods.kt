package cloud.mallne.dicentra.aviator.core

import kotlinx.serialization.Serializable

@Serializable
enum class ServiceMethods(
    val serviceFlavour: String
) {
    GATHER("gather"),
    UPDATE("update"),
    CREATE("create"),
    UPSERT("upsert"),
    DELETE("delete")
}