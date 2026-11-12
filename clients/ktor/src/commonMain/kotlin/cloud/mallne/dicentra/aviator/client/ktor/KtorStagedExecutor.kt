package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.client.ktor.io.AvKtorRequest
import cloud.mallne.dicentra.aviator.client.ktor.io.AvKtorResponse
import cloud.mallne.dicentra.aviator.client.ktor.io.manualPipeline
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.execution.RequestParameter
import cloud.mallne.dicentra.aviator.core.execution.LocatedParameterKey
import cloud.mallne.dicentra.aviator.core.execution.LocatedRequestParameters
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import cloud.mallne.dicentra.aviator.core.execution.controlledAviatorCall
import cloud.mallne.dicentra.aviator.model.AviatorServiceUtils
import cloud.mallne.dicentra.aviator.core.InternalAviatorAPI
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.RawRequestBody
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.adapter.request.FormAdapter
import cloud.mallne.dicentra.aviator.koas.typed.TemplateParser.parsePath
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.openapi.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.currentCoroutineContext

@OptIn(InternalAviatorAPI::class)
class KtorStagedExecutor<O : @Serializable Any, B : @Serializable Any> :
    StagedExecutor<KtorExecutionContext<O, B>, O, B> {

    override suspend fun onPathMatching(context: KtorExecutionContext<O, B>) {
        val pathParams = RequestParameters(context.requestParams.value.toMutableMap())
        val located = currentCoroutineContext()[LocatedRequestParameters]
        located?.values?.keys?.forEach { pathParams.remove(it.name) }
        located?.values?.forEach { (key, value) ->
            if (key.location == ParameterType.path) {
                pathParams[key.name] = when (value) {
                    is RequestParameter.Single<*> -> RequestParameter.Single(value.toString().encodeURLPathPart())
                    is RequestParameter.Multi<*> -> RequestParameter.Multi(value.toStringList().map { it.encodeURLPathPart() })
                }
            }
        }
        val pathRoute = if (located == null) context.requestRoute else context.requestRoute.copy(
            parameter = context.requestRoute.parameter.filter { it.`in` == ParameterType.path } +
                context.requestRoute.parameter.filter { it.`in` != ParameterType.path && it.`in` != ParameterType.query },
        )
        context.networkChain.addAll(AviatorServiceUtils.catchPaths(context.dataHolder, pathParams, pathRoute).map {
            context.log(KtorLoggingIds.TRACE_CREATE_CHAIN) { trace("Creating Network Chain with target $it") }
            NetworkChain(it)
        })
    }

    override suspend fun onFormingRequest(context: KtorExecutionContext<O, B>) {
        val route = context.requestRoute
        val locatedParameters = currentCoroutineContext()[LocatedRequestParameters]
        val located = locatedParameters?.values.orEmpty()
        fun parameterValue(name: String, location: ParameterType): RequestParameter? =
            located[LocatedParameterKey(name, location)]
                ?: if (located.keys.any { it.name == name }) null else context.requestParams[name]
        val fallbackCT = route.body.types.keys.firstOrNull()
        context.networkChain.forEach { net ->
            val raw = context.body as? RawRequestBody
            val body = if (raw != null) {
                NetworkBody.Empty to ContentType.parse(raw.contentType)
            } else if (context.body != null && context.bodyClazz != null) {
                val adapter =
                    route.body.mapNotNull { bdy -> context.adapterFor(bdy.key)?.let { it to bdy.key } }.firstOrNull()
                adapter?.let { adpt -> adpt.first.buildBody(context.body, context)?.let { it to adpt.second } }
                    ?: (NetworkBody.Empty to fallbackCT)
            } else {
                FormAdapter.buildBody(context) ?: (NetworkBody.Empty to fallbackCT)
            }

            val headers = route.parameter.filter { it.`in` == ParameterType.header }.mapNotNull { parameter ->
                parameterValue(parameter.name, ParameterType.header)?.let { parameter.name to it }
            }.toMap()
            val requestUrl = URLBuilder(net.url).apply {
                if (locatedParameters != null) {
                    val queryParameters = route.parameter.filter { it.`in` == ParameterType.query }
                    val queryValues = queryParameters.mapNotNull { parameter ->
                        val value = parameterValue(parameter.name, ParameterType.query)
                        value?.let { parameter.name to it }
                    }.toMap()
                    val encodedValues = RequestParameters(queryValues.toMutableMap()).toStringList()
                        .mapValues { (_, values) -> values.map { it.encodeURLQueryComponent(encodeFull = true) } }
                    val query = route.copy(path = "", parameter = queryParameters)
                        .parsePath(encodedValues)
                    if (query.isNotEmpty()) parameters.appendAll(parseQueryString(query.removePrefix("?")))
                }
            }.build()

            net.request = AvKtorRequest(
                method = route.method,
                url = requestUrl,
                outgoingContent = body.first,
                headers = object : NetworkHeader {
                    override var values: RequestParameters = RequestParameters(headers.toMutableMap())
                },
                contentType = body.second,
            )
        }
    }

    override suspend fun onRequesting(context: KtorExecutionContext<O, B>) {
        val chain = context.networkChain.filter { it.request != null && it.response == null }
        var attemptIndex = 0
        chain.manualPipeline { net, next ->
            context.log(KtorLoggingIds.TRACE_CREATE_REQUEST) { trace("Creating Network Request for ${net.url}") }
            val (response, failed) = controlledAviatorCall(retry = attemptIndex++ > 0, call = {
            val resp = context.dataHolder.client.request {
                url(net.request!!.url)
                val raw = context.body as? RawRequestBody
                if (raw != null) {
                    // Preserve serialized bytes even when a provider plugin forms its own body.
                    setBody(object : OutgoingContent.ByteArrayContent() {
                        override val contentType = ContentType.parse(raw.contentType)
                        override val contentLength = raw.bytes.size.toLong()
                        override fun bytes(): ByteArray = raw.bytes
                    })
                } else when (net.request?.outgoingContent) {
                    is NetworkBody.Form -> {
                        if (ContentType.Application.FormUrlEncoded.match(
                                net.request?.contentType ?: ContentType.Text.Plain
                            )
                        ) {
                            setBody(
                                FormDataContent(
                                    parameters {
                                        val fd = (net.request?.outgoingContent as NetworkBody.Form).formData
                                        fd.forEach {
                                            append(it.first, it.second)
                                        }
                                    }
                                ))
                        } else if (ContentType.MultiPart.FormData.match(
                                net.request?.contentType ?: ContentType.Text.Plain
                            )
                        ) {
                            setBody(
                                MultiPartFormDataContent(
                                    formData {
                                        val fd = (net.request?.outgoingContent as NetworkBody.Form).formData
                                        fd.forEach {
                                            append(it.first, it.second)
                                        }
                                    }
                                ))
                        }
                    }

                    else -> {
                        val text = net.request!!.outgoingContent.fetchRepresentation(context)
                        if (text != null && net.request?.contentType != null) {
                            setBody(TextContent(text, net.request!!.contentType!!))
                            contentType(net.request!!.contentType!!)
                        } else if (net.request!!.outgoingContent !is NetworkBody.Empty) {
                            context.log(KtorLoggingIds.WARN_NO_BODY_FINALIZATION) {
                                warn("A Body could not be stringified!")
                            }
                        }
                    }
                }
                method = net.request!!.method
                net.request!!.headers.values.forEach { (k, v) ->
                    if (raw == null || (!k.equals(HttpHeaders.ContentType, ignoreCase = true) &&
                                !k.equals(HttpHeaders.ContentLength, ignoreCase = true))) {
                        when (v) {
                            is RequestParameter.Single<*> -> header(k, v.toString())
                            is RequestParameter.Multi<*> -> v.toStringList().forEach { header(k, it) }
                        }
                    }
                }
            }
            val contentTypeRecieved = resp.headers["Content-Type"]?.let { ContentType.parse(it) }
            val content = resp.bodyAsChannel()
                .readBuffer(context.dataHolder.maxResponseBytes + 1)
                .readByteArray()
            check(content.size.toLong() <= context.dataHolder.maxResponseBytes) {
                "Aviator response exceeded the configured maximum of ${context.dataHolder.maxResponseBytes} bytes"
            }
            val response = AvKtorResponse(
                ktorPrimitive = resp,
                content = content,
                contentType = contentTypeRecieved ?: context.dataHolder.route.returnType[resp.status]?.toList()
                    ?.first()?.first ?: ContentType.Any,
            )
            if (resp.status.value >= 400) {
                val text = resp.bodyAsText()
                context.log(KtorLoggingIds.WARN_REQUEST_FAILED) {
                    warn("Request {${net.request?.method}: ${net.request?.url}} failed with status ${resp.status.value}: $text")
                }
            }
            response to (resp.status.value >= 400)
            })
            net.response = response
            if (failed) next()
        }

        val successful = context.networkChain.find { (it.response?.status?.value ?: 500) >= 400 }
        if (successful != null) {
            context.log(KtorLoggingIds.WARN_NO_RESPONSES) { warn("No responses returned ok") }
        }
    }

    override suspend fun onPaintingResponse(context: KtorExecutionContext<O, B>) {
        val successful = context.networkChain.find { (it.response?.status?.value ?: 500) < 400 }
        context.result = try {
            val deserializer = successful?.response?.let { context.deserializerFor(it.contentType) }
            successful?.response?.content?.let { deserializer?.deserialize(it, context) }
        } catch (e: SerializationException) {
            context.log(KtorLoggingIds.WARN_OPTIONAL_FINALIZATION) {
                warn(
                    "Serialization exception, maybe a Plugin will Handle the Object",
                    e
                )
            }
            null
        } catch (e: IllegalArgumentException) {
            context.log(KtorLoggingIds.WARN_OPTIONAL_FINALIZATION) {
                warn(
                    "There seems to be Parsing Problems, maybe a Plugin will handle this",
                    e
                )
            }
            null
        }
    }

    override suspend fun onFinished(context: KtorExecutionContext<O, B>) {
        super.onFinished(context)
    }
}
