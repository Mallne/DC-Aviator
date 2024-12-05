package cloud.mallne.dicentra.aviator.core.model.oas.security;

import java.util.*;

/**
 * SecurityRequirement
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#securityRequirementObject"
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.1.0/versions/3.1.0.md#securityRequirementObject"
 */

public class SecurityRequirement extends LinkedHashMap<String, List<String>> {
    public SecurityRequirement() {
    }

    public SecurityRequirement addList(String name, String item) {
        this.put(name, Collections.singletonList(item));
        return this;
    }

    public SecurityRequirement addList(String name, List<String> item) {
        this.put(name, item);
        return this;
    }

    public SecurityRequirement addList(String name) {
        this.put(name, new ArrayList<>());
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
        String sb = "class SecurityRequirement {\n" +
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

