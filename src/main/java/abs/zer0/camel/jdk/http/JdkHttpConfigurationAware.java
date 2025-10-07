package abs.zer0.camel.jdk.http;

import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.jsse.SSLContextParameters;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

public interface JdkHttpConfigurationAware {

    default URI getHttpUri() {
        return null;
    }

    default String getHttpMethod() {
        return null;
    }

    default HttpClient.Version getHttpVersion() {
        return null;
    }

    default Boolean getThrowExceptionOnFailure() {
        return null;
    }

    default String getOkStatusCodeRanges() {
        return null;
    }

    default Boolean getDisableStreamCache() {
        return null;
    }

    default Boolean getResponseBodyAsByteArray() {
        return null;
    }

    default Duration getConnectTimeout() {
        return null;
    }

    default Duration getResponseTimeout() {
        return null;
    }

    default Integer getMaxConnections() {
        return null;
    }

    default SSLContextParameters getSslContextParameters() {
        return null;
    }

    default HttpClient.Redirect getRedirectPolicy() {
        return null;
    }

    default HeaderFilterStrategy getHeaderFilterStrategy() {
        return null;
    }

    default Integer getHttp2Priority() {
        return null;
    }

    default Boolean getUseSystemProperties() {
        return null;
    }

    default Boolean getAsync() {
        return null;
    }

    default String getProxyHost() {
        return null;
    }

    default Integer getProxyPort() {
        return null;
    }

    default void setConfigurationParameters(JdkHttpConfiguration configuration) {
        if (getHttpUri() != null) {
            configuration.setHttpUri(getHttpUri());
        }
        if (getHttpMethod() != null && !getHttpMethod().isBlank()) {
            configuration.setHttpMethod(getHttpMethod().toUpperCase());
        }
        if (getHttpVersion() != null) {
            configuration.setHttpVersion(getHttpVersion());
        }

        if (getThrowExceptionOnFailure() != null) {
            configuration.setThrowExceptionOnFailure(getThrowExceptionOnFailure());
        }
        if (getOkStatusCodeRanges() != null && !getOkStatusCodeRanges().isBlank()) {
            configuration.setOkStatusCodeRanges(getOkStatusCodeRanges());
        }
        if (getDisableStreamCache() != null) {
            configuration.setDisableStreamCache(getDisableStreamCache());
        }
        if (getResponseBodyAsByteArray() != null) {
            configuration.setResponseBodyAsByteArray(getResponseBodyAsByteArray());
        }

        if (getConnectTimeout() != null) {
            configuration.setConnectTimeout(getConnectTimeout());
        }
        if (getResponseTimeout() != null) {
            configuration.setResponseTimeout(getResponseTimeout());
        }
        if (getMaxConnections() != null) {
            configuration.setMaxConnections(getMaxConnections());
        }
        if (getSslContextParameters() != null) {
            configuration.setSslContextParameters(getSslContextParameters());
        }

        if (getRedirectPolicy() != null) {
            configuration.setRedirectPolicy(getRedirectPolicy());
        }
        if (getHeaderFilterStrategy() != null) {
            configuration.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
        if (getHttp2Priority() != null) {
            configuration.setHttp2Priority(getHttp2Priority());
        }
        if (getUseSystemProperties() != null) {
            configuration.setUseSystemProperties(getUseSystemProperties());
        }
        if (getAsync() != null) {
            configuration.setAsync(getAsync());
        }

        if (getProxyHost() != null && !getProxyHost().isBlank()) {
            configuration.setProxyHost(getProxyHost());
        }
        if (getProxyPort() != null) {
            configuration.setProxyPort(getProxyPort());
        }
    }

}
