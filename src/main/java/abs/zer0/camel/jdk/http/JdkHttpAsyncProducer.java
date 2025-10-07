package abs.zer0.camel.jdk.http;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;

import java.io.Closeable;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.BiFunction;

public class JdkHttpAsyncProducer extends DefaultAsyncProducer {

    private final JdkHttpConfiguration configuration;
    private final HttpClient httpClient;

    public JdkHttpAsyncProducer(JdkHttpEndpoint endpoint, JdkHttpConfiguration configuration, HttpClient httpClient) {
        super(endpoint);
        this.configuration = Objects.requireNonNull(configuration, "JdkHttpConfiguration cannot be null")
                .copy();
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client cannot be null");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            final HttpRequest httpRequest = JdkHttpHelper.httpRequestFromExchange(exchange, configuration);
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .handle(asyncHandler(exchange, callback));
        } catch (CamelExchangeException | URISyntaxException e) {
            exchange.setException(e);
            callback.done(true);

            return true;
        }

        return false;
    }


    public JdkHttpConfiguration getConfiguration() {
        return configuration.copy();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }


    private BiFunction<HttpResponse<InputStream>, Throwable, Void> asyncHandler(Exchange exchange, AsyncCallback callback) {
        return (httpResponse, throwable) -> {
            final Object body = exchange.getMessage().getBody();
            if (body instanceof Closeable closeable) {
                IOHelper.close(closeable);
            }

            if (throwable != null) {
                exchange.setException(throwable);
                callback.done(false);
            } else {
                try {
                    JdkHttpHelper.httpResponseToExchange(httpResponse, exchange, configuration);
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                        @Override
                        public void onDone(Exchange exchange) {
                            super.onDone(exchange);

                            IOHelper.close(httpResponse.body());
                        }
                    });
                    callback.done(false);
                }
            }

            return null;
        };
    }

}
