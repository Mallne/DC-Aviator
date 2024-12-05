package cloud.mallne.dicentra.aviator.core.model.oas.parameters;

import java.util.Objects;

/**
 * CookieParameter
 */

public class CookieParameter extends Parameter {
    private String in = "cookie";

    /**
     * returns the in property from a CookieParameter instance.
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
    public CookieParameter in(String in) {
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
        CookieParameter cookieParameter = (CookieParameter) o;
        return Objects.equals(this.in, cookieParameter.in) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(in, super.hashCode());
    }

    @Override
    public String toString() {
        String sb = "class CookieParameter {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "    in: " + toIndentedString(in) + "\n" +
                "}";
        return sb;
    }

}

