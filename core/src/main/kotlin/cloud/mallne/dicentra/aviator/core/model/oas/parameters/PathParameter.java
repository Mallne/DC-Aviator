package cloud.mallne.dicentra.aviator.core.model.oas.parameters;

import java.util.Objects;

/**
 * PathParameter
 */

public class PathParameter extends Parameter {
    private String in = "path";
    private Boolean required = true;

    /**
     * returns the in property from a PathParameter instance.
     *
     * @return String in
     **/
    @Override
    public String getIn() {
        return in;
    }

    @Override
    public void setIn(String in) {
        this.in = in;
    }

    @Override
    public PathParameter in(String in) {
        this.in = in;
        return this;
    }

    /**
     * returns the required property from a PathParameter instance.
     *
     * @return Boolean required
     **/
    @Override
    public Boolean getRequired() {
        return required;
    }

    @Override
    public void setRequired(Boolean required) {
        this.required = required;
    }

    @Override
    public PathParameter required(Boolean required) {
        this.required = required;
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
        PathParameter pathParameter = (PathParameter) o;
        return Objects.equals(this.in, pathParameter.in) &&
                Objects.equals(this.required, pathParameter.required) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(in, required, super.hashCode());
    }

    @Override
    public String toString() {
        String sb = "class PathParameter {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "    in: " + toIndentedString(in) + "\n" +
                "    required: " + toIndentedString(required) + "\n" +
                "}";
        return sb;
    }

}

