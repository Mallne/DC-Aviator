package cloud.mallne.dicentra

import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.validateRoutes() {
    routing {
        get("/version") {
            call.respond()
        }
    }

}