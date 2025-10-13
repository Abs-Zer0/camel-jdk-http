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

    private final HttpClient httpClient;
    private final JdkHttpBinding httpBinding;

    public JdkHttpAsyncProducer(JdkHttpEndpoint endpoint, HttpClient httpClient, JdkHttpBinding httpBinding) {
        super(endpoint);
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client cannot be null");
        this.httpBinding = Objects.requireNonNull(httpBinding, "JdkHttpBinding cannot be null");
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            final HttpRequest httpRequest = httpBinding.httpRequestFromExchange(exchange);
            final Object requestBody = exchange.getMessage().getBody();

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
                    .handle(asyncHandler(exchange, callback, requestBody));
        } catch (CamelExchangeException | URISyntaxException e) {
            exchange.setException(e);
            callback.done(true);

            return true;
        }

        return false;
    }

    private BiFunction<HttpResponse<InputStream>, Throwable, Void> asyncHandler(Exchange exchange, AsyncCallback callback, Object requestBody) {
        return (httpResponse, throwable) -> {
            if (requestBody instanceof Closeable closeable) {
                IOHelper.close(closeable);
            }

            if (throwable != null) {
                exchange.setException(throwable);
                callback.done(false);
            } else {
                try {
                    httpBinding.httpResponseToExchange(httpResponse, exchange);
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
