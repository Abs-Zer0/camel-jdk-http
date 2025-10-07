package abs.zer0.camel.jdk.http;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;

import java.io.Closeable;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public class JdkHttpProducer extends DefaultProducer {

    private final JdkHttpConfiguration configuration;
    private final HttpClient httpClient;

    public JdkHttpProducer(JdkHttpEndpoint endpoint, JdkHttpConfiguration configuration, HttpClient httpClient) {
        super(endpoint);
        this.configuration = Objects.requireNonNull(configuration, "JdkHttpConfiguration cannot be null")
                .copy();
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client cannot be null");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final HttpResponse<InputStream> httpResponse;
        try {
            final HttpRequest httpRequest = JdkHttpHelper.httpRequestFromExchange(exchange, configuration);
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } finally {
            final Object body = exchange.getMessage().getBody();
            if (body instanceof Closeable closeable) {
                IOHelper.close(closeable);
            }
        }

        try {
            JdkHttpHelper.httpResponseToExchange(httpResponse, exchange, configuration);
        } finally {
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    super.onDone(exchange);

                    IOHelper.close(httpResponse.body());
                }
            });
        }
    }


    public JdkHttpConfiguration getConfiguration() {
        return configuration.copy();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

}
