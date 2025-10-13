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

public final class JdkHttpBinding {

    private final URI httpUri;
    private String httpMethod;

    private boolean throwExceptionOnFailure = true;
    private Set<Integer> okStatusCodes = IntStream.rangeClosed(200, 299).boxed().collect(Collectors.toUnmodifiableSet());
    private boolean disableStreamCache = false;
    private boolean responseBodyAsByteArray = false;

    private Duration responseTimeout;

    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();

    public JdkHttpBinding(URI httpUri) {
        this.httpUri = Objects.requireNonNull(httpUri, "HTTP URI cannot be null");
    }


    public HttpRequest httpRequestFromExchange(Exchange exchange)
            throws URISyntaxException, CamelExchangeException {
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

    public void httpResponseToExchange(HttpResponse<InputStream> httpResponse, Exchange exchange)
            throws HttpOperationFailedException, IOException {
        final Message message = exchange.getMessage();

        final int statusCode = httpResponse.statusCode();
        final String statusText = JdkHttpHelper.HTTP_STATUSES.get(statusCode);
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


    public URI getHttpUri() {
        return httpUri;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "HTTP method cannot be null")
                .trim()
                .toUpperCase();
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public Set<Integer> getOkStatusCodes() {
        return okStatusCodes;
    }

    public synchronized void setOkStatusCodeRanges(String okStatusCodeRanges) {
        Objects.requireNonNull(okStatusCodeRanges, "OK StatusCode ranges cannot be null");
        this.okStatusCodes = parseOkStatusCodeRanges(okStatusCodeRanges);
    }

    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public boolean isResponseBodyAsByteArray() {
        return responseBodyAsByteArray;
    }

    public void setResponseBodyAsByteArray(boolean responseBodyAsByteArray) {
        this.responseBodyAsByteArray = responseBodyAsByteArray;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "Response timeout cannot be null");
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = Objects.requireNonNull(headerFilterStrategy, "Camel HeaderFilterStrategy cannot be null");
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
