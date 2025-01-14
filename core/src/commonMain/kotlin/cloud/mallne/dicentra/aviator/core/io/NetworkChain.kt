package cloud.mallne.dicentra.aviator.core.io

import kotlinx.serialization.Serializable

@Serializable
open class NetworkChain<Req : NetworkRequest<Header>, Res : NetworkResponse<Header>, Header : NetworkHeader>(
    open val url: String,
    open var request: Req? = null,
    open var response: Res? = null,
)