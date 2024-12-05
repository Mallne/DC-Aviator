package cloud.mallne.dicentra.aviator.core.model.oas.security;

import io.swagger.v3.oas.models.annotations.OpenAPI31;
import io.swagger.v3.oas.models.security.OAuthFlows;

import java.util.Objects;

/**
 * SecurityScheme
 *
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#securitySchemeObject"
 * @see "https://github.com/OAI/OpenAPI-Specification/blob/3.1.0/versions/3.1.0.md#securitySchemeObject"
 */

public class SecurityScheme {
    private Type type = null;
    private String description = null;
    private String name = null;
    private String $ref = null;
    private In in = null;
    private String scheme = null;
    private String bearerFormat = null;
    private OAuthFlows flows = null;
    private String openIdConnectUrl = null;
    private java.util.Map<String, Object> extensions = null;

    /**
     * returns the type property from a SecurityScheme instance.
     *
     * @return Type type
     **/

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public SecurityScheme type(Type type) {
        this.type = type;
        return this;
    }

    /**
     * returns the description property from a SecurityScheme instance.
     *
     * @return String description
     **/

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SecurityScheme description(String description) {
        this.description = description;
        return this;
    }

    /**
     * returns the name property from a SecurityScheme instance.
     *
     * @return String name
     **/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SecurityScheme name(String name) {
        this.name = name;
        return this;
    }

    /**
     * returns the in property from a SecurityScheme instance.
     *
     * @return In in
     **/

    public In getIn() {
        return in;
    }

    public void setIn(In in) {
        this.in = in;
    }

    public SecurityScheme in(In in) {
        this.in = in;
        return this;
    }

    /**
     * returns the scheme property from a SecurityScheme instance.
     *
     * @return String scheme
     **/

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public SecurityScheme scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    /**
     * returns the bearerFormat property from a SecurityScheme instance.
     *
     * @return String bearerFormat
     **/

    public String getBearerFormat() {
        return bearerFormat;
    }

    public void setBearerFormat(String bearerFormat) {
        this.bearerFormat = bearerFormat;
    }

    public SecurityScheme bearerFormat(String bearerFormat) {
        this.bearerFormat = bearerFormat;
        return this;
    }

    /**
     * returns the flows property from a SecurityScheme instance.
     *
     * @return OAuthFlows flows
     **/

    public OAuthFlows getFlows() {
        return flows;
    }

    public void setFlows(OAuthFlows flows) {
        this.flows = flows;
    }

    public SecurityScheme flows(OAuthFlows flows) {
        this.flows = flows;
        return this;
    }

    /**
     * returns the openIdConnectUrl property from a SecurityScheme instance.
     *
     * @return String openIdConnectUrl
     **/

    public String getOpenIdConnectUrl() {
        return openIdConnectUrl;
    }

    public void setOpenIdConnectUrl(String openIdConnectUrl) {
        this.openIdConnectUrl = openIdConnectUrl;
    }

    public SecurityScheme openIdConnectUrl(String openIdConnectUrl) {
        this.openIdConnectUrl = openIdConnectUrl;
        return this;
    }

    public java.util.Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(java.util.Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    @OpenAPI31
    public void addExtension31(String name, Object value) {
        if (name != null && (name.startsWith("x-oas-") || name.startsWith("x-oai-"))) {
            return;
        }
        addExtension(name, value);
    }

    public void addExtension(String name, Object value) {
        if (name == null || name.isEmpty() || !name.startsWith("x-")) {
            return;
        }
        if (this.extensions == null) {
            this.extensions = new java.util.LinkedHashMap<>();
        }
        this.extensions.put(name, value);
    }

    public SecurityScheme extensions(java.util.Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * returns the $ref property from an SecurityScheme instance.
     *
     * @return String $ref
     **/
    public String get$ref() {
        return $ref;
    }

    public void set$ref(String $ref) {
        if ($ref != null && ($ref.indexOf('.') == -1 && $ref.indexOf('/') == -1)) {
            $ref = "#/components/securitySchemes/" + $ref;
        }
        this.$ref = $ref;
    }

    public SecurityScheme $ref(String $ref) {
        set$ref($ref);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SecurityScheme that)) {
            return false;
        }

        if (type != that.type) {
            return false;
        }
        if (!Objects.equals(description, that.description)) {
            return false;
        }
        if (!Objects.equals(name, that.name)) {
            return false;
        }
        if (!Objects.equals($ref, that.$ref)) {
            return false;
        }
        if (in != that.in) {
            return false;
        }
        if (!Objects.equals(scheme, that.scheme)) {
            return false;
        }
        if (!Objects.equals(bearerFormat, that.bearerFormat)) {
            return false;
        }
        if (!Objects.equals(flows, that.flows)) {
            return false;
        }
        if (!Objects.equals(openIdConnectUrl, that.openIdConnectUrl)) {
            return false;
        }
        return Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + ($ref != null ? $ref.hashCode() : 0);
        result = 31 * result + (in != null ? in.hashCode() : 0);
        result = 31 * result + (scheme != null ? scheme.hashCode() : 0);
        result = 31 * result + (bearerFormat != null ? bearerFormat.hashCode() : 0);
        result = 31 * result + (flows != null ? flows.hashCode() : 0);
        result = 31 * result + (openIdConnectUrl != null ? openIdConnectUrl.hashCode() : 0);
        result = 31 * result + (extensions != null ? extensions.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {

        String sb = "class SecurityScheme {\n" +
                "    type: " + toIndentedString(type) + "\n" +
                "    description: " + toIndentedString(description) + "\n" +
                "    name: " + toIndentedString(name) + "\n" +
                "    in: " + toIndentedString(in) + "\n" +
                "    scheme: " + toIndentedString(scheme) + "\n" +
                "    bearerFormat: " + toIndentedString(bearerFormat) + "\n" +
                "    flows: " + toIndentedString(flows) + "\n" +
                "    openIdConnectUrl: " + toIndentedString(openIdConnectUrl) + "\n" +
                "    $ref: " + toIndentedString($ref) + "\n" +
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

    /**
     * Gets or Sets type
     */
    public enum Type {
        APIKEY("apiKey"),
        HTTP("http"),
        OAUTH2("oauth2"),
        OPENIDCONNECT("openIdConnect"),
        MUTUALTLS("mutualTLS");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    /**
     * Gets or Sets in
     */
    public enum In {
        COOKIE("cookie"),

        HEADER("header"),

        QUERY("query");

        private final String value;

        In(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

}

