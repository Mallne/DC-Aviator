package cloud.mallne.dicentra.aviator.koas.typed

import cloud.mallne.dicentra.aviator.koas.io.Schema
import kotlinx.serialization.Serializable

sealed interface Constraints {
    @Serializable
    data class Number(
        val exclusiveMinimum: Boolean,
        val minimum: Double,
        val exclusiveMaximum: Boolean,
        val maximum: Double,
        val multipleOf: Double?
    ) : Constraints {
        companion object {
            operator fun invoke(schema: Schema): Number? =
                if (schema.minimum != null || schema.maximum != null || schema.multipleOf != null)
                    Number(
                        schema.exclusiveMinimum == true,
                        schema.minimum ?: Double.NEGATIVE_INFINITY,
                        schema.exclusiveMaximum == true,
                        schema.maximum ?: Double.POSITIVE_INFINITY,
                        schema.multipleOf
                    )
                else null
        }
    }

    @Serializable
    data class Text(val minLength: Int, val maxLength: Int, val pattern: String?) : Constraints {
        companion object {
            operator fun invoke(schema: Schema): Text? =
                if (schema.maxLength != null || schema.minLength != null || schema.pattern != null)
                    Text(schema.minLength ?: 0, schema.maxLength ?: Int.MAX_VALUE, schema.pattern)
                else null
        }
    }

    @Serializable
    data class Collection(
        val minItems: Int,
        val maxItems: Int,
    ) : Constraints {
        companion object {
            operator fun invoke(schema: Schema): Collection? =
                if (schema.minItems != null || schema.maxItems != null)
                    Collection(schema.minItems ?: 0, schema.maxItems ?: Int.MAX_VALUE)
                else null
        }
    }

    // TODO `not` is not supported yet
    /**
     * minProperties and maxProperties let you restrict the number of properties allowed in an object.
     * This can be useful when using additionalProperties, or free-form objects.
     */
    @Serializable
    data class Object(
        val minProperties: Int,
        val maxProperties: Int,
    ) : Constraints {
        companion object {
            operator fun invoke(schema: Schema): Object? =
                if (schema.minProperties != null || schema.maxProperties != null)
                    Object(
                        schema.minProperties ?: 0,
                        schema.maxProperties ?: Int.MAX_VALUE,
                    )
                else null
        }
    }
}
