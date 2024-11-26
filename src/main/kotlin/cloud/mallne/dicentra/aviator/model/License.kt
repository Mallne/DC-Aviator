package cloud.mallne.dicentra.aviator.model

import com.google.gson.annotations.Expose

data class License(
    @Expose
    val name: String,
    @Expose
    val url: String? = null
)