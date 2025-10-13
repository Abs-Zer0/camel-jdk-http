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

    private final HttpClient httpClient;
    private final JdkHttpBinding httpBinding;

    public JdkHttpProducer(JdkHttpEndpoint endpoint, HttpClient httpClient, JdkHttpBinding httpBinding) {
        super(endpoint);
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client cannot be null");
        this.httpBinding = Objects.requireNonNull(httpBinding, "JdkHttpBinding cannot be null");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final HttpResponse<InputStream> httpResponse;
        try {
            final HttpRequest httpRequest = httpBinding.httpRequestFromExchange(exchange);
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } finally {
            final Object body = exchange.getMessage().getBody();
            if (body instanceof Closeable closeable) {
                IOHelper.close(closeable);
            }
        }

        try {
            httpBinding.httpResponseToExchange(httpResponse, exchange);
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

}
