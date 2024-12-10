package cloud.mallne.dicentra.aviator.core.model

class PathItem {
    /**
     * returns the summary property from a PathItem instance.
     *
     * @return String summary
     */
    var summary: String? = null

    /**
     * returns the description property from a PathItem instance.
     *
     * @return String description
     */
    var description: String? = null
    private var get: Operation? = null
    private var put: Operation? = null
    private var post: Operation? = null
    private var delete: Operation? = null
    private var options: Operation? = null
    private var head: Operation? = null
    private var patch: Operation? = null
    private var trace: Operation? = null
    private var servers: List<Server>? = null
    private var parameters: List<Parameter>? = null
    private var `$ref`: String? = null
    private var extensions: MutableMap<String, Any>? = null

    fun summary(summary: String?): PathItem {
        this.summary = summary
        return this
    }

    fun description(description: String?): PathItem {
        this.description = description
        return this
    }

    /**
     * returns the get property from a PathItem instance.
     *
     * @return Operation get
     */
    fun getGet(): Operation? {
        return get
    }

    fun setGet(get: Operation?) {
        this.get = get
    }

    fun get(get: Operation?): PathItem {
        this.get = get
        return this
    }

    /**
     * returns the put property from a PathItem instance.
     *
     * @return Operation put
     */
    fun getPut(): Operation? {
        return put
    }

    fun setPut(put: Operation?) {
        this.put = put
    }

    fun put(put: Operation?): PathItem {
        this.put = put
        return this
    }

    /**
     * returns the post property from a PathItem instance.
     *
     * @return Operation post
     */
    fun getPost(): Operation? {
        return post
    }

    fun setPost(post: Operation?) {
        this.post = post
    }

    fun post(post: Operation?): PathItem {
        this.post = post
        return this
    }

    /**
     * returns the delete property from a PathItem instance.
     *
     * @return Operation delete
     */
    fun getDelete(): Operation? {
        return delete
    }

    fun setDelete(delete: Operation?) {
        this.delete = delete
    }

    fun delete(delete: Operation?): PathItem {
        this.delete = delete
        return this
    }

    /**
     * returns the options property from a PathItem instance.
     *
     * @return Operation options
     */
    fun getOptions(): Operation? {
        return options
    }

    fun setOptions(options: Operation?) {
        this.options = options
    }

    fun options(options: Operation?): PathItem {
        this.options = options
        return this
    }

    /**
     * returns the head property from a PathItem instance.
     *
     * @return Operation head
     */
    fun getHead(): Operation? {
        return head
    }

    fun setHead(head: Operation?) {
        this.head = head
    }

    fun head(head: Operation?): PathItem {
        this.head = head
        return this
    }

    /**
     * returns the patch property from a PathItem instance.
     *
     * @return Operation patch
     */
    fun getPatch(): Operation? {
        return patch
    }

    fun setPatch(patch: Operation?) {
        this.patch = patch
    }

    fun patch(patch: Operation?): PathItem {
        this.patch = patch
        return this
    }

    /**
     * returns the trace property from a PathItem instance.
     *
     * @return Operation trace
     */
    fun getTrace(): Operation? {
        return trace
    }

    fun setTrace(trace: Operation?) {
        this.trace = trace
    }

    fun trace(trace: Operation?): PathItem {
        this.trace = trace
        return this
    }

    fun readOperations(): List<Operation> {
        val allOperations: List<Operation> = ArrayList()
        if (this.get != null) {
            allOperations.add(this.get)
        }
        if (this.put != null) {
            allOperations.add(this.put)
        }
        if (this.head != null) {
            allOperations.add(this.head)
        }
        if (this.post != null) {
            allOperations.add(this.post)
        }
        if (this.delete != null) {
            allOperations.add(this.delete)
        }
        if (this.patch != null) {
            allOperations.add(this.patch)
        }
        if (this.options != null) {
            allOperations.add(this.options)
        }
        if (this.trace != null) {
            allOperations.add(this.trace)
        }

        return allOperations
    }

    fun operation(method: HttpMethod, operation: Operation?) {
        when (method) {
            HttpMethod.PATCH -> this.patch = operation
            HttpMethod.POST -> this.post = operation
            HttpMethod.PUT -> this.put = operation
            HttpMethod.GET -> this.get = operation
            HttpMethod.OPTIONS -> this.options = operation
            HttpMethod.TRACE -> this.trace = operation
            HttpMethod.HEAD -> this.head = operation
            HttpMethod.DELETE -> this.delete = operation
            else -> {}
        }
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

    fun readOperationsMap(): Map<HttpMethod, Operation> {
        val result: Map<HttpMethod, Operation> = LinkedHashMap()

        if (this.get != null) {
            result.put(HttpMethod.GET, this.get)
        }
        if (this.put != null) {
            result.put(HttpMethod.PUT, this.put)
        }
        if (this.post != null) {
            result.put(HttpMethod.POST, this.post)
        }
        if (this.delete != null) {
            result.put(HttpMethod.DELETE, this.delete)
        }
        if (this.patch != null) {
            result.put(HttpMethod.PATCH, this.patch)
        }
        if (this.head != null) {
            result.put(HttpMethod.HEAD, this.head)
        }
        if (this.options != null) {
            result.put(HttpMethod.OPTIONS, this.options)
        }
        if (this.trace != null) {
            result.put(HttpMethod.TRACE, this.trace)
        }

        return result
    }

    /**
     * returns the servers property from a PathItem instance.
     *
     * @return List&lt;Server&gt; servers
     */
    fun getServers(): List<Server>? {
        return servers
    }

    fun setServers(servers: List<Server>?) {
        this.servers = servers
    }

    fun servers(servers: List<Server>?): PathItem {
        this.servers = servers
        return this
    }

    fun addServersItem(serversItem: Server?): PathItem {
        if (this.servers == null) {
            this.servers = ArrayList()
        }
        servers.add(serversItem)
        return this
    }

    /**
     * returns the parameters property from a PathItem instance.
     *
     * @return List&lt;Parameter&gt; parameters
     */
    fun getParameters(): List<Parameter>? {
        return parameters
    }

    fun setParameters(parameters: List<Parameter>?) {
        this.parameters = parameters
    }

    fun parameters(parameters: List<Parameter>?): PathItem {
        this.parameters = parameters
        return this
    }

    fun addParametersItem(parametersItem: Parameter?): PathItem {
        if (this.parameters == null) {
            this.parameters = ArrayList()
        }
        parameters.add(parametersItem)
        return this
    }

    fun getExtensions(): Map<String, Any>? {
        return extensions
    }

    fun addExtension(name: String?, value: Any) {
        if (name == null || name.isEmpty() || !name.startsWith("x-")) {
            return
        }
        if (this.extensions == null) {
            this.extensions = java.util.LinkedHashMap()
        }
        extensions!![name] = value
    }

    @OpenAPI31
    fun addExtension31(name: String?, value: Any) {
        if (name != null && (name.startsWith("x-oas-") || name.startsWith("x-oai-"))) {
            return
        }
        addExtension(name, value)
    }

    fun setExtensions(extensions: MutableMap<String, Any>?) {
        this.extensions = extensions
    }

    fun extensions(extensions: MutableMap<String, Any>?): PathItem {
        this.extensions = extensions
        return this
    }

    /**
     * returns the ref property from a PathItem instance.
     *
     * @return String ref
     */
    fun `get$ref`(): String? {
        return `$ref`
    }

    fun `set$ref`(`$ref`: String?) {
        this.`$ref` = `$ref`
    }

    fun `$ref`(`$ref`: String?): PathItem {
        `set$ref`(`$ref`)
        return this
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is PathItem) {
            return false
        }

        val pathItem = o

        if (if (summary != null) (summary != pathItem.summary) else pathItem.summary != null) {
            return false
        }
        if (if (description != null) (description != pathItem.description) else pathItem.description != null) {
            return false
        }
        if (if (get != null) !get.equals(pathItem.get) else pathItem.get != null) {
            return false
        }
        if (if (put != null) !put.equals(pathItem.put) else pathItem.put != null) {
            return false
        }
        if (if (post != null) !post.equals(pathItem.post) else pathItem.post != null) {
            return false
        }
        if (if (delete != null) !delete.equals(pathItem.delete) else pathItem.delete != null) {
            return false
        }
        if (if (options != null) !options.equals(pathItem.options) else pathItem.options != null) {
            return false
        }
        if (if (head != null) !head.equals(pathItem.head) else pathItem.head != null) {
            return false
        }
        if (if (patch != null) !patch.equals(pathItem.patch) else pathItem.patch != null) {
            return false
        }
        if (if (trace != null) !trace.equals(pathItem.trace) else pathItem.trace != null) {
            return false
        }
        if (if (servers != null) !servers!!.equals(pathItem.servers) else pathItem.servers != null) {
            return false
        }
        if (if (parameters != null) !parameters!!.equals(pathItem.parameters) else pathItem.parameters != null) {
            return false
        }
        if (if (`$ref` != null) (`$ref` != pathItem.`$ref`) else pathItem.`$ref` != null) {
            return false
        }
        return if (extensions != null) (extensions == pathItem.extensions) else pathItem.extensions == null
    }

    override fun hashCode(): Int {
        var result = if (summary != null) summary.hashCode() else 0
        result = 31 * result + (if (description != null) description.hashCode() else 0)
        result = 31 * result + (if (get != null) get.hashCode() else 0)
        result = 31 * result + (if (put != null) put.hashCode() else 0)
        result = 31 * result + (if (post != null) post.hashCode() else 0)
        result = 31 * result + (if (delete != null) delete.hashCode() else 0)
        result = 31 * result + (if (options != null) options.hashCode() else 0)
        result = 31 * result + (if (head != null) head.hashCode() else 0)
        result = 31 * result + (if (patch != null) patch.hashCode() else 0)
        result = 31 * result + (if (trace != null) trace.hashCode() else 0)
        result = 31 * result + (if (servers != null) servers.hashCode() else 0)
        result = 31 * result + (if (parameters != null) parameters.hashCode() else 0)
        result = 31 * result + (if (`$ref` != null) `$ref`.hashCode() else 0)
        result = 31 * result + (if (extensions != null) extensions.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("class PathItem {\n")
        sb.append("    summary: ").append(toIndentedString(summary)).append("\n")
        sb.append("    description: ").append(toIndentedString(description)).append("\n")
        sb.append("    get: ").append(toIndentedString(get)).append("\n")
        sb.append("    put: ").append(toIndentedString(put)).append("\n")
        sb.append("    post: ").append(toIndentedString(post)).append("\n")
        sb.append("    delete: ").append(toIndentedString(delete)).append("\n")
        sb.append("    options: ").append(toIndentedString(options)).append("\n")
        sb.append("    head: ").append(toIndentedString(head)).append("\n")
        sb.append("    patch: ").append(toIndentedString(patch)).append("\n")
        sb.append("    trace: ").append(toIndentedString(trace)).append("\n")
        sb.append("    servers: ").append(toIndentedString(servers)).append("\n")
        sb.append("    parameters: ").append(toIndentedString(parameters)).append("\n")
        sb.append("    \$ref: ").append(toIndentedString(`$ref`)).append("\n")
        sb.append("}")
        return sb.toString()
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
}