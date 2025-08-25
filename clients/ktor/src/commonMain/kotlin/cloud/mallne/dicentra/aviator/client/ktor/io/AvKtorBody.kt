package cloud.mallne.dicentra.aviator.client.ktor.io

import cloud.mallne.dicentra.aviator.core.io.NetworkBody
import kotlinx.serialization.json.JsonElement

class AvKtorJsonBody(override var json: JsonElement) : NetworkBody.Json
class AvKtorFormBody(override var formData: List<Pair<String, String>>) : NetworkBody.Form