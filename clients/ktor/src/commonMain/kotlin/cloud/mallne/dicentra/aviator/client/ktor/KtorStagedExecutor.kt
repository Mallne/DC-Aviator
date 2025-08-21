package cloud.mallne.dicentra.aviator.client.ktor

import cloud.mallne.dicentra.aviator.client.ktor.io.AvKtorRequest
import cloud.mallne.dicentra.aviator.client.ktor.io.AvKtorResponse
import cloud.mallne.dicentra.aviator.client.ktor.io.manualPipeline
import cloud.mallne.dicentra.aviator.core.execution.StagedExecutor
import cloud.mallne.dicentra.aviator.core.io.NetworkChain
import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.reflect.*
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
        context.networkChain.forEach { net ->
            net.request = AvKtorRequest(
                method = route.method,
                url = Url(net.url),
                outgoingContent = if (context.body != null && context.bodyClazz != null) {
                    context.dataHolder.json.encodeToJsonElement(
                        context.bodyClazz!!.third,
                        context.body!!
                    )
                } else {
                    null
                },
                headers = object : NetworkHeader {
                    override var values: MutableMap<String, List<String>> = mutableMapOf()
                }
            )
        }
    }

    private fun List<String>.unpack(): String {
        return if (this.size == 1) {
            this.first()
        } else {
            this.joinToString(",")
        }
    }

    override suspend fun onRequesting(context: KtorExecutionContext<O, B>) {
        val chain = context.networkChain.filter { it.request != null && it.response == null }
        chain.manualPipeline { net, next ->
            context.log(KtorLoggingIds.TRACE_CREATE_REQUEST) { trace("Creating Network Request for ${net.url}") }
            val resp = context.dataHolder.client.request {
                url(net.url)
                if (context.body != null && net.request?.outgoingContent != null && context.bodyClazz != null) {
                    setBody(
                        context.body,
                        TypeInfo(context.bodyClazz!!.first, context.bodyClazz!!.second)
                    )
                }
                method = net.request!!.method
                net.request!!.headers.values.forEach { (k, v) -> header(k, v.unpack()) }
            }

            val response = AvKtorResponse(
                ktorPrimitive = resp,
                content = resp.body(),
            )
            net.response = response

            if (resp.status.value >= 400) {
                next()
            }
        }

        val successful = context.networkChain.find { (it.response?.status?.value ?: 500) > 400 }
        if (successful != null) {
            context.log(KtorLoggingIds.WARN_NO_RESPONSES) { warn("No responses returned ok") }
        }
    }

    override suspend fun onPaintingResponse(context: KtorExecutionContext<O, B>) {
        val successful = context.networkChain.find { (it.response?.status?.value ?: 500) < 400 }
        context.result = try {
            successful?.response?.parseBody(context.outputClazz.third, context.dataHolder.json)
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