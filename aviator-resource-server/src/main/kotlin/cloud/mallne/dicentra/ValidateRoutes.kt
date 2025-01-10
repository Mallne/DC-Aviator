package cloud.mallne.dicentra

import cloud.mallne.dicentra.aviator.core.execution.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.mock.MockConverter
import cloud.mallne.dicentra.aviator.core.mock.MockExecutionContext
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.info.Info
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.*
import kotlin.reflect.typeOf

fun Application.validateRoutes() {
    val conv = MockConverter()
    routing {
        get("version") {
            call.respond(
                AviatorExtensionSpec.SpecVersion,
                typeInfo = TypeInfo(String::class, typeOf<String>())
            )
        }
        post("parse") {
            try {
                val koas = call.receive<OpenAPI>()
                val serv = conv.build(koas)
                val outp = serv.map { (locator, service) ->
                    locator.toString() to service.requestContextful()
                }.toMap()
                call.respond(outp, typeInfo<Map<String, MockExecutionContext>>())
            } catch (e: IllegalArgumentException) {
                call.respondText(
                    text = e.localizedMessage,
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.BadRequest
                )
            } catch (e: IllegalStateException) {
                call.respondText(
                    text = e.localizedMessage,
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.BadRequest
                )
            } catch (e: RuntimeException) {
                call.respondText(
                    text = e.localizedMessage,
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.BadRequest
                )
            }
        }

        get("test") {
            val koas = OpenAPI(
                info = Info(
                    title = "OpenAPI Title",
                    version = AviatorExtensionSpec.SpecVersion,
                )
            )
            call.respond(koas, typeInfo<OpenAPI>())
        }
    }

}