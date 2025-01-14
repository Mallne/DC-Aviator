package cloud.mallne.dicentra.aviator.testserver

import cloud.mallne.dicentra.aviator.testserver.helper.toBooleanish
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.resources.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun Application.configureHTTP() {
    install(Compression)
    if (environment.config.propertyOrNull("dicentra.behind-proxy")?.getString()?.toBooleanish() == true) {
        install(ForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
        install(XForwardedHeaders) // WARNING: for security, do not include this if not behind a reverse proxy
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
                explicitNulls = false
            }
        )
    }
    install(Resources)
    install(RateLimit) {
        global {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
        }
    }
}
