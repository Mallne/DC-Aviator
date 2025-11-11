package cloud.mallne.dicentra.aviator.plugin.adapter.xml

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import kotlinx.serialization.*
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.serialization.XML

object XmlAdapter : AviatorPlugin<XmlAdapterPluginConfig> {
    override val identity: String = "adapter:JSON"
    override fun install(config: XmlAdapterPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = XmlAdapterPluginConfig()
        config.invoke(pluginConfig)
        return XmlAdapterPluginInstance(pluginConfig)
    }

    val <T : AviatorServiceDataHolder> T.xml: XML
        get() = serializers.find { it is XML } as? XML ?: XML()

    fun XML.parseToElement(content: String): Element = decodeFromString(content)
    fun <T> XML.encodeToElement(serializer: SerializationStrategy<T>, value: T): Element {
        val stringified: String = encodeToString(serializer, value)
        return decodeFromString(stringified)
    }

    inline fun <reified T> XML.encodeToElement(value: T): Element =
        encodeToElement(serializersModule.serializer(), value)

    inline fun <reified T> XML.decodeFromElement(element: Element): T =
        decodeFromElement(serializersModule.serializer(), element)

    fun <T> XML.decodeFromElement(deserializer: DeserializationStrategy<T>, element: Element): T {
        val serialized = encodeToString(element)
        return decodeFromString(deserializer, serialized)
    }
}