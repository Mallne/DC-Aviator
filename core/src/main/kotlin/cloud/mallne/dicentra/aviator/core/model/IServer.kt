package cloud.mallne.dicentra.aviator.core.model

interface IServer {
    val url: String // Required: The URL of the server
    val description: String? // Optional: A short description of the server
}