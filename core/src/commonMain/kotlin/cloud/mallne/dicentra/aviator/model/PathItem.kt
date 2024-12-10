package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.model.IPathItem
import kotlinx.serialization.Serializable

/**
 * Describes the operations available on a single path.
 */
@Serializable
data class PathItem(
    override val info: Info? = null,
    override val get: Operation? = null,
    override val put: Operation? = null,
    override val post: Operation? = null,
    override val delete: Operation? = null,
    override val parameters: List<Parameter>? = null,
) : IPathItem
