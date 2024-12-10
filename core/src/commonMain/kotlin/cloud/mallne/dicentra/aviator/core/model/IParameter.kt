package cloud.mallne.dicentra.aviator.core.model

/**
 * Describes a single operation parameter.
 */
interface IParameter {
    val name: String
    val `in`: Insides
    val description: String?
}
