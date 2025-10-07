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
        scheme = "jdk-http",
        title = "JDK HTTP client",
        syntax = "jdk-http:protocol://host:port/path?query",
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
public class JdkHttpEndpoint extends DefaultEndpoint implements JdkHttpConfigurationAware {

    @UriParam(label = "configuration", description = "To use JdkHttpConfiguration as configuration when creating endpoints.")
    private JdkHttpConfiguration configuration;
    @UriParam(label = "advanced", description = "To use custom JDK HttpClient.")
    private volatile HttpClient httpClient;

    @UriPath
    private URI httpUri;
    @UriParam(label = "producer", description = "The HTTP method to use.")
    private String httpMethod;
    @UriParam(label = "advanced", defaultValue = "HTTP/1.1", description = "Requests a specific HTTP protocol version where possible." +
            " If this method is not invoked prior to building, then newly built clients will prefer HTTP/2." +
            " If set to HTTP/2, then each request will attempt to upgrade to HTTP/2." +
            " If the upgrade succeeds, then the response to this request will use HTTP/2 and all subsequent requests and responses to the same origin server will use HTTP/2." +
            " If the upgrade fails, then the response will be handled using HTTP/1.1")
    private HttpClient.Version httpVersion;

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
    private Duration connectTimeout;
    @UriParam(label = "timeout", defaultValue = "infinite Duration", description = "Sets a timeout for HTTP request." +
            " If the response is not received within the specified timeout then an HttpTimeoutException is thrown from HttpClient::send or HttpClient::sendAsync completes exceptionally with an HttpTimeoutException.")
    private Duration responseTimeout;
    @UriParam(label = "advanced", defaultValue = "20", description = "The maximum number of connections.")
    private Integer maxConnections;
    @UriParam(label = "security", description = "To configure security using SSLContextParameters."
            + " Important: Only one instance of org.apache.camel.support.jsse.SSLContextParameters is supported per JdkHttpComponent."
            + " If you need to use 2 or more different instances, you need to define a new JdkHttpComponent per instance you need.")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "advanced", defaultValue = "NORMAL", description = "Specifies whether requests will automatically follow redirects issued by the server." +
            " Normal policy means always redirect, except from HTTPS URLs to HTTP URLs.")
    private HttpClient.Redirect redirectPolicy;
    @UriParam(label = "filter", description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced", description = "Sets the default priority for any HTTP/2 requests sent from JDK HttpClient." +
            " The value provided must be between 1 and 256 (inclusive).")
    private Integer http2Priority;
    @UriParam(label = "advanced", defaultValue = "false", description = "To use System Properties as fallback for configuration for configuring JDK HttpClient.")
    private Boolean useSystemProperties;
    @UriParam(label = "async,advanced", defaultValue = "false", description = "To use asynchronous Camel Endpoint implementation and JDK HttpClient call.")
    private Boolean async;

    @UriParam(label = "proxy", description = "Sets the proxy server host.")
    private String proxyHost;
    @UriParam(label = "proxy", description = "Sets the proxy server port.")
    private Integer proxyPort;

    public JdkHttpEndpoint(String endpointUri, JdkHttpComponent component, JdkHttpConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        final JdkHttpConfiguration resolvedConfiguration = resolveConfiguration();
        final HttpClient resolvedHttpClient = resolveHttpClient(resolvedConfiguration);
        if (httpClient != resolvedHttpClient) {
            closeHttpClient();
            httpClient = resolvedHttpClient;
        }

        return resolvedConfiguration.isAsync() ?
                new JdkHttpAsyncProducer(this, resolvedConfiguration, httpClient) :
                new JdkHttpProducer(this, resolvedConfiguration, httpClient);
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


    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client cannot be null");
    }

    public JdkHttpConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JdkHttpConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "JdkHttpConfiguration cannot be null")
                .copy();
    }

    @Override
    public URI getHttpUri() {
        return httpUri;
    }

    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    @Override
    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    @Override
    public HttpClient.Version getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(HttpClient.Version httpVersion) {
        this.httpVersion = Objects.requireNonNull(httpVersion, "HTTP version cannot be null");
    }

    @Override
    public Boolean getThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    @Override
    public String getOkStatusCodeRanges() {
        return okStatusCodeRanges;
    }

    public void setOkStatusCodeRanges(String okStatusCodeRanges) {
        this.okStatusCodeRanges = Objects.requireNonNull(okStatusCodeRanges, "OK StatusCode ranges cannot be null");
    }

    @Override
    public Boolean getDisableStreamCache() {
        return disableStreamCache;
    }

    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    @Override
    public Boolean getResponseBodyAsByteArray() {
        return responseBodyAsByteArray;
    }

    public void setResponseBodyAsByteArray(boolean responseBodyAsByteArray) {
        this.responseBodyAsByteArray = responseBodyAsByteArray;
    }

    @Override
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "Connect timeout cannot be null");
    }

    @Override
    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "Response timeout cannot be null");
    }

    @Override
    public Integer getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = Objects.requireNonNull(sslContextParameters, "Camel SSLContextParameters cannot be null");
    }

    @Override
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

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = Objects.requireNonNull(headerFilterStrategy, "Camel HeaderFilterStrategy cannot be null");
    }

    @Override
    public Integer getHttp2Priority() {
        return http2Priority;
    }

    public void setHttp2Priority(int http2Priority) {
        this.http2Priority = http2Priority;
    }

    @Override
    public Boolean getUseSystemProperties() {
        return useSystemProperties;
    }

    public void setUseSystemProperties(boolean useSystemProperties) {
        this.useSystemProperties = useSystemProperties;
    }

    @Override
    public Boolean getAsync() {
        return async;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }

    @Override
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = Objects.requireNonNull(proxyHost, "HTTP proxy host cannot be null");
    }

    @Override
    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }


    private JdkHttpConfiguration resolveConfiguration() {
        final JdkHttpConfiguration resolvedConfiguration = configuration != null ?
                configuration.copy() :
                new JdkHttpConfiguration();
        setConfigurationParameters(resolvedConfiguration);

        return resolvedConfiguration;
    }

    private HttpClient resolveHttpClient(JdkHttpConfiguration resolvedConfiguration)
            throws GeneralSecurityException, IOException {
        if (httpClient != null) {
            return httpClient;
        }
        if (resolvedConfiguration.isUseSystemProperties()) {
            return HttpClient.newHttpClient();
        }

        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(resolvedConfiguration.getHttpVersion())
                .connectTimeout(resolvedConfiguration.getConnectTimeout())
                .executor(Executors.newFixedThreadPool(resolvedConfiguration.getMaxConnections()))
                .followRedirects(resolvedConfiguration.getRedirectPolicy());

        final SSLContextParameters resolvedSsl = resolvedConfiguration.getSslContextParameters();
        if (resolvedSsl != null && getCamelContext() != null) {
            httpClientBuilder.sslContext(resolvedSsl.createSSLContext(getCamelContext()));
        }

        final Integer resolvedHttp2Priority = resolvedConfiguration.getHttp2Priority();
        if (resolvedHttp2Priority != null && resolvedConfiguration.getHttpVersion() == HttpClient.Version.HTTP_2) {
            httpClientBuilder.priority(resolvedHttp2Priority);
        }

        final String resolvedProxyHost = resolvedConfiguration.getProxyHost();
        if (resolvedProxyHost != null && !resolvedProxyHost.isBlank()) {
            final Integer resolvedProxyPort = Objects.requireNonNull(resolvedConfiguration.getProxyPort(), "HTTP Proxy port is required");
            httpClientBuilder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(resolvedProxyHost, resolvedProxyPort)));
        }

        return httpClientBuilder.build();
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
