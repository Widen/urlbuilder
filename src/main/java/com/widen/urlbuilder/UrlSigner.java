package com.widen.urlbuilder;

import java.util.Map;

/**
 * Signs a URL while {@link UrlBuilder#toString()} writes it.
 *
 * <p>The builder calls the signer once the path and the query parameters are known. The signer
 * receives a {@link SigningContext} that describes the unsigned URL, and it returns the query
 * parameters to append, typically a signature.
 *
 * <p>To sign the complete URL, sign {@link SigningContext#getStringToSign()}:
 * <pre>{@code
 * UrlBuilder builder = new UrlBuilder("cdn.example.com", "/videos/movie.mp4")
 *     .usingUrlSigner(context -> {
 *         String signature = hmacSha256(context.getStringToSign(), secretKey);
 *         return Collections.singletonMap("signature", signature);
 *     });
 * }</pre>
 *
 * <p>A value the signature must cover, such as an expiration, goes into the URL with
 * {@link SigningContext#addParameter(String, Object)} before the signer reads that text:
 * <pre>{@code
 * public class ExpiringHmacSigner implements UrlSigner {
 *     private final String secretKey;
 *
 *     public ExpiringHmacSigner(String secretKey) {
 *         this.secretKey = secretKey;
 *     }
 *
 *     public Map<String, ?> sign(SigningContext context) {
 *         context.addParameter("expires", Instant.now().plusSeconds(3600).getEpochSecond());
 *
 *         String signature = hmacSha256(context.getStringToSign(), secretKey);
 *         return Collections.singletonMap("signature", signature);
 *     }
 * }
 *
 * UrlBuilder builder = new UrlBuilder("cdn.example.com", "/videos/movie.mp4")
 *     .usingUrlSigner(new ExpiringHmacSigner(SECRET_KEY));
 * }</pre>
 *
 * @since 3.0.0
 */
@FunctionalInterface
public interface UrlSigner {
    
    /**
     * Signs the URL and returns the query parameters to append to it.
     *
     * <p>The builder appends the returned parameters after every other parameter and encodes them
     * with its query encoder, like parameters added with {@link UrlBuilder#addParameter(String, Object)}.
     * Return them unencoded; a value that is already percent-encoded is encoded twice. A value may be
     * any object, and the builder writes its {@code toString()}.
     *
     * <p>{@link SigningContext#getUrl()} does not include the returned parameters, so a signature over
     * that URL does not cover them. A value the signature must cover, such as an expiration, is added
     * with {@link SigningContext#addParameter(String, Object)} instead of being returned.
     *
     * @param context The URL being signed
     * @return The parameters to append, such as {@code "signature" -> "abc123"}. The builder ignores
     *         an entry with a blank key or a null value, and it treats a null map as empty.
     */
    Map<String, ?> sign(SigningContext context);
    
    /**
     * The URL being signed.
     *
     * <p>The context describes the URL the builder is about to write, and it accepts further
     * parameters that the signature must cover.
     */
    interface SigningContext {

        /**
         * Adds a query parameter to the URL being signed, after the builder's own parameters.
         *
         * <p>From this call on, {@link #getUrl()}, {@link #getEncodedQuery()}, and
         * {@link #getParameters()} include the parameter, so a signature over the URL covers it.
         * The builder writes it before the parameters that {@link UrlSigner#sign} returns.
         * The parameter belongs to this one signing, not to the builder: the next call to
         * {@link UrlBuilder#toString()} starts again from the builder's own parameters.
         *
         * <p>Add every parameter before reading the text to sign. A signature computed earlier
         * would not cover a parameter added later, so the context rejects the call.
         *
         * @param name The parameter name; a blank name adds nothing
         * @param value The parameter value, written with its {@code toString()} and encoded by the
         *        builder; null adds the name alone
         * @return This context for chaining
         * @throws IllegalStateException if the signer has already called {@link #getStringToSign()},
         *         {@link #getUrl()}, {@link #getEncodedQuery()}, or {@link #getEncodedPathAndQuery()}
         */
        SigningContext addParameter(String name, Object value);

        /**
         * Adds several query parameters to the URL being signed, in the map's iteration order.
         *
         * <p>It behaves as {@link #addParameter(String, Object)} called once for each entry.
         *
         * @param parameters The parameter names and values
         * @return This context for chaining
         */
        default SigningContext addParameters(Map<String, ?> parameters) {
            for (Map.Entry<String, ?> entry : parameters.entrySet()) {
                addParameter(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Returns the protocol (scheme) of the URL.
         * 
         * @return The protocol ("http" or "https"), or empty string if 
         *         using protocol-relative or hostname-relative generation modes
         *         (i.e., when no scheme is present in the URL)
         */
        String getProtocol();
        
        /**
         * Returns the hostname of the URL.
         * 
         * @return The hostname (e.g., "example.com", "cdn.example.com")
         */
        String getHostname();
        
        /**
         * Returns the port number of the URL.
         * 
         * @return The port set on the builder, or {@code null} when it is unset,
         *         80, or 443, whichever scheme the URL uses. A hostname-relative
         *         URL omits the port, but the port is still reported here.
         */
        Integer getPort();
        
        /**
         * Returns the encoded path of the URL.
         * 
         * <p>The path will be properly encoded according to RFC 3986 Section 3.3.
         * Multiple path segments are joined with "/" separators.
         * 
         * @return The encoded path (e.g., "/path/to/resource", "/my%20file.txt"),
         *         or "/" if no path is set
         */
        String getEncodedPath();
        
        /**
         * Returns the encoded query string of the URL.
         * 
         * <p>The query string will be properly encoded according to RFC 3986 Section 3.4.
         * Multiple parameters are joined with {@code "&"} separators. It includes the
         * parameters added with {@link #addParameter(String, Object)}.
         * 
         * @return The encoded query string without leading "?" 
         *         (e.g., {@code "key1=value1&key2=value2"}), or empty string if no 
         *         query parameters are set
         */
        String getEncodedQuery();
        
        /**
         * Returns the raw (unencoded) query parameters as a map.
         *
         * <p>This provides access to the individual query parameter keys and values
         * before encoding, which can be useful for signing schemes that need to
         * inspect or manipulate individual parameters.
         *
         * <p>If the same key appears multiple times, only the last value is included.
         * Values may be {@code null} or empty; such values are preserved as-is in
         * this map. When the URL is serialized, parameters whose value is null or
         * empty are rendered without an "=" sign (for example, {@code "key"} rather
         * than {@code "key="}).
         *
         * <p>The returned map is unmodifiable. It includes the parameters added with
         * {@link #addParameter(String, Object)} before the call.
         *
         * @return An unmodifiable map of raw query parameter keys to values
         *         (e.g., {@code {"key1" -> "value1", "key2" -> "value2"}}),
         *         or an empty map if no query parameters are set
         */
        Map<String, String> getParameters();

        /**
         * Returns the fragment of the URL.
         * 
         * <p>Note: Fragments are typically not included in signatures since
         * they are processed client-side only and not sent to the server.
         * 
         * @return The fragment without leading "#" (e.g., "section1"), 
         *         or null if no fragment is set
         */
        String getFragment();
        
        /**
         * Returns the complete unsigned URL string.
         * 
         * <p>This is the full URL that would be generated if no signing
         * was applied. It includes protocol, hostname, port, path, query
         * parameters, but not the fragment (unless included in your signing scheme).
         * 
         * <p>It includes the parameters added with {@link #addParameter(String, Object)}.
         *
         * @return The complete unsigned URL string
         *         (e.g., "https://example.com/path?key=value")
         */
        String getUrl();

        /**
         * Returns the text a signature over the whole URL should cover.
         *
         * <p>This is the unsigned URL as the builder writes it: scheme, host, port, path, and every
         * query parameter, including the parameters added with {@link #addParameter(String, Object)}.
         * It equals {@link #getUrl()} and is named for what a signer does with it. A scheme that signs
         * less signs {@link #getEncodedPathAndQuery()} or {@link #getEncodedPath()} instead.
         *
         * <p>A relative URL has less to sign. In protocol-relative mode the text starts with
         * {@code //} and has no scheme. In hostname-relative mode it starts with the path, so the
         * signature covers no scheme, host, or port and stays valid on any host.
         *
         * @return The text to sign (e.g., "https://example.com/path?key=value&amp;expires=1795127806")
         */
        default String getStringToSign() {
            return getUrl();
        }

        /**
         * Returns the encoded path followed by the encoded query string, without the scheme, host,
         * or port.
         *
         * <p>A signature over this text covers the path and every query parameter, including the
         * parameters added with {@link #addParameter(String, Object)}, and it stays valid when the
         * same link is served from another host.
         *
         * @return The path and query string (e.g., "/path?key=value"), or the path alone when the
         *         URL has no query parameters
         */
        default String getEncodedPathAndQuery() {
            String query = getEncodedQuery();
            return query.isEmpty() ? getEncodedPath() : getEncodedPath() + "?" + query;
        }
        
        /**
         * Returns whether the URL uses SSL (https).
         * 
         * @return true if using https, false if using http
         */
        boolean isSsl();
        
        /**
         * Returns the generation mode of the URL.
         * 
         * @return The generation mode (FULLY_QUALIFIED, PROTOCOL_RELATIVE, 
         *         HOSTNAME_RELATIVE, etc.)
         */
        UrlBuilder.GenerationMode getGenerationMode();
    }
}
