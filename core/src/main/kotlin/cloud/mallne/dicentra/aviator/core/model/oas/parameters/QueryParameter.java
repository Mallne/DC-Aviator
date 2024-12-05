package cloud.mallne.dicentra.aviator.core.model.oas.parameters;

import java.util.Objects;

/**
 * QueryParameter
 */

public class QueryParameter extends Parameter {
    private String in = "query";

    /**
     * returns the in property from a QueryParameter instance.
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
    public QueryParameter in(String in) {
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
        QueryParameter queryParameter = (QueryParameter) o;
        return Objects.equals(this.in, queryParameter.in) &&
                super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(in, super.hashCode());
    }

    @Override
    public String toString() {
        String sb = "class QueryParameter {\n" +
                "    " + toIndentedString(super.toString()) + "\n" +
                "    in: " + toIndentedString(in) + "\n" +
                "}";
        return sb;
    }

}

