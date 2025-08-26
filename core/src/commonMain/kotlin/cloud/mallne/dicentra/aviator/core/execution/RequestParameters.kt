package cloud.mallne.dicentra.aviator.core.execution

import kotlinx.serialization.Serializable

@Serializable
data class RequestParameters(
    val value: MutableMap<String, RequestParameter> = mutableMapOf(),
) : MutableMap<String, RequestParameter> by value {

    fun toStringList(): Map<String, List<String>> {
        return value.map { (template, param) ->
            template to when (param) {
            is RequestParameter.Single<*> -> listOf(param.toString())
            is RequestParameter.Multi<*> -> param.toStringList()
        } }.toMap()
    }
}

sealed interface RequestParameter {
    override fun toString(): String
    val size: Int
    fun isEmpty(): Boolean


    data class Multi<T>(
        val value: List<T>,
        val stringify: (T) -> String = { it.toString() },
    ) : RequestParameter, List<T> by value {
        override fun toString(): String = value.joinToString(separator = ",") { stringify(it) }
        fun toStringList(): List<String> = value.map { stringify(it) }
    }

    data class Single<T>(
        val value: T,
        val stringify: (T) -> String = { it.toString() },
    ) : RequestParameter {
        override fun toString(): String = stringify(value)
        override val size: Int
            get() = 1

        override fun isEmpty(): Boolean = false
    }
}