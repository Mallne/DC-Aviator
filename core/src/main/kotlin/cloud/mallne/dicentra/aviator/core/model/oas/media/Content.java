package cloud.mallne.dicentra.aviator.core.model.oas.media;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Content
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#contentObject"
 */

public class Content extends LinkedHashMap<String, MediaType> {
    public Content() {
    }

    public Content addMediaType(String name, MediaType item) {
        this.put(name, item);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        String sb = "class Content {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "}";
        return sb;
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

}

