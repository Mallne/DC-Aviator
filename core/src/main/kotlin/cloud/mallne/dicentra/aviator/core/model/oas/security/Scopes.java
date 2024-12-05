package cloud.mallne.dicentra.aviator.core.model.oas.security;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Scopes
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#scopedObject"
 */

public class Scopes extends LinkedHashMap<String, String> {
    private java.util.Map<String, Object> extensions = null;

    public Scopes() {
    }

    public Scopes addString(String name, String item) {
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
        Scopes scopes = (Scopes) o;
        return Objects.equals(this.extensions, scopes.extensions) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extensions, super.hashCode());
    }

    /**
     * @deprecated As extensions don't make sense at this level
     */
    @Deprecated
    public java.util.Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * @deprecated As extensions don't make sense at this level
     */
    @Deprecated
    public void setExtensions(java.util.Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    /**
     * @deprecated As extensions don't make sense at this level
     */
    @Deprecated
    public void addExtension(String name, Object value) {
        if (name == null || name.isEmpty() || !name.startsWith("x-")) {
            return;
        }
        if (this.extensions == null) {
            this.extensions = new LinkedHashMap<>();
        }
        this.extensions.put(name, value);
    }

    /**
     * @deprecated As extensions don't make sense at this level
     */
    @Deprecated
    public Scopes extensions(java.util.Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    @Override
    public String toString() {
        String sb = "class Scopes {\n" +
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

