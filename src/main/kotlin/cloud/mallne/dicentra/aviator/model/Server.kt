package cloud.mallne.dicentra.aviator.model

import com.google.gson.annotations.Expose

data class Server(
    @Expose
    val url: String, // Required: The URL of the server
    @Expose
    val description: String? = null, // Optional: A short description of the server
)