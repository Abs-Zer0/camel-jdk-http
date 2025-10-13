package abs.zer0.camel.jdk.http;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.IOHelper;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UriEndpoint(
        firstVersion = "4.14.0",
        scheme = "jdk-http",
        title = "JDK HTTP client",
        syntax = "jdk-http:httpUri",
        producerOnly = true,
        category = {Category.HTTP},
        lenientProperties = true,
        headersClass = JdkHttpConstants.class
)
@Metadata(
        excludeProperties = "bridgeErrorHandler,exceptionHandler,exchangePattern",
        annotations = {
                "protocol=http"
        }
)
public class JdkHttpEndpoint extends DefaultEndpoint implements EndpointServiceLocation, HeaderFilterStrategyAware {

    @UriParam(label = "advanced", description = "To use custom JDK HttpClient.")
    private HttpClient httpClient;

    @UriPath(name = "httpUri", description = "The URL of the HTTP endpoint to call.")
    @Metadata(required = true)
    private URI httpUri;
    @UriParam(label = "producer", description = "The HTTP method to use.")
    private String httpMethod;
    @UriParam(label = "advanced", defaultValue = "HTTP/1.1", description = "Requests a specific HTTP protocol version where possible." +
            " If this method is not invoked prior to building, then newly built clients will prefer HTTP/2." +
            " If set to HTTP/2, then each request will attempt to upgrade to HTTP/2." +
            " If the upgrade succeeds, then the response to this request will use HTTP/2 and all subsequent requests and responses to the same origin server will use HTTP/2." +
            " If the upgrade fails, then the response will be handled using HTTP/1.1")
    private HttpClient.Version httpVersion = HttpClient.Version.HTTP_1_1;

    @UriParam(label = "producer", defaultValue = "true", description = "Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server."
            + " This allows you to get all responses regardless of the HTTP status code.")
    private Boolean throwExceptionOnFailure;
    @UriParam(label = "advanced", defaultValue = "200-299", description = "The status codes which are considered a success response." +
            " The values are inclusive." +
            " Multiple ranges can be defined, separated by comma, e.g. 200-204,209,301-304." +
            " Each range must be a single number or from-to with the dash included.")
    private String okStatusCodeRanges;
    @UriParam(label = "producer", defaultValue = "false", description = "Determines whether or not the raw input stream is cached or not." +
            " The producer (camel-jdk-http) will by default cache the response body stream." +
            " If setting this option to true, then the producers will not cache the response body stream but use the response stream as-is (the stream can only be read once) as the message body.")
    private Boolean disableStreamCache;
    @UriParam(label = "producer", defaultValue = "false", description = "Determines whether or not the HTTP response body is converted to byte array or not." +
            " The producer (camel-jdk-http) will by default use InputStream for read HTTP response body.")
    private Boolean responseBodyAsByteArray;

    @UriParam(label = "timeout", defaultValue = "PT30S", description = "Sets the connect timeout duration for JDK HttpClient." +
            " In the case where a new connection needs to be established, if the connection cannot be established within the given duration, then HttpClient::send throws an HttpConnectTimeoutException, or HttpClient::sendAsync completes exceptionally with an HttpConnectTimeoutException." +
            " If a new connection does not need to be established, for example if a connection can be reused from a previous request, then this timeout duration has no effect.")
    private Duration connectTimeout = Duration.ofSeconds(30);
    @UriParam(label = "timeout", defaultValue = "infinite Duration", description = "Sets a timeout for HTTP request." +
            " If the response is not received within the specified timeout then an HttpTimeoutException is thrown from HttpClient::send or HttpClient::sendAsync completes exceptionally with an HttpTimeoutException.")
    private Duration responseTimeout;
    @UriParam(label = "advanced", defaultValue = "20", description = "The maximum number of connections.")
    private int maxConnections = 20;
    @UriParam(label = "security", description = "To configure security using SSLContextParameters."
            + " Important: Only one instance of org.apache.camel.support.jsse.SSLContextParameters is supported per JdkHttpComponent."
            + " If you need to use 2 or more different instances, you need to define a new JdkHttpComponent per instance you need.")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "advanced", defaultValue = "NORMAL", description = "Specifies whether requests will automatically follow redirects issued by the server." +
            " Normal policy means always redirect, except from HTTPS URLs to HTTP URLs.")
    private HttpClient.Redirect redirectPolicy = HttpClient.Redirect.NORMAL;
    @UriParam(label = "filter", description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced", description = "Sets the default priority for any HTTP/2 requests sent from JDK HttpClient." +
            " The value provided must be between 1 and 256 (inclusive).")
    private Integer http2Priority;
    @UriParam(label = "advanced", defaultValue = "false", description = "To use System Properties as fallback for configuration for configuring JDK HttpClient.")
    private boolean useSystemProperties = false;
    @UriParam(label = "async,advanced", defaultValue = "false", description = "To use asynchronous Camel Endpoint implementation and JDK HttpClient call.")
    private boolean async = false;

    @UriParam(label = "proxy", description = "Sets the proxy server host.")
    private String proxyHost;
    @UriParam(label = "proxy", description = "Sets the proxy server port.")
    private Integer proxyPort;

    public JdkHttpEndpoint(String endpointUri, JdkHttpComponent component) {
        super(endpointUri, component);
        this.httpUri = URI.create(endpointUri);
    }


    @Override
    public Producer createProducer() throws Exception {
        final HttpClient resolvedHttpClient = resolveHttpClient();
        if (httpClient != resolvedHttpClient) {
            setHttpClient(resolvedHttpClient);
        }

        final JdkHttpBinding httpBinding = new JdkHttpBinding(httpUri);
        setBindingParameters(httpBinding);

        return async ?
                new JdkHttpAsyncProducer(this, httpClient, httpBinding) :
                new JdkHttpProducer(this, httpClient, httpBinding);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from http endpoint");
    }

    @Override
    protected void doStop() throws Exception {
        closeHttpClient();
        super.doStop();
    }

    @Override
    public String getServiceUrl() {
        if (httpUri != null) {
            return httpUri.toString();
        }

        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }


    public HttpClient getHttpClient() {
        return httpClient;
    }

    public synchronized void setHttpClient(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "HTTP client cannot be null");
        if (this.httpClient != null) {
            closeHttpClient();
        }
        this.httpClient = httpClient;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    public void setHttpUri(URI httpUri) {
        this.httpUri = Objects.requireNonNull(httpUri, "HTTP URI cannot be null");
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "HTTP method cannot be null")
                .trim()
                .toUpperCase();
    }

    public HttpClient.Version getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(HttpClient.Version httpVersion) {
        this.httpVersion = Objects.requireNonNull(httpVersion, "HTTP version cannot be null");
    }

    public Boolean getThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public String getOkStatusCodeRanges() {
        return okStatusCodeRanges;
    }

    public void setOkStatusCodeRanges(String okStatusCodeRanges) {
        this.okStatusCodeRanges = Objects.requireNonNull(okStatusCodeRanges, "OK StatusCode ranges cannot be null");
    }

    public Boolean getDisableStreamCache() {
        return disableStreamCache;
    }

    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public Boolean getResponseBodyAsByteArray() {
        return responseBodyAsByteArray;
    }

    public void setResponseBodyAsByteArray(boolean responseBodyAsByteArray) {
        this.responseBodyAsByteArray = responseBodyAsByteArray;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "Connect timeout cannot be null");
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "Response timeout cannot be null");
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("Maximum number of connections cannot be less than 1");
        }
        this.maxConnections = maxConnections;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = Objects.requireNonNull(sslContextParameters, "Camel SSLContextParameters cannot be null");
    }

    public HttpClient.Redirect getRedirectPolicy() {
        return redirectPolicy;
    }

    public void setRedirectPolicy(HttpClient.Redirect redirectPolicy) {
        this.redirectPolicy = Objects.requireNonNull(redirectPolicy, "HTTP redirect policy cannot be null");
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = Objects.requireNonNull(headerFilterStrategy, "Camel HeaderFilterStrategy cannot be null");
    }

    public Integer getHttp2Priority() {
        return http2Priority;
    }

    public void setHttp2Priority(int http2Priority) {
        if (http2Priority < 1 || http2Priority > 256) {
            throw new IllegalArgumentException("HTTP/2 priority cannot be out of range [1, 256]");
        }
        this.http2Priority = http2Priority;
    }

    public boolean isUseSystemProperties() {
        return useSystemProperties;
    }

    public void setUseSystemProperties(boolean useSystemProperties) {
        this.useSystemProperties = useSystemProperties;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = Objects.requireNonNull(proxyHost, "HTTP proxy host cannot be null")
                .trim();
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }


    private synchronized HttpClient resolveHttpClient() throws GeneralSecurityException, IOException {
        if (httpClient != null) {
            return httpClient;
        }
        if (useSystemProperties) {
            return HttpClient.newHttpClient();
        }

        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(httpVersion)
                .connectTimeout(connectTimeout)
                .executor(Executors.newFixedThreadPool(maxConnections))
                .followRedirects(redirectPolicy);

        if (sslContextParameters != null && getCamelContext() != null) {
            httpClientBuilder.sslContext(sslContextParameters.createSSLContext(getCamelContext()));
        }

        if (http2Priority != null && httpVersion == HttpClient.Version.HTTP_2) {
            httpClientBuilder.priority(http2Priority);
        }

        if (proxyHost != null && !proxyHost.isBlank()) {
            final Integer checkedProxyPort = Objects.requireNonNull(proxyPort, "HTTP Proxy port is required");
            httpClientBuilder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(proxyHost, checkedProxyPort)));
        }

        return httpClientBuilder.build();
    }

    private void setBindingParameters(JdkHttpBinding httpBinding) {
        if (httpMethod != null && !httpMethod.isBlank()) {
            httpBinding.setHttpMethod(httpMethod);
        }

        if (throwExceptionOnFailure != null) {
            httpBinding.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        if (okStatusCodeRanges != null && !okStatusCodeRanges.isBlank()) {
            httpBinding.setOkStatusCodeRanges(okStatusCodeRanges);
        }
        if (disableStreamCache != null) {
            httpBinding.setDisableStreamCache(disableStreamCache);
        }
        if (responseBodyAsByteArray != null) {
            httpBinding.setResponseBodyAsByteArray(responseBodyAsByteArray);
        }

        if (responseTimeout != null) {
            httpBinding.setResponseTimeout(responseTimeout);
        }

        if (headerFilterStrategy != null) {
            httpBinding.setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

    private void closeHttpClient() {
        if (httpClient instanceof Closeable closeable) {
            IOHelper.close(closeable);
        } else if (httpClient != null) {
            httpClient.executor().ifPresent(executor -> {
                if (executor instanceof ExecutorService executorService) {
                    getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
                }
            });
        }
    }

}
