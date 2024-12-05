package cloud.mallne.dicentra.aviator.core.model.oas

import cloud.mallne.dicentra.aviator.core.model.oas.parameters.Parameter
import cloud.mallne.dicentra.aviator.core.model.oas.servers.Server

/**
 * PathItem
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md.pathItemObject"
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.1.0/versions/3.1.0.md.pathItemObject"
 */
class PathItem(
    var summary: String? = null,
    var description: String? = null,
    var get: Operation? = null,
    var put: Operation? = null,
    var post: Operation? = null,
    var delete: Operation? = null,
    var options: Operation? = null,
    var head: Operation? = null,
    var patch: Operation? = null,
    var trace: Operation? = null,
    var servers: MutableList<Server>? = null,
    var parameters: MutableList<Parameter>? = null,
    var `$ref`: String? = null,
    var extensions: MutableMap<String, Any>? = null
) {

    fun readOperations(): MutableList<Operation> {
        val allOperations: MutableList<Operation> = ArrayList<Operation>()
        if (this.get != null) {
            allOperations.add(this.get!!)
        }
        if (this.put != null) {
            allOperations.add(this.put!!)
        }
        if (this.head != null) {
            allOperations.add(this.head!!)
        }
        if (this.post != null) {
            allOperations.add(this.post!!)
        }
        if (this.delete != null) {
            allOperations.add(this.delete!!)
        }
        if (this.patch != null) {
            allOperations.add(this.patch!!)
        }
        if (this.options != null) {
            allOperations.add(this.options!!)
        }
        if (this.trace != null) {
            allOperations.add(this.trace!!)
        }

        return allOperations
    }

    fun operation(method: HttpMethod, operation: Operation) {
        when (method) {
            HttpMethod.PATCH -> this.patch = operation
            HttpMethod.POST -> this.post = operation
            HttpMethod.PUT -> this.put = operation
            HttpMethod.GET -> this.get = operation
            HttpMethod.OPTIONS -> this.options = operation
            HttpMethod.TRACE -> this.trace = operation
            HttpMethod.HEAD -> this.head = operation
            HttpMethod.DELETE -> this.delete = operation
        }
    }

    fun readOperationsMap(): MutableMap<HttpMethod, Operation> {
        val result: MutableMap<HttpMethod, Operation> = LinkedHashMap<HttpMethod, Operation>()

        if (this.get != null) {
            result.put(HttpMethod.GET, this.get!!)
        }
        if (this.put != null) {
            result.put(HttpMethod.PUT, this.put!!)
        }
        if (this.post != null) {
            result.put(HttpMethod.POST, this.post!!)
        }
        if (this.delete != null) {
            result.put(HttpMethod.DELETE, this.delete!!)
        }
        if (this.patch != null) {
            result.put(HttpMethod.PATCH, this.patch!!)
        }
        if (this.head != null) {
            result.put(HttpMethod.HEAD, this.head!!)
        }
        if (this.options != null) {
            result.put(HttpMethod.OPTIONS, this.options!!)
        }
        if (this.trace != null) {
            result.put(HttpMethod.TRACE, this.trace!!)
        }

        return result
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is PathItem) {
            return false
        }

        if (summary != o.summary) {
            return false
        }
        if (description != o.description) {
            return false
        }
        if (get != o.get) {
            return false
        }
        if (put != o.put) {
            return false
        }
        if (post != o.post) {
            return false
        }
        if (delete != o.delete) {
            return false
        }
        if (options != o.options) {
            return false
        }
        if (head != o.head) {
            return false
        }
        if (patch != o.patch) {
            return false
        }
        if (trace != o.trace) {
            return false
        }
        if (servers != o.servers) {
            return false
        }
        if (parameters != o.parameters) {
            return false
        }
        if (`$ref` != o.`$ref`) {
            return false
        }
        return extensions == o.extensions
    }

    override fun hashCode(): Int {
        var result = summary?.hashCode() ?: 0
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (get?.hashCode() ?: 0)
        result = 31 * result + (put?.hashCode() ?: 0)
        result = 31 * result + (post?.hashCode() ?: 0)
        result = 31 * result + (delete?.hashCode() ?: 0)
        result = 31 * result + (options?.hashCode() ?: 0)
        result = 31 * result + (head?.hashCode() ?: 0)
        result = 31 * result + (patch?.hashCode() ?: 0)
        result = 31 * result + (trace?.hashCode() ?: 0)
        result = 31 * result + (servers?.hashCode() ?: 0)
        result = 31 * result + (parameters?.hashCode() ?: 0)
        result = 31 * result + (`$ref`?.hashCode() ?: 0)
        result = 31 * result + (extensions?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        val sb = "class PathItem {\n" +
                "    summary: " + toIndentedString(summary) + "\n" +
                "    description: " + toIndentedString(description) + "\n" +
                "    get: " + toIndentedString(get) + "\n" +
                "    put: " + toIndentedString(put) + "\n" +
                "    post: " + toIndentedString(post) + "\n" +
                "    delete: " + toIndentedString(delete) + "\n" +
                "    options: " + toIndentedString(options) + "\n" +
                "    head: " + toIndentedString(head) + "\n" +
                "    patch: " + toIndentedString(patch) + "\n" +
                "    trace: " + toIndentedString(trace) + "\n" +
                "    servers: " + toIndentedString(servers) + "\n" +
                "    parameters: " + toIndentedString(parameters) + "\n" +
                "    \$ref: " + toIndentedString(`$ref`) + "\n" +
                "}"
        return sb
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private fun toIndentedString(o: Any?): String {
        if (o == null) {
            return "null"
        }
        return o.toString().replace("\n", "\n    ")
    }

    enum class HttpMethod {
        POST,
        GET,
        PUT,
        PATCH,
        DELETE,
        HEAD,
        OPTIONS,
        TRACE
    }
}

