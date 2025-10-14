package abs.zer0.camel.jdk.http;

import org.apache.camel.Message;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public final class JdkHttpHelper {

    public static <T> T getHeaderIgnoreCase(Message message, String name, Class<T> type) {
        Objects.requireNonNull(message, "Camel Message cannot be null");
        Objects.requireNonNull(name, "Header name cannot be null");

        return message.getHeaders().keySet()
                .stream()
                .filter(name::equalsIgnoreCase)
                .findFirst()
                .map(s -> message.getHeader(s, type))
                .orElse(null);
    }

    public static URI setUriScheme(URI uri, String scheme) throws URISyntaxException {
        Objects.requireNonNull(uri, "HTTP URI cannot be null");

        if (scheme == null || scheme.isBlank()) {
            return uri;
        }

        return new URI(
                scheme.trim(),
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
        );
    }

    public static URI setUriHost(URI uri, String host) throws URISyntaxException {
        Objects.requireNonNull(uri, "HTTP URI cannot be null");

        if (host == null || host.isBlank()) {
            return uri;
        }

        return new URI(
                uri.getScheme(),
                uri.getUserInfo(),
                host.trim(),
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
        );
    }

    public static URI setUriPort(URI uri, int port) throws URISyntaxException {
        Objects.requireNonNull(uri, "HTTP URI cannot be null");

        return new URI(
                uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                port,
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
        );
    }

    public static URI appendUriPath(URI uri, String path) throws URISyntaxException {
        Objects.requireNonNull(uri, "HTTP URI cannot be null");

        if (path == null || path.isBlank()) {
            return uri;
        }

        String encodedPath = UnsafeUriCharactersEncoder.encodeHttpURI(path.trim());
        if (uri.getPath() != null && uri.getPath().endsWith("/") && encodedPath.startsWith("/")) {
            encodedPath = encodedPath.substring(1);
        } else if (uri.getPath() != null && !uri.getPath().endsWith("/") && !encodedPath.startsWith("/")) {
            encodedPath = "/" + encodedPath;
        }

        return new URI(
                uri.getScheme(),
                uri.getUserInfo(),
                uri.getHost(),
                uri.getPort(),
                uri.getPath() + encodedPath,
                uri.getQuery(),
                uri.getFragment()
        );
    }

    private JdkHttpHelper() {
    }

}
