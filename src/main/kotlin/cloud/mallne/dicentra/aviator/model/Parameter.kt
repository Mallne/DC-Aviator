package cloud.mallne.dicentra.aviator.model

import com.google.gson.annotations.Expose

/**
 * Describes a single operation parameter.
 */
data class Parameter(
    @Expose
    val name: String,
    @Expose
    val `in`: Insides,
    @Expose
    val description: String? = null,
) {
    companion object {
        enum class Insides {
            query,
            header,
            path,
            cookie
        }
    }
}
