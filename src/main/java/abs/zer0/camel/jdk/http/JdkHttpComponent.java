package abs.zer0.camel.jdk.http;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component("jdk-http")
public class JdkHttpComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced", defaultValue = "HTTP/1.1", description = "Requests a specific HTTP protocol version where possible." +
            " If this method is not invoked prior to building, then newly built clients will prefer HTTP/2." +
            " If set to HTTP/2, then each request will attempt to upgrade to HTTP/2." +
            " If the upgrade succeeds, then the response to this request will use HTTP/2 and all subsequent requests and responses to the same origin server will use HTTP/2." +
            " If the upgrade fails, then the response will be handled using HTTP/1.1")
    private HttpClient.Version httpVersion;

    @Metadata(label = "producer", defaultValue = "true", description = "Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server."
            + " This allows you to get all responses regardless of the HTTP status code.")
    private Boolean throwExceptionOnFailure;
    @Metadata(label = "advanced", defaultValue = "200-299", description = "The status codes which are considered a success response." +
            " The values are inclusive." +
            " Multiple ranges can be defined, separated by comma, e.g. 200-204,209,301-304." +
            " Each range must be a single number or from-to with the dash included.")
    private String okStatusCodeRanges;
    @Metadata(label = "producer", defaultValue = "false", description = "Determines whether or not the raw input stream is cached or not." +
            " The producer (camel-jdk-http) will by default cache the response body stream." +
            " If setting this option to true, then the producers will not cache the response body stream but use the response stream as-is (the stream can only be read once) as the message body.")
    private Boolean disableStreamCache;
    @Metadata(label = "producer", defaultValue = "false", description = "Determines whether or not the HTTP response body is converted to byte array or not." +
            " The producer (camel-jdk-http) will by default use InputStream for read HTTP response body.")
    private Boolean responseBodyAsByteArray;

    @Metadata(label = "timeout", defaultValue = "PT30S", description = "Sets the connect timeout duration for JDK HttpClient." +
            " In the case where a new connection needs to be established, if the connection cannot be established within the given duration, then HttpClient::send throws an HttpConnectTimeoutException, or HttpClient::sendAsync completes exceptionally with an HttpConnectTimeoutException." +
            " If a new connection does not need to be established, for example if a connection can be reused from a previous request, then this timeout duration has no effect.")
    private Duration connectTimeout;
    @Metadata(label = "timeout", defaultValue = "infinite Duration", description = "Sets a timeout for HTTP request." +
            " If the response is not received within the specified timeout then an HttpTimeoutException is thrown from HttpClient::send or HttpClient::sendAsync completes exceptionally with an HttpTimeoutException.")
    private Duration responseTimeout;
    @Metadata(label = "advanced", defaultValue = "20", description = "The maximum number of connections.")
    private Integer maxConnections;
    @Metadata(label = "security", description = "To configure security using SSLContextParameters."
            + " Important: Only one instance of org.apache.camel.support.jsse.SSLContextParameters is supported per JdkHttpComponent."
            + " If you need to use 2 or more different instances, you need to define a new JdkHttpComponent per instance you need.")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false", description = "Enable usage of global SSL context parameters.")
    private boolean useGlobalSslContextParameters = false;

    @Metadata(label = "advanced", defaultValue = "NORMAL", description = "Specifies whether requests will automatically follow redirects issued by the server." +
            " Normal policy means always redirect, except from HTTPS URLs to HTTP URLs.")
    private HttpClient.Redirect redirectPolicy;
    @Metadata(label = "advanced", description = "Sets the default priority for any HTTP/2 requests sent from JDK HttpClient." +
            " The value provided must be between 1 and 256 (inclusive).")
    private Integer http2Priority;
    @Metadata(label = "advanced", defaultValue = "false", description = "To use System Properties as fallback for configuration for configuring JDK HttpClient.")
    private Boolean useSystemProperties;
    @Metadata(label = "async,advanced", defaultValue = "false", description = "To use asynchronous Camel Endpoint implementation and JDK HttpClient call.")
    private Boolean async;

    @Metadata(label = "proxy", description = "Sets the proxy server host.")
    private String proxyHost;
    @Metadata(label = "proxy", description = "Sets the proxy server port.")
    private Integer proxyPort;


    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final JdkHttpEndpoint httpEndpoint = new JdkHttpEndpoint(uri, this);
        setEndpointParameters(httpEndpoint);
        setProperties(httpEndpoint, parameters);

        final URI remainingUri = URI.create(UnsafeUriCharactersEncoder.encodeHttpURI(remaining));
        final URI httpUri = URISupport.createRemainingURI(remainingUri, parameters);
        httpEndpoint.setHttpUri(httpUri);

        return httpEndpoint;
    }


    public HttpClient.Version getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(HttpClient.Version httpVersion) {
        this.httpVersion = Objects.requireNonNull(httpVersion, "HTTP version cannot be null");
    }

    public Boolean isThrowExceptionOnFailure() {
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

    public Boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public Boolean isResponseBodyAsByteArray() {
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

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = Objects.requireNonNull(sslContextParameters, "Camel SSLContextParameters cannot be null");
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public HttpClient.Redirect getRedirectPolicy() {
        return redirectPolicy;
    }

    public void setRedirectPolicy(HttpClient.Redirect redirectPolicy) {
        this.redirectPolicy = Objects.requireNonNull(redirectPolicy, "HTTP redirect policy cannot be null");
    }

    public Integer getHttp2Priority() {
        return http2Priority;
    }

    public void setHttp2Priority(int http2Priority) {
        this.http2Priority = http2Priority;
    }

    public Boolean isUseSystemProperties() {
        return useSystemProperties;
    }

    public void setUseSystemProperties(boolean useSystemProperties) {
        this.useSystemProperties = useSystemProperties;
    }

    public Boolean getAsync() {
        return async;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = Objects.requireNonNull(proxyHost, "HTTP proxy host cannot be null");
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }


    private void setEndpointParameters(JdkHttpEndpoint httpEndpoint) {
        if (httpVersion != null) {
            httpEndpoint.setHttpVersion(httpVersion);
        }

        if (throwExceptionOnFailure != null) {
            httpEndpoint.setThrowExceptionOnFailure(throwExceptionOnFailure);
        }
        if (okStatusCodeRanges != null && !okStatusCodeRanges.isBlank()) {
            httpEndpoint.setOkStatusCodeRanges(okStatusCodeRanges);
        }
        if (disableStreamCache != null) {
            httpEndpoint.setDisableStreamCache(disableStreamCache);
        }
        if (responseBodyAsByteArray != null) {
            httpEndpoint.setResponseBodyAsByteArray(responseBodyAsByteArray);
        }

        if (connectTimeout != null) {
            httpEndpoint.setConnectTimeout(connectTimeout);
        }
        if (responseTimeout != null) {
            httpEndpoint.setResponseTimeout(responseTimeout);
        }
        if (maxConnections != null) {
            httpEndpoint.setMaxConnections(maxConnections);
        }
        if (sslContextParameters != null) {
            httpEndpoint.setSslContextParameters(sslContextParameters);
        } else {
            final SSLContextParameters globalSslContextParameters = retrieveGlobalSslContextParameters();
            if (globalSslContextParameters != null) {
                httpEndpoint.setSslContextParameters(globalSslContextParameters);
            }
        }

        if (redirectPolicy != null) {
            httpEndpoint.setRedirectPolicy(redirectPolicy);
        }
        if (http2Priority != null) {
            httpEndpoint.setHttp2Priority(http2Priority);
        }
        if (useSystemProperties != null) {
            httpEndpoint.setUseSystemProperties(useSystemProperties);
        }
        if (async != null) {
            httpEndpoint.setAsync(async);
        }

        if (proxyHost != null && !proxyHost.isBlank()) {
            httpEndpoint.setProxyHost(proxyHost);
            httpEndpoint.setProxyPort(Objects.requireNonNull(proxyPort, "HTTP proxy port cannot be null"));
        }
    }

}
