package cloud.mallne.dicentra.aviator.testserver

import cloud.mallne.dicentra.aviator.core.AviatorExtensionSpec
import cloud.mallne.dicentra.aviator.core.mock.MockConverter
import cloud.mallne.dicentra.aviator.core.mock.MockExecutionContext
import cloud.mallne.dicentra.aviator.exceptions.AviatorValidationException
import cloud.mallne.dicentra.aviator.exceptions.ServiceException
import cloud.mallne.dicentra.aviator.koas.OpenAPI
import cloud.mallne.dicentra.aviator.koas.exceptions.ExplicitTypeException
import cloud.mallne.dicentra.aviator.koas.exceptions.IngestArgumentViolation
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPIConstraintViolation
import cloud.mallne.dicentra.aviator.koas.exceptions.OpenAPISerializationException
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
            suspend fun catchSendBadRequest(e: Throwable) {
                call.respondText(
                    text = e.message ?: e.toString(),
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.BadRequest
                )
            }
            try {
                val koas = call.receive<OpenAPI>()
                val serv = conv.build(koas)
                val outp = serv.map { (locator, service) ->
                    locator.toString() to service.requestContextful()
                }.toMap()
                call.respond(outp, typeInfo<Map<String, MockExecutionContext>>())
            } catch (e: ExplicitTypeException) {
                catchSendBadRequest(e)
            } catch (e: IngestArgumentViolation) {
                catchSendBadRequest(e)
            } catch (e: OpenAPISerializationException) {
                catchSendBadRequest(e)
            } catch (e: OpenAPIConstraintViolation) {
                catchSendBadRequest(e)
            } catch (e: ServiceException) {
                catchSendBadRequest(e)
            } catch (e: AviatorValidationException) {
                catchSendBadRequest(e)
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