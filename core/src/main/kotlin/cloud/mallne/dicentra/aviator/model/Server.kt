package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.aviator.core.model.IServer
import kotlinx.serialization.Serializable

@Serializable
data class Server(
    override val url: String, // Required: The URL of the server
    override val description: String? = null, // Optional: A short description of the server
) : IServer