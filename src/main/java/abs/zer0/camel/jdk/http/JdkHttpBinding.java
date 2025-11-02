package abs.zer0.camel.jdk.http;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

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
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class is responsible for converting a message from a Camel Exchange into an {@link HttpRequest},
 * as well as for converting a received {@link HttpResponse} back into a Camel Exchange.
 * It provides configurable control over the HTTP method, headers, request/response body,
 * status codes, and other parameters of HTTP interaction.
 */
public final class JdkHttpBinding {

    private static final Set<String> ALLOW_RESTRICTED_HEADERS = parseAllowRestrictedHeaders();

    private final URI httpUri;
    private String httpMethod;

    private boolean throwExceptionOnFailure = true;
    private Set<Integer> okStatusCodes = IntStream.rangeClosed(200, 299).boxed().collect(Collectors.toUnmodifiableSet());
    private boolean disableStreamCache = false;
    private boolean responseBodyAsByteArray = false;

    private Duration responseTimeout;

    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();

    /**
     * Creates a new binding instance with the specified default URI.
     *
     * @param httpUri the URI is used as a fallback when no URI header is present in the Exchange. Must not be {@code null}.
     */
    public JdkHttpBinding(URI httpUri) {
        this.httpUri = Objects.requireNonNull(httpUri, "HTTP URI cannot be null");
    }

    /**
     * Converts a message from an {@link Exchange} into an {@link HttpRequest}.
     * The method extracts the URI, method, headers, and body from the Exchange to build the HTTP request.
     * <br/>
     * The fields of this class customize the creation logic of the {@link HttpRequest}.
     *
     * @param exchange the Camel Exchange containing the source message.
     * @return a configured {@link HttpRequest}.
     * @throws URISyntaxException       if the URI in the message is invalid.
     * @throws CamelExchangeException   if an error occurs while creating the request body.
     * @throws IllegalArgumentException if an unsupported HTTP method is used.
     */
    public HttpRequest httpRequestFromExchange(Exchange exchange) throws URISyntaxException, CamelExchangeException {
        final Message message = exchange.getMessage();

        final HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .uri(getRequestUri(exchange))
                .expectContinue(getRequestExpect(message));

        final String method = getRequestMethod(message);
        switch (method.toUpperCase()) {
            case "PATCH":
            case "POST":
            case "PUT":
                httpRequestBuilder.method(method, getRequestBody(exchange));
                break;
            case "DELETE":
            case "GET":
            case "HEAD":
                httpRequestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        final Map<String, List<String>> headers = getRequestHeaders(exchange);
        headers.forEach((name, listValue) -> {
            for (final String value : listValue) {
                httpRequestBuilder.header(name, value);
            }
        });

        final HttpClient.Version version = message.getHeader(JdkHttpConstants.HTTP_PROTOCOL_VERSION, HttpClient.Version.class);
        if (version != null) {
            httpRequestBuilder.version(version);
        }

        if (responseTimeout != null) {
            httpRequestBuilder.timeout(responseTimeout);
        }

        return httpRequestBuilder.build();
    }

    /**
     * Populates the {@link Exchange} with information from the {@link HttpResponse}.
     * It extracts the status code, headers, and body from the response and places them into the Exchange message.
     * <br/>
     * If the response status is not successful and the {@code throwExceptionOnFailure} flag is enabled,
     * an {@link HttpOperationFailedException} will be thrown.
     *
     * @param httpResponse the HTTP response received from the server.
     * @param exchange     the Camel Exchange to be updated.
     * @throws HttpOperationFailedException if the response status indicates an error and {@code throwExceptionOnFailure} is {@code true}.
     * @throws IOException                  if an error occurs while reading the response body.
     */
    public void httpResponseToExchange(HttpResponse<InputStream> httpResponse, Exchange exchange)
            throws HttpOperationFailedException, IOException {
        final Message message = exchange.getMessage();

        final int statusCode = httpResponse.statusCode();
        final String statusText = JdkHttpConstants.HTTP_STATUSES.get(statusCode);
        if (!okStatusCodes.contains(statusCode) && throwExceptionOnFailure) {
            final String uri = httpResponse.uri().toString();
            final String location = httpResponse.headers().firstValue(JdkHttpConstants.LOCATION).orElse(null);

            throw new HttpOperationFailedException(uri, statusCode, statusText, location, null, null);
        }
        message.setHeader(JdkHttpConstants.HTTP_RESPONSE_CODE, statusCode);
        message.setHeader(JdkHttpConstants.HTTP_RESPONSE_TEXT, statusText);

        setResponseHeaders(httpResponse, exchange);
        setResponseBody(httpResponse, exchange);
    }


    /**
     * Gets the default URI configured for this binding.
     *
     * @return the default {@link URI}.
     */
    public URI getHttpUri() {
        return httpUri;
    }

    /**
     * Gets the overriden HTTP method configured for this binding.
     *
     * @return the overriden HTTP method, or {@code null} if not set.
     */
    public String getHttpMethod() {
        return httpMethod;
    }

    /**
     * Sets the overriden HTTP method for this binding.
     * This method ignores the {@link JdkHttpConstants#HTTP_METHOD} header in the Exchange.
     * <br/>
     * If neither is set, the method defaults to "GET" for a request without a body and "POST" otherwise.
     *
     * @param httpMethod the overriden HTTP method. Must not be {@code null}.
     */
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "HTTP method cannot be null")
                .trim()
                .toUpperCase();
    }

    /**
     * The flag that determines whether an exception should be thrown for non-successful HTTP responses.
     *
     * @return {@code true} if an exception is thrown on failure, otherwise {@code false}.
     */
    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Sets the flag that determines whether an exception should be thrown for non-successful HTTP responses.
     * If set to {@code true}, an {@link HttpOperationFailedException} is thrown when the response status code
     * is not in the successful code ranges.
     *
     * @param throwExceptionOnFailure {@code true} to throw an exception on failure, otherwise {@code false}.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    /**
     * Gets the set of status codes that are considered successful.
     * A response with a status code in this set will not trigger an exception,
     * even if {@link #throwExceptionOnFailure} is {@code true}.
     *
     * @return an unmodifiable {@link Set} of successful status codes.
     */
    public Set<Integer> getOkStatusCodes() {
        return okStatusCodes;
    }

    /**
     * Sets the ranges of status codes that are considered successful.
     * The format of the string is a comma-separated list of codes or ranges, e.g., "200,201-299,404".
     * <br/>
     * The default range is 200-299.
     *
     * @param okStatusCodeRanges a string with the status code ranges. Must not be {@code null}.
     */
    public void setOkStatusCodeRanges(String okStatusCodeRanges) {
        Objects.requireNonNull(okStatusCodeRanges, "OK StatusCode ranges cannot be null");
        this.okStatusCodes = parseOkStatusCodeRanges(okStatusCodeRanges);
    }

    /**
     * The flag that determines whether stream caching for the response body is disabled.
     *
     * @return {@code true} if stream caching must be disabled, otherwise {@code false}.
     */
    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    /**
     * Sets the flag to disable stream caching for the response body.
     * If {@code true}, the response body will be set as an {@link InputStream} directly in the message,
     * allowing for only a single read. If {@code false}, the body is cached in memory for repeated access.
     *
     * @param disableStreamCache {@code true} to disable stream caching, otherwise {@code false}.
     */
    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    /**
     * The flag that determines if the response body should be returned as a byte array ({@code byte[]}).
     *
     * @return {@code true} if the response body will be a {@code byte[]}, otherwise {@code false}.
     */
    public boolean isResponseBodyAsByteArray() {
        return responseBodyAsByteArray;
    }

    /**
     * Sets the flag to determine that the response body should be converted to a byte array ({@code byte[]}).
     * If {@code true}, the response body will be fully read and converted to a {@code byte[]}.
     * <br/>
     * This setting takes precedence over {@link #disableStreamCache}.
     *
     * @param responseBodyAsByteArray {@code true} for the response body to be a {@code byte[]}, otherwise {@code false}.
     */
    public void setResponseBodyAsByteArray(boolean responseBodyAsByteArray) {
        this.responseBodyAsByteArray = responseBodyAsByteArray;
    }

    /**
     * Gets the timeout for waiting an HTTP response.
     *
     * @return the {@link Duration} timeout, or {@code null} if not set.
     */
    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * Sets the timeout for waiting an HTTP response.
     * This timeout is applied to the {@link HttpRequest} when it is built.
     *
     * @param responseTimeout the {@link Duration} timeout. Must not be {@code null}.
     */
    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "Response timeout cannot be null");
    }

    /**
     * Gets the header filtering strategy used to control which headers are propagated.
     *
     * @return the {@link HeaderFilterStrategy}.
     */
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * Sets the header filtering strategy.
     * This strategy determines which headers are filtered out when mapping between Camel messages
     * and HTTP requests/responses.
     *
     * @param headerFilterStrategy the {@link HeaderFilterStrategy} to use. Must not be {@code null}.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = Objects.requireNonNull(headerFilterStrategy, "Camel HeaderFilterStrategy cannot be null");
    }


    private static Set<String> parseAllowRestrictedHeaders() {
        final String allowRestrictedHeaders = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (allowRestrictedHeaders == null) {
            return Collections.emptySet();
        }

        final Set<String> allowedHeaders = new TreeSet<>(String::compareToIgnoreCase);
        for (String header : allowRestrictedHeaders.split(",")) {
            allowedHeaders.add(header.trim());
        }

        return Collections.unmodifiableSet(allowedHeaders);
    }

    private Set<Integer> parseOkStatusCodeRanges(String okStatusCodeRanges) {
        try {
            final Set<Integer> okStatuses = new HashSet<>();

            for (String range : okStatusCodeRanges.split(",")) {
                final int dashIndex = range.indexOf("-");
                if (dashIndex == -1) {
                    okStatuses.add(Integer.parseInt(range.trim()));
                } else {
                    final String minValue = range.substring(0, dashIndex).trim();
                    final String maxValue = range.substring(dashIndex + 1).trim();

                    IntStream.rangeClosed(Integer.parseInt(minValue), Integer.parseInt(maxValue))
                            .forEach(okStatuses::add);
                }
            }

            return Collections.unmodifiableSet(okStatuses);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("OK StatusCode ranges has invalid format: " + okStatusCodeRanges, nfe);
        }
    }

    private URI getRequestUri(Exchange exchange)
            throws URISyntaxException {
        final Message message = exchange.getMessage();

        String uriStr = message.getHeader(JdkHttpConstants.HTTP_URI, String.class);
        if (uriStr == null || uriStr.isBlank()) {
            uriStr = httpUri.toASCIIString();
        }

        uriStr = exchange.getContext().resolvePropertyPlaceholders(uriStr);
        URI uri = URI.create(uriStr);

        final String scheme = message.getHeader(JdkHttpConstants.HTTP_SCHEME, String.class);
        if (scheme != null && !scheme.isBlank()) {
            final String schemeTrimmed = scheme.trim();
            if (!"http".equals(schemeTrimmed) && !"https".equals(schemeTrimmed)) {
                throw new IllegalArgumentException(JdkHttpConstants.HTTP_SCHEME + " header must provided only http or https value");
            }
            uri = JdkHttpHelper.setUriScheme(uri, schemeTrimmed);
        }

        final String host = message.getHeader(JdkHttpConstants.HTTP_HOST, String.class);
        uri = JdkHttpHelper.setUriHost(uri, host);

        final Integer port = message.getHeader(JdkHttpConstants.HTTP_PORT, Integer.class);
        if (port != null) {
            uri = JdkHttpHelper.setUriPort(uri, port);
        }

        final String path = message.getHeader(JdkHttpConstants.HTTP_PATH, String.class);
        uri = JdkHttpHelper.appendUriPath(uri, path);

        final String query = message.getHeader(JdkHttpConstants.HTTP_QUERY, String.class);
        if (query != null && !query.isBlank()) {
            uri = URISupport.createURIWithQuery(uri, UnsafeUriCharactersEncoder.encodeHttpURI(query.trim()));
        }

        return uri;
    }

    private String getRequestMethod(Message message) {
        if (httpMethod != null && !httpMethod.isBlank()) {
            return httpMethod;
        }

        final String method = message.getHeader(JdkHttpConstants.HTTP_METHOD, String.class);
        if (method != null && !method.isBlank()) {
            return method;
        }

        return message.getBody() != null ? "POST" : "GET";
    }

    private HttpRequest.BodyPublisher getRequestBody(Exchange exchange) throws CamelExchangeException {
        final Message message = exchange.getMessage();

        final Object body = message.getBody();
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }

        if (body instanceof HttpRequest.BodyPublisher bodyPublisher) {
            return bodyPublisher;
        } else if (body instanceof byte[] bytes) {
            return HttpRequest.BodyPublishers.ofByteArray(bytes);
        } else if (body instanceof String str) {
            final String contentType = JdkHttpHelper.getHeaderIgnoreCase(message, JdkHttpConstants.CONTENT_TYPE, String.class);
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

        final Long contentLength = JdkHttpHelper.getHeaderIgnoreCase(message, JdkHttpConstants.CONTENT_LENGTH, Long.class);
        if (contentLength != null) {
            inputStreamPublisher = HttpRequest.BodyPublishers.fromPublisher(inputStreamPublisher, contentLength);
        }

        return inputStreamPublisher;
    }

    private boolean getRequestExpect(Message message) {
        final String expectHeader = JdkHttpHelper.getHeaderIgnoreCase(message, JdkHttpConstants.EXPECT, String.class);

        return "100-continue".equals(expectHeader);
    }

    private Map<String, List<String>> getRequestHeaders(Exchange exchange) {
        final Message message = exchange.getMessage();
        final TypeConverter typeConverter = exchange.getContext().getTypeConverter();

        final Map<String, List<String>> filteredHeaders = new TreeMap<>(String::compareToIgnoreCase);
        for (Map.Entry<String, Object> header : message.getHeaders().entrySet()) {
            final String headerName = header.getKey();
            if (JdkHttpConstants.RESTRICTED_HEADERS.contains(headerName) && !ALLOW_RESTRICTED_HEADERS.contains(headerName)) {
                continue;
            }

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

    private void setResponseHeaders(HttpResponse<InputStream> httpResponse, Exchange exchange) {
        final Message message = exchange.getMessage();

        for (Map.Entry<String, List<String>> header : httpResponse.headers().map().entrySet()) {
            final String headerName = header.getKey();
            final List<String> headerValues = header.getValue();

            if (JdkHttpConstants.CONTENT_TYPE.equalsIgnoreCase(headerName) && !headerValues.isEmpty()) {
                exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(headerValues.get(0)));
            }

            final Object camelHeaderValue = headerValues.size() == 1 ?
                    headerValues.get(0) :
                    new ArrayList<>(headerValues);
            if (!headerFilterStrategy.applyFilterToExternalHeaders(headerName, camelHeaderValue, exchange)) {
                message.setHeader(headerName, camelHeaderValue);
            }
        }
    }

    private void setResponseBody(HttpResponse<InputStream> httpResponse, Exchange exchange)
            throws IOException {
        final Message message = exchange.getMessage();
        final InputStream body = httpResponse.body();

        if (body == null) {
            message.setBody(null);
        } else if (responseBodyAsByteArray) {
            message.setBody(body.readAllBytes());
        } else if (disableStreamCache) {
            message.setBody(body);
        } else {
            final OutputStreamBuilder streamCacheBuilder = OutputStreamBuilder.withExchange(exchange);
            IOHelper.copy(body, streamCacheBuilder);
            message.setBody(streamCacheBuilder.build());
        }
    }

}

