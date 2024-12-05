package cloud.mallne.dicentra.aviator.core.model.oas.media;

import io.swagger.v3.oas.models.SpecVersion;

/**
 * JsonSchema
 */

public class JsonSchema extends Schema<Object> {

    public JsonSchema() {
        specVersion(SpecVersion.V31);
    }

    @Override
    public String toString() {
        String sb = "class JsonSchema {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "}";
        return sb;
    }
}
