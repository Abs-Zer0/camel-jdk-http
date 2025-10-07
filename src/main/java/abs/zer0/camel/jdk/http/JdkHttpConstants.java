package abs.zer0.camel.jdk.http;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

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

    private JdkHttpConstants() {
    }

}
