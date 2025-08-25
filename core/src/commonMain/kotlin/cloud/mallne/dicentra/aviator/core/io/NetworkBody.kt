package cloud.mallne.dicentra.aviator.core.io

import kotlinx.serialization.json.JsonElement

sealed interface NetworkBody {
    object Empty : NetworkBody
    interface Json : NetworkBody {
        var json: JsonElement
    }
    interface Form : NetworkBody {
        var formData: List<Pair<String, String>>
    }
}