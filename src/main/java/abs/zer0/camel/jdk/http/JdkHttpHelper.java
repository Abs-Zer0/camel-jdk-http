package abs.zer0.camel.jdk.http;

import org.apache.camel.*;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.util.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

public final class JdkHttpHelper {

    public static <T> T getHeaderIgnoreCase(Message message, String name, Class<T> type) {
        Objects.requireNonNull(message, "Camel Message cannot be null");
        Objects.requireNonNull(name, "Header name cannot be null");

        return message.getHeaders().keySet()
                .stream()
                .filter(name::equalsIgnoreCase)
                .findFirst()
                .map(s -> message.getHeader(s, type))
                .orElse(null);
    }


    public static HttpRequest httpRequestFromExchange(Exchange exchange, JdkHttpConfiguration configuration)
            throws URISyntaxException, CamelExchangeException {
        Objects.requireNonNull(exchange, "Camel Exchange cannot be null");
        Objects.requireNonNull(configuration, "JdkHttpConfiguration cannot be null");

        final Message message = exchange.getMessage();

        final HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .uri(getRequestUri(exchange, message, configuration))
                .expectContinue(getRequestExpect(message));

        final String method = getRequestMethod(message);
        switch (method.toUpperCase()) {
            case "DELETE":
                httpRequestBuilder.DELETE();
                break;
            case "GET":
                httpRequestBuilder.GET();
                break;
            case "POST":
                httpRequestBuilder.POST(getRequestBody(exchange, message));
                break;
            case "PUT":
                httpRequestBuilder.PUT(getRequestBody(exchange, message));
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        final Map<String, List<String>> headers = getRequestHeaders(exchange, message, configuration);
        headers.forEach((name, listValue) -> {
            for (final String value : listValue) {
                httpRequestBuilder.header(name, value);
            }
        });

        final HttpClient.Version version = message.getHeader(JdkHttpConstants.HTTP_PROTOCOL_VERSION, HttpClient.Version.class);
        if (version != null) {
            httpRequestBuilder.version(version);
        }

        if (configuration.getResponseTimeout() != null) {
            httpRequestBuilder.timeout(configuration.getResponseTimeout());
        }

        return httpRequestBuilder.build();
    }

    public static void httpResponseToExchange(HttpResponse<InputStream> httpResponse, Exchange exchange, JdkHttpConfiguration configuration)
            throws HttpOperationFailedException, IOException {
        Objects.requireNonNull(httpResponse, "HTTP Response cannot be null");
        Objects.requireNonNull(exchange, "Camel Exchange cannot be null");
        Objects.requireNonNull(configuration, "JdkHttpConfiguration cannot be null");

        final Message message = exchange.getMessage();

        final int statusCode = httpResponse.statusCode();
        final String statusText = HTTP_STATUSES.get(statusCode);
        if (!configuration.getOkStatusCodes().contains(statusCode) && configuration.isThrowExceptionOnFailure()) {
            final String uri = httpResponse.uri().toString();
            final String location = httpResponse.headers().firstValue(JdkHttpConstants.LOCATION).orElse(null);

            throw new HttpOperationFailedException(uri, statusCode, statusText, location, null, null);
        }
        message.setHeader(JdkHttpConstants.HTTP_RESPONSE_CODE, statusCode);
        message.setHeader(JdkHttpConstants.HTTP_RESPONSE_TEXT, statusText);

        setResponseHeaders(httpResponse, exchange, message, configuration);
        setResponseBody(httpResponse, exchange, message, configuration);
    }


    private static URI getRequestUri(Exchange exchange, Message message, JdkHttpConfiguration configuration)
            throws URISyntaxException {
        String uriStr = message.getHeader(JdkHttpConstants.HTTP_URI, String.class);
        if (uriStr == null || uriStr.isBlank()) {
            uriStr = configuration.getHttpUri().toASCIIString();
        }

        uriStr = exchange.getContext().resolvePropertyPlaceholders(uriStr);
        URI uri = URI.create(uriStr);

        final String scheme = message.getHeader(JdkHttpConstants.HTTP_SCHEME, String.class);
        if (scheme != null && !scheme.isBlank()) {
            final String schemeTrimmed = scheme.trim();
            if (!"http".equals(schemeTrimmed) && !"https".equals(schemeTrimmed)) {
                throw new IllegalArgumentException(JdkHttpConstants.HTTP_SCHEME + " header must provided only http or https value");
            }
            uri = new URI(
                    scheme,
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        }

        final String host = message.getHeader(JdkHttpConstants.HTTP_HOST, String.class);
        if (host != null && !host.isBlank()) {
            uri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    host.trim(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        }

        final Integer port = message.getHeader(JdkHttpConstants.HTTP_PORT, Integer.class);
        if (port != null) {
            uri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    port,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            );
        }

        final String path = message.getHeader(JdkHttpConstants.HTTP_PATH, String.class);
        if (path != null && !path.isBlank()) {
            String encodedPath = UnsafeUriCharactersEncoder.encodeHttpURI(path.trim());
            if (uri.getPath() != null && uri.getPath().endsWith("/") && encodedPath.startsWith("/")) {
                encodedPath = encodedPath.substring(1);
            } else if (uri.getPath() != null && !uri.getPath().endsWith("/") && !encodedPath.startsWith("/")) {
                encodedPath = "/" + encodedPath;
            }

            uri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath() + encodedPath,
                    uri.getQuery(),
                    uri.getFragment()
            );
        }

        final String query = message.getHeader(JdkHttpConstants.HTTP_QUERY, String.class);
        if (query != null && !query.isBlank()) {
            uri = URISupport.createURIWithQuery(uri, UnsafeUriCharactersEncoder.encodeHttpURI(query.trim()));
        }

        return uri;
    }

    private static String getRequestMethod(Message message) {
        final String method = message.getHeader(JdkHttpConstants.HTTP_METHOD, String.class);
        if (method != null && !method.isBlank()) {
            return method;
        }

        return message.getBody() != null ? "POST" : "GET";
    }

    private static HttpRequest.BodyPublisher getRequestBody(Exchange exchange, Message message) throws CamelExchangeException {
        final Object body = message.getBody();
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }

        if (body instanceof HttpRequest.BodyPublisher bodyPublisher) {
            return bodyPublisher;
        } else if (body instanceof byte[] bytes) {
            return HttpRequest.BodyPublishers.ofByteArray(bytes);
        } else if (body instanceof String str) {
            final String contentType = getHeaderIgnoreCase(message, JdkHttpConstants.CONTENT_TYPE, String.class);
            final String charsetName = (contentType != null && !contentType.isBlank()) ?
                    IOHelper.getCharsetNameFromContentType(contentType.toLowerCase()) :
                    ExchangeHelper.getCharsetName(exchange, true);

            return HttpRequest.BodyPublishers.ofString(str, Charset.forName(charsetName));
        }

        try {
            if (body instanceof File file) {
                return HttpRequest.BodyPublishers.ofFile(file.toPath());
            } else if (body instanceof Path path) {
                return HttpRequest.BodyPublishers.ofFile(path);
            }
        } catch (FileNotFoundException fnfe) {
            throw new CamelExchangeException("Error creating File body from message", exchange, fnfe);
        }

        final InputStream inputStreamBody = message.getBody(InputStream.class);
        HttpRequest.BodyPublisher inputStreamPublisher = HttpRequest.BodyPublishers.ofInputStream(() -> inputStreamBody);

        final Long contentLength = getHeaderIgnoreCase(message, JdkHttpConstants.CONTENT_LENGTH, Long.class);
        if (contentLength != null) {
            inputStreamPublisher = HttpRequest.BodyPublishers.fromPublisher(inputStreamPublisher, contentLength);
        }

        return inputStreamPublisher;
    }

    private static boolean getRequestExpect(Message message) {
        final String expectHeader = getHeaderIgnoreCase(message, JdkHttpConstants.EXPECT, String.class);

        return "100-continue".equals(expectHeader);
    }

    private static Map<String, List<String>> getRequestHeaders(Exchange exchange, Message message, JdkHttpConfiguration configuration) {
        final TypeConverter typeConverter = exchange.getContext().getTypeConverter();
        final HeaderFilterStrategy headerFilterStrategy = configuration.getHeaderFilterStrategy();

        final Map<String, List<String>> filteredHeaders = new TreeMap<>(String::compareToIgnoreCase);
        for (Map.Entry<String, Object> header : message.getHeaders().entrySet()) {
            final String headerName = header.getKey();
            final Iterable<?> headerValues = ObjectHelper.createIterable(header.getValue(), null, true);

            headerValues.forEach(value -> {
                final String strValue = typeConverter.tryConvertTo(String.class, value);
                if (strValue != null && !headerFilterStrategy.applyFilterToCamelHeaders(headerName, strValue, exchange)) {
                    filteredHeaders.computeIfAbsent(headerName, key -> new LinkedList<>())
                            .add(strValue);
                }
            });
        }

        return filteredHeaders;
    }


    private static void setResponseHeaders(HttpResponse<InputStream> httpResponse, Exchange exchange, Message message, JdkHttpConfiguration configuration) {
        final HeaderFilterStrategy headerFilterStrategy = configuration.getHeaderFilterStrategy();

        for (Map.Entry<String, List<String>> header : httpResponse.headers().map().entrySet()) {
            final String headerName = header.getKey();
            final List<String> headerValues = header.getValue();

            if (JdkHttpConstants.CONTENT_TYPE.equalsIgnoreCase(headerName) && !headerValues.isEmpty()) {
                exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(headerValues.getFirst()));
            }

            final Object camelHeaderValue = headerValues.size() == 1 ?
                    headerValues.getFirst() :
                    new ArrayList<>(headerValues);
            if (!headerFilterStrategy.applyFilterToExternalHeaders(headerName, camelHeaderValue, exchange)) {
                message.setHeader(headerName, camelHeaderValue);
            }
        }
    }

    private static void setResponseBody(HttpResponse<InputStream> httpResponse, Exchange exchange, Message message, JdkHttpConfiguration configuration)
            throws IOException {
        final InputStream body = httpResponse.body();

        if (body == null) {
            message.setBody(null);
        } else if (configuration.isResponseBodyAsByteArray()) {
            message.setBody(body.readAllBytes());
        } else if (configuration.isDisableStreamCache()) {
            message.setBody(body);
        } else {
            final OutputStreamBuilder streamCacheBuilder = OutputStreamBuilder.withExchange(exchange);
            IOHelper.copy(body, streamCacheBuilder);
            message.setBody(streamCacheBuilder.build());
        }
    }


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


    private JdkHttpHelper() {
    }

}
