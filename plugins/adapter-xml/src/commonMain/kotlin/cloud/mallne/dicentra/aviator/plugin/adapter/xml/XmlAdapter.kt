package cloud.mallne.dicentra.aviator.plugin.adapter.xml

import cloud.mallne.dicentra.aviator.core.AviatorServiceDataHolder
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPlugin
import cloud.mallne.dicentra.aviator.core.plugins.AviatorPluginInstance
import kotlinx.serialization.*
import nl.adaptivity.xmlutil.dom2.*
import nl.adaptivity.xmlutil.serialization.XML

object XmlAdapter : AviatorPlugin<XmlAdapterPluginConfig> {
    override val identity: String = "adapter:XML"
    override fun install(config: XmlAdapterPluginConfig.() -> Unit): AviatorPluginInstance {
        val pluginConfig = XmlAdapterPluginConfig()
        config.invoke(pluginConfig)
        return XmlAdapterPluginInstance(pluginConfig)
    }

    val <T : AviatorServiceDataHolder> T.xml: XML
        get() = serializers.find { it is XML } as? XML ?: XML.recommended_1_0()

    fun XML.parseToElement(content: String): Element = decodeFromString(content)
    fun <T> XML.encodeToElement(serializer: SerializationStrategy<T>, value: T): Element {
        val stringified: String = encodeToString(serializer, value)
        val element: Element = decodeFromString(stringified)
        element.stripNamespaceDeclarations()
        return element
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

internal fun Element.stripNamespaceDeclarations() {
    val attrs = attributes
    val nsAttrs = mutableListOf<Attr>()
    for (attr in attrs) {
        if (attr.name.startsWith("xmlns")) {
            nsAttrs.add(attr)
        }
    }
    for (nsAttr in nsAttrs) {
        removeAttributeNode(nsAttr)
    }
    var child: Node? = firstChild
    while (child != null) {
        if (child is Element) {
            child.stripNamespaceDeclarations()
        }
        child = child.nextSibling
    }
}