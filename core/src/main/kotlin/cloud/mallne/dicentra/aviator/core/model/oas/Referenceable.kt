package cloud.mallne.dicentra.aviator.core.model.oas

interface Referenceable {
    var `$ref`: String?
    var summary: String?
    var description: String?
}