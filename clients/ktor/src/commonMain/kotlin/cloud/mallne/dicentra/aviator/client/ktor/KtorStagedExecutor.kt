package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.client.ktor.io.AvKtorRequest
import cloud.mallne.dicentra.aviator.client.ktor.io.AvKtorResponse
import cloud.mallne.dicentra.aviator.client.ktor.io.manualPipeline
import cloud.mallne.dicentra.aviator.core.execution.RequestParameters
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import cloud.mallne.dicentra.aviator.core.io.adapter.request.FormAdapter
import cloud.mallne.dicentra.aviator.koas.parameters.Parameter
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

class KtorStagedExecutor<O : @Serializable Any, B : @Serializable Any> :
    StagedExecutor<KtorExecutionContext<O, B>, O, B> {

    override suspend fun onPathMatching(context: KtorExecutionContext<O, B>) {
        context.networkChain.addAll(context.dataHolder.catchPaths(context.requestParams).map {
            context.log(KtorLoggingIds.TRACE_CREATE_CHAIN) { trace("Creating Network Chain with target $it") }
            NetworkChain(it)
        })
    }

    override suspend fun onFormingRequest(context: KtorExecutionContext<O, B>) {
        val route = context.dataHolder.route
        val fallbackCT = route.body.types.keys.firstOrNull()
        context.networkChain.forEach { net ->
            val body = if (context.body != null && context.bodyClazz != null) {
                val adapter =
                    route.body.mapNotNull { bdy -> context.adapterFor(bdy.key)?.let { it to bdy.key } }.firstOrNull()
                adapter?.let { adpt -> adpt.first.buildBody(context.body, context)?.let { it to adpt.second } }
                    ?: (NetworkBody.Empty to fallbackCT)
            } else {
                FormAdapter.buildBody(context) ?: (NetworkBody.Empty to fallbackCT)
            }

            val headers = route.parameter.filter { it.input == Parameter.Input.Header }.mapNotNull { parameter ->
                context.requestParams[parameter.name]?.let { parameter.name to it }
            }.toMap()

            net.request = AvKtorRequest(
                method = route.method,
                url = Url(net.url),
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
        chain.manualPipeline { net, next ->
            context.log(KtorLoggingIds.TRACE_CREATE_REQUEST) { trace("Creating Network Request for ${net.url}") }
            val resp = context.dataHolder.client.request {
                url(net.url)
                when (net.request?.outgoingContent) {
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
                net.request!!.headers.values.forEach { (k, v) -> header(k, v.toString()) }
            }
            val contentTypeRecieved = resp.headers["Content-Type"]?.let { ContentType.parse(it) }
            val response = AvKtorResponse(
                ktorPrimitive = resp,
                content = resp.bodyAsBytes(),
                contentType = contentTypeRecieved ?: context.dataHolder.route.returnType[resp.status]?.toList()
                    ?.first()?.first ?: ContentType.Any,
            )
            net.response = response

            if (resp.status.value >= 400) {
                next()
            }
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