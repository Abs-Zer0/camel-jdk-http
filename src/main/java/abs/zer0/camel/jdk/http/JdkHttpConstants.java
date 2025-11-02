package abs.zer0.camel.jdk.http;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Constants for the JDK HTTP component.
 *
 * @see JdkHttpComponent
 * @see JdkHttpBinding
 * @see JdkHttpProducer
 * @see JdkHttpAsyncProducer
 */
public final class JdkHttpConstants {

    @Metadata(label = "producer", javaType = "java.net.http.HttpClient.Version", description = "The version of the HTTP protocol used.")
    public static final String HTTP_PROTOCOL_VERSION = Exchange.HTTP_PROTOCOL_VERSION;
    @Metadata(label = "producer", javaType = "String", description = "The HTTP method to use.")
    public static final String HTTP_METHOD = Exchange.HTTP_METHOD;

    @Metadata(label = "producer", javaType = "String", description = "URI scheme." +
            " Will override existing URI scheme set directly on the endpoint." +
            " Can be http or https only.")
    public static final String HTTP_SCHEME = Exchange.HTTP_SCHEME;
    @Metadata(label = "producer", javaType = "String", description = "URI host." +
            " Will override existing URI host set directly on the endpoint.")
    public static final String HTTP_HOST = Exchange.HTTP_HOST;
    @Metadata(label = "producer", javaType = "Integer", description = "URI port." +
            " Will override existing URI port set directly on the endpoint.")
    public static final String HTTP_PORT = Exchange.HTTP_PORT;
    @Metadata(label = "producer", javaType = "String", description = "URI path." +
            " Will override existing URI path set directly on the endpoint.")
    public static final String HTTP_PATH = Exchange.HTTP_PATH;
    @Metadata(label = "producer", javaType = "String", description = "URI parameters." +
            " Will override existing URI parameters set directly on the endpoint.")
    public static final String HTTP_QUERY = Exchange.HTTP_QUERY;
    @Metadata(label = "producer", javaType = "java.net.URI", description = "URI to call." +
            " Will override existing URI set directly on the endpoint.")
    public static final String HTTP_URI = Exchange.HTTP_URI;

    @Metadata(javaType = "String", description = "The HTTP Content-Type.")
    public static final String CONTENT_TYPE = Exchange.CONTENT_TYPE;
    @Metadata(javaType = "String", description = "The HTTP Content-Length.")
    public static final String CONTENT_LENGTH = Exchange.CONTENT_LENGTH;
    public static final String EXPECT = "Expect";
    public static final String LOCATION = "Location";

    @Metadata(label = "producer", javaType = "int", description = "The HTTP response code from the external server.")
    public static final String HTTP_RESPONSE_CODE = Exchange.HTTP_RESPONSE_CODE;
    @Metadata(label = "producer", javaType = "String", description = "he HTTP response text from the external server.")
    public static final String HTTP_RESPONSE_TEXT = Exchange.HTTP_RESPONSE_TEXT;


    public static final Set<String> RESTRICTED_HEADERS = setOfRestrictedHeaders();

    public static final Map<Integer, String> HTTP_STATUSES = Map.ofEntries(
            Map.entry(100, "Continue"),
            Map.entry(101, "Switching Protocols"),
            Map.entry(103, "Early Hints"),

            Map.entry(200, "OK"),
            Map.entry(201, "Created"),
            Map.entry(202, "Accepted"),
            Map.entry(203, "Non-Authoritative Information"),
            Map.entry(204, "No Content"),
            Map.entry(205, "Reset Content"),
            Map.entry(206, "Partial Content"),
            Map.entry(226, "IM Used"),

            Map.entry(300, "Multiple Choices"),
            Map.entry(301, "Moved Permanently"),
            Map.entry(302, "Moved Temporarily"),
            Map.entry(303, "See Other"),
            Map.entry(304, "Not Modified"),
            Map.entry(305, "Use Proxy"),
            Map.entry(307, "Temporary Redirect"),
            Map.entry(308, "Permanent Redirect"),

            Map.entry(400, "Bad Request"),
            Map.entry(401, "Unauthorized"),
            Map.entry(402, "Payment Required"),
            Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"),
            Map.entry(405, "Method Not Allowed"),
            Map.entry(406, "Not Acceptable"),
            Map.entry(407, "Proxy Authentication Required"),
            Map.entry(408, "Request Timeout"),
            Map.entry(409, "Conflict"),
            Map.entry(410, "Gone"),
            Map.entry(411, "Length Required"),
            Map.entry(412, "Precondition Failed"),
            Map.entry(413, "Payload Too Large"),
            Map.entry(414, "URI Too Long"),
            Map.entry(415, "Unsupported Media Type"),
            Map.entry(416, "Range Not Satisfiable"),
            Map.entry(417, "Expectation Failed"),
            Map.entry(421, "Misdirected Request"),
            Map.entry(425, "Too Early"),
            Map.entry(426, "Upgrade Required"),
            Map.entry(428, "Precondition Required"),
            Map.entry(429, "Too Many Requests"),

            Map.entry(500, "Internal Server Error"),
            Map.entry(501, "Not Implemented"),
            Map.entry(502, "Bad Gateway"),
            Map.entry(503, "Service Unavailable"),
            Map.entry(504, "Gateway Timeout"),
            Map.entry(505, "HTTP Version Not Supported"),
            Map.entry(506, "Variant Also Negotiates"),
            Map.entry(510, "Not Extended")
    );


    private JdkHttpConstants() {
    }

    private static Set<String> setOfRestrictedHeaders() {
        final Set<String> restrictedHeaders = new TreeSet<>(String::compareToIgnoreCase);
        restrictedHeaders.add("Connection");
        restrictedHeaders.add(CONTENT_LENGTH);
        restrictedHeaders.add(EXPECT);
        restrictedHeaders.add("Host");
        restrictedHeaders.add("Upgrade");

        return Collections.unmodifiableSet(restrictedHeaders);
    }

}
