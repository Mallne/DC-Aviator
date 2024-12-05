package cloud.mallne.dicentra.aviator.core.model.oas.parameters;

import java.util.Objects;

/**
 * HeaderParameter
 */

public class HeaderParameter extends Parameter {
    private String in = "header";

    /**
     * returns the in property from a HeaderParameter instance.
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
    public HeaderParameter in(String in) {
        this.in = in;
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
        HeaderParameter headerParameter = (HeaderParameter) o;
        return Objects.equals(this.in, headerParameter.in) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(in, super.hashCode());
    }

    @Override
    public String toString() {
        String sb = "class HeaderParameter {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "    in: " + toIndentedString(in) + "\n" +
                "}";
        return sb;
    }

}

