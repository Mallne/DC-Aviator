package cloud.mallne.dicentra

import cloud.mallne.dicentra.helper.toBooleanish
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.resources.*

fun Application.configureHTTP() {
    install(Compression)
    if (environment.config.propertyOrNull("dicentra.behind-proxy")?.getString()?.toBooleanish() == true) {
        install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
        install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    }
    install(ContentNegotiation) {
        json()
    }
    install(Resources)
}
