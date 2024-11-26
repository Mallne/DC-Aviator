package cloud.mallne.dicentra.aviator.model

import com.google.gson.annotations.Expose

data class Info(
    @Expose
    val title: String,
    @Expose
    val description: String? = null,
    @Expose
    val license: License? = null,
    @Expose
    val version: String
)