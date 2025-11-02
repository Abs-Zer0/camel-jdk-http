package abs.zer0.camel.jdk.http;

import org.apache.camel.Message;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Helper class for the JDK HTTP component.
 */
public final class JdkHttpHelper {

    /**
     * Gets header value from {@link Message} by name ignoring case.
     *
     * @param message The message from which to get the header value.
     * @param name    The name of the header.
     * @param type    The type of the header value that should be converted to.
     * @return The header value or null if not found.
     */
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

    /**
     * Creates a new URI with the given scheme.
     *
     * @param uri    The URI to use as base.
     * @param scheme The scheme to use.
     * @return The new URI with the given scheme.
     * @throws URISyntaxException If the URI is invalid.
     */
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

    /**
     * Creates a new URI with the given host.
     *
     * @param uri  The URI to use as base.
     * @param host The host to use.
     * @return The new URI with the given host.
     * @throws URISyntaxException If the URI is invalid.
     */
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

    /**
     * Creates a new URI with the given port.
     *
     * @param uri  The URI to use as base.
     * @param port The port to use.
     * @return The new URI with the given port.
     * @throws URISyntaxException If the URI is invalid.
     */
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

    /**
     * Creates a new URI with appended given path.
     *
     * @param uri  The URI to use as base.
     * @param path The path to be added.
     * @return The new URI with the given path.
     * @throws URISyntaxException If the URI is invalid.
     */
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
