package cloud.mallne.dicentra

import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.info.Info
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.*
import kotlin.reflect.typeOf

fun Application.validateRoutes() {
    routing {
        get("version") {
            call.respond(
                AviatorExtensionSpec.SpecVersion,
                typeInfo = TypeInfo(String::class, typeOf<String>())
            )
        }
        post("parse") {
            val koas = call.receive<OpenAPI>()
            call.respond(koas, typeInfo<OpenAPI>())
        }

        get("test") {
            val koas: OpenAPI = OpenAPI(
                info = Info(
                    title = "OpenAPI Title",
                    version = AviatorExtensionSpec.SpecVersion,
                )
            )
            call.respond(koas, typeInfo<OpenAPI>())
        }
    }

}