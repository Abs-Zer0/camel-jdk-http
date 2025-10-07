package abs.zer0.camel.jdk.http;

import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.jsse.SSLContextParameters;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class JdkHttpConfiguration {

    private URI httpUri;
    private String httpMethod;
    private HttpClient.Version httpVersion = HttpClient.Version.HTTP_1_1;

    private boolean throwExceptionOnFailure = true;
    private String okStatusCodeRanges = "200-299";
    private Set<Integer> okStatusCodes = IntStream.rangeClosed(200, 299).boxed().collect(Collectors.toUnmodifiableSet());
    private boolean disableStreamCache = false;
    private boolean responseBodyAsByteArray = false;

    private Duration connectTimeout = Duration.ofSeconds(30);
    private Duration responseTimeout;
    private int maxConnections = 20;
    private SSLContextParameters sslContextParameters;

    private HttpClient.Redirect redirectPolicy = HttpClient.Redirect.NORMAL;
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    private Integer http2Priority;
    private boolean useSystemProperties = false;
    private boolean async = false;

    private String proxyHost;
    private Integer proxyPort;


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

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public String getOkStatusCodeRanges() {
        return okStatusCodeRanges;
    }

    public synchronized void setOkStatusCodeRanges(String okStatusCodeRanges) {
        this.okStatusCodeRanges = Objects.requireNonNull(okStatusCodeRanges, "OK StatusCode ranges cannot be null");
        this.okStatusCodes = parseOkStatusCodeRanges();
    }

    public Set<Integer> getOkStatusCodes() {
        return okStatusCodes;
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

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

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


    @Override
    public String toString() {
        return "JdkHttpConfiguration{" +
                "httpUri=" + httpUri +
                ", httpMethod='" + httpMethod + '\'' +
                ", httpVersion=" + httpVersion +
                ", throwExceptionOnFailure=" + throwExceptionOnFailure +
                ", okStatusCodeRanges='" + okStatusCodeRanges + '\'' +
                ", okStatusCodes=" + okStatusCodes +
                ", disableStreamCache=" + disableStreamCache +
                ", responseBodyAsByteArray=" + responseBodyAsByteArray +
                ", connectTimeout=" + connectTimeout +
                ", responseTimeout=" + responseTimeout +
                ", maxConnections=" + maxConnections +
                ", sslContextParameters=" + sslContextParameters +
                ", redirectPolicy=" + redirectPolicy +
                ", headerFilterStrategy=" + headerFilterStrategy +
                ", http2Priority=" + http2Priority +
                ", useSystemProperties=" + useSystemProperties +
                ", async=" + async +
                ", proxyHost='" + proxyHost + '\'' +
                ", proxyPort=" + proxyPort +
                '}';
    }

    public JdkHttpConfiguration copy() {
        final JdkHttpConfiguration copied = new JdkHttpConfiguration();
        copied.httpUri = httpUri;
        copied.httpMethod = httpMethod;
        copied.httpVersion = httpVersion;
        copied.throwExceptionOnFailure = throwExceptionOnFailure;
        copied.okStatusCodeRanges = okStatusCodeRanges;
        copied.okStatusCodes = okStatusCodes;
        copied.disableStreamCache = disableStreamCache;
        copied.responseBodyAsByteArray = responseBodyAsByteArray;
        copied.connectTimeout = connectTimeout;
        copied.responseTimeout = responseTimeout;
        copied.maxConnections = maxConnections;
        copied.sslContextParameters = sslContextParameters;
        copied.redirectPolicy = redirectPolicy;
        copied.headerFilterStrategy = headerFilterStrategy;
        copied.http2Priority = http2Priority;
        copied.useSystemProperties = useSystemProperties;
        copied.async = async;
        copied.proxyHost = proxyHost;
        copied.proxyPort = proxyPort;

        return copied;
    }


    private Set<Integer> parseOkStatusCodeRanges() {
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

}
