package cloud.mallne.dicentra.aviator.client.fetch

import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.RawRequestBody
import cloud.mallne.dicentra.aviator.core.io.adapter.request.FormAdapter
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import io.ktor.http.*
import io.ktor.openapi.ParameterType
import io.ktor.util.date.GMTDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("UnsafeCastFromDynamic")
class FetchStagedExecutor<O : @Serializable Any, B : @Serializable Any> :
    StagedExecutor<FetchExecutionContext<O, B>, O, B> {

    override suspend fun onPathMatching(context: FetchExecutionContext<O, B>) {
        context.networkChain.addAll(
            AviatorServiceUtils.catchPaths(context.dataHolder, context.requestParams).map(::NetworkChain)
        )
    }

    override suspend fun onFormingRequest(context: FetchExecutionContext<O, B>) {
        val route = context.dataHolder.route
        val defaultContentType = route.body.types.keys.firstOrNull()
        context.networkChain.forEach { chain ->
            val raw = context.body as? RawRequestBody
            val bodyAndType: Pair<NetworkBody, ContentType?> = if (raw != null) {
                NetworkBody.Empty to ContentType.parse(raw.contentType)
            } else if (context.body != null && context.bodyClazz != null) {
                val adapter = route.body.mapNotNull { bodyType ->
                    context.adapterFor(bodyType.key)?.let { it to bodyType.key }
                }.firstOrNull()
                adapter?.let { selected ->
                    val body: NetworkBody? = selected.first.buildBody(context.body, context)
                    body?.let { Pair<NetworkBody, ContentType?>(it, selected.second) }
                }
                    ?: (NetworkBody.Empty to defaultContentType)
            } else {
                FormAdapter.buildBody(context)?.let { Pair<NetworkBody, ContentType?>(it.first, it.second) }
                    ?: (NetworkBody.Empty to defaultContentType)
            }
            val outgoing = bodyAndType.first
            val contentType = bodyAndType.second

            val url = URLBuilder(chain.url)
            route.parameter.filter { it.`in` == ParameterType.query }.forEach { parameter ->
                context.requestParams[parameter.name]?.let { value ->
                    parameterValues(value).forEach { url.parameters.append(parameter.name, it) }
                }
            }
            val requestHeaders = mutableMapOf<String, RequestParameter>()
            route.parameter.filter { it.`in` == ParameterType.header }.forEach { parameter ->
                context.requestParams[parameter.name]?.let { requestHeaders[parameter.name] = it }
            }
            val multipart = contentType?.match(ContentType.MultiPart.FormData) == true && outgoing is NetworkBody.Form
            if (!multipart && contentType != null && requestHeaders.keys.none { it.equals("Content-Type", true) }) {
                requestHeaders["Content-Type"] = RequestParameter.Single(contentType.toString())
            }
            chain.request = FetchRequest(
                method = route.method,
                url = url.build(),
                outgoingContent = outgoing,
                headers = FetchHeaders(RequestParameters(requestHeaders)),
                contentType = contentType,
            )
        }
    }

    override suspend fun onRequesting(context: FetchExecutionContext<O, B>) {
        val pending = context.networkChain.filter { it.request != null && it.response == null }
        for (chain in pending) {
            val request = chain.request ?: continue
            val init: dynamic = js("({})")
            init.method = request.method.value
            val headers: dynamic = js("new Headers()")
            request.headers.values.forEach { (name, value) -> headers.append(name, value.toString()) }
            init.headers = headers

            val raw = context.body as? RawRequestBody
            val outgoing = request.outgoingContent
            when {
                raw != null -> init.body = raw.bytes.toUint8Array()
                outgoing is NetworkBody.Form -> {
                    if (request.contentType?.match(ContentType.MultiPart.FormData) == true) {
                        val form: dynamic = js("new FormData()")
                        outgoing.formData.forEach { (key, value) -> form.append(key, value) }
                        init.body = form
                    } else {
                        val params: dynamic = js("new URLSearchParams()")
                        outgoing.formData.forEach { (key, value) -> params.append(key, value) }
                        init.body = params
                    }
                }
                else -> outgoing.fetchRepresentation(context)?.let { init.body = it }
            }

            val response = awaitFetch(context.dataHolder.fetcher(request.url.toString(), init))
            val buffer = awaitFetch(response.arrayBuffer())
            val bytes = arrayBufferToByteArray(buffer)
            check(bytes.size <= context.dataHolder.maxResponseBytes) {
                "Aviator response exceeded the configured maximum of ${context.dataHolder.maxResponseBytes} bytes"
            }
            val responseHeaders = FetchHeaders()
            val contentTypeValue: String? = response.headers.get("content-type") as String?
            val contentType = contentTypeValue?.let(ContentType::parse)
                ?: routeResponseContentType(context, response.status as Int)
            response.headers.forEach { value: String, key: String ->
                responseHeaders.values[key] = RequestParameter.Single(value)
            }
            chain.response = FetchResponse(
                status = HttpStatusCode.fromValue(response.status as Int),
                content = bytes,
                time = GMTDate(),
                headers = responseHeaders,
                contentType = contentType,
            )
            if ((response.status as Int) < 400) break
        }
    }

    override suspend fun onPaintingResponse(context: FetchExecutionContext<O, B>) {
        val successful = context.networkChain.find { (it.response?.status?.value ?: 500) < 400 }
        context.result = try {
            val response = successful?.response
            val deserializer = response?.let { context.deserializerFor(it.contentType) }
            response?.content?.let { deserializer?.deserialize(it, context) }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

private fun routeResponseContentType(context: FetchExecutionContext<*, *>, status: Int): ContentType =
    context.dataHolder.route.returnType[HttpStatusCode.fromValue(status)]?.toList()?.firstOrNull()?.first ?: ContentType.Any

private suspend fun awaitFetch(promise: dynamic): dynamic = suspendCoroutine { continuation ->
    promise.then(
        { value: dynamic -> continuation.resume(value) },
        { error: dynamic -> continuation.resumeWithException(Throwable(error.toString())) },
    )
}

private fun ByteArray.toUint8Array(): dynamic {
    val values = Array(size) { index -> this[index].toInt() and 0xff }
    return js("new Uint8Array(values)")
}

private fun arrayBufferToByteArray(buffer: dynamic): ByteArray {
    val data: dynamic = js("new Uint8Array(buffer)")
    return ByteArray(data.length as Int) { index -> (data[index] as Int).toByte() }
}

private fun parameterValues(parameter: RequestParameter): List<String> = when (parameter) {
    is RequestParameter.Single<*> -> listOf(parameter.toString())
    is RequestParameter.Multi<*> -> parameter.toStringList()
}
