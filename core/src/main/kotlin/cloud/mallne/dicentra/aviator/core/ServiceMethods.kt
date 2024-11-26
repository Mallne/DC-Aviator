package cloud.mallne.dicentra.aviator.core

enum class ServiceMethods(
    val serviceFlavour: String
) {
    GATHER("gather"),
    UPDATE("update"),
    CREATE("create"),
    DELETE("delete")
}