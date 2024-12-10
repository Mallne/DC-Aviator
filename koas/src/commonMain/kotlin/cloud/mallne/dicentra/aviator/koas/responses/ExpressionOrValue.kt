package cloud.mallne.dicentra.aviator.koas.responses

import kotlin.jvm.JvmInline

sealed interface ExpressionOrValue {
    @JvmInline
    value class Expression(val value: String) : ExpressionOrValue

    @JvmInline
    value class Value(val value: Any?) : ExpressionOrValue
}