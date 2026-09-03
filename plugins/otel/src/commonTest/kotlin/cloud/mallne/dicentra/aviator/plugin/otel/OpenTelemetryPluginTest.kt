package cloud.mallne.dicentra.aviator.plugin.otel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenTelemetryPluginTest {

    @Test
    fun pluginIdentity() {
        assertEquals("DC-AV-OpenTelemetry", OpenTelemetryPlugin.identity)
    }

    @Test
    fun installRequiresOpenTelemetryInstance() {
        assertFailsWith<IllegalArgumentException> {
            OpenTelemetryPlugin.install { }
        }
    }
}
