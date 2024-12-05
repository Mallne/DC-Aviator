package cloud.mallne.dicentra.aviator.core.model.oas.media;

import io.swagger.v3.oas.models.media.Schema;

/**
 * ComposedSchema
 */

public class ComposedSchema extends Schema<Object> {


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ComposedSchema {\n");
        sb.append("    ").append(toIndentedString(super.toString())).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
