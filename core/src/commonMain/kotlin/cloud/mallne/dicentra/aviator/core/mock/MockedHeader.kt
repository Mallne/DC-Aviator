package cloud.mallne.dicentra.aviator.core.mock

import cloud.mallne.dicentra.aviator.core.io.NetworkHeader
import kotlinx.serialization.Serializable

@Serializable
data class MockedHeader(
    override val values: Map<String, List<String>> = emptyMap()
) : NetworkHeader