package cloud.mallne.dicentra.aviator.koas.io

import cloud.mallne.dicentra.aviator.koas.PathItem
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * A map of possible out-of band callbacks related to the parent operation. Each value in the map is
 * a [cloud.mallne.dicentra.aviator.koas.PathItem] Object that describes a set of requests that may be initiated by the API provider
 * and the expected responses. The key value used to identify the path item object is an expression,
 * evaluated at runtime, that identifies a URL to use for the callback operation.
 */
@Serializable
@JvmInline
value class Callback(val value: Map<String, PathItem>)