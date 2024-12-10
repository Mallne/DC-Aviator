package cloud.mallne.dicentra.aviator.core.model

/**
 * Describes the operations available on a single path.
 */
interface IPathItem {
    val info: IInfo?
    val get: IOperation?
    val put: IOperation?
    val post: IOperation?
    val delete: IOperation?
    val parameters: List<IParameter>?

    fun getOperations(method: RequestMethod): IOperation? {
        return when (method) {
            RequestMethod.GET -> get
            RequestMethod.PUT -> put
            RequestMethod.POST -> post
            RequestMethod.DELETE -> delete
            else -> {
                throw IllegalArgumentException("Unknown method: $method")
            }
        }
    }

    fun getOperations(): Map<RequestMethod, IOperation?> {
        return mapOf(
            RequestMethod.GET to get,
            RequestMethod.PUT to put,
            RequestMethod.POST to post,
            RequestMethod.DELETE to delete,
        )
    }
}
