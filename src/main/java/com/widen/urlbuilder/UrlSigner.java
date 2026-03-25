package com.widen.urlbuilder;

import java.util.Map;

/**
 * Functional interface for signing URLs during toString() generation.
 * 
 * <p>A URL signer receives contextual information about the URL being built
 * and returns a map of query parameters to append to the URL (typically 
 * including a signature parameter).
 * 
 * <p>The signer is invoked after all path segments and query parameters 
 * have been added but before the final URL string is constructed. This allows
 * the signer to inspect the complete unsigned URL and generate appropriate
 * signature parameters.
 * 
 * <p>Example usage with lambda:
 * <pre>{@code
 * UrlBuilder builder = new UrlBuilder().withHostname("cdn.example.com")
 *     .withPath("/videos/movie.mp4")
 *     .usingUrlSigner(context -> {
 *         String signature = hmacSha256(context.getUrl(), secretKey);
 *         return Collections.singletonMap("signature", signature);
 *     });
 * }</pre>
 * 
 * <p>Example usage with class:
 * <pre>{@code
 * public class HmacUrlSigner implements UrlSigner {
 *     private final String secretKey;
 *     
 *     public HmacUrlSigner(String secretKey) {
 *         this.secretKey = secretKey;
 *     }
 *     
 *     public Map<String, String> sign(SigningContext context) {
 *         String signature = hmacSha256(context.getUrl(), secretKey);
 *         Map<String, String> params = new HashMap<>();
 *         params.put("signature", signature);
 *         params.put("expires", String.valueOf(System.currentTimeMillis() / 1000 + 3600));
 *         return params;
 *     }
 * }
 * 
 * UrlBuilder builder = new UrlBuilder().withHostname("cdn.example.com")
 *     .usingUrlSigner(new HmacUrlSigner(SECRET_KEY));
 * }</pre>
 * 
 * @since 3.0.0
 */
@FunctionalInterface
public interface UrlSigner {
    
    /**
     * Sign the URL and return parameters to append.
     * 
     * <p>The returned map should contain query parameter names and values
     * that will be appended to the URL. Common examples include:
     * <ul>
     *   <li>"signature" - The cryptographic signature</li>
     *   <li>"expires" - Expiration timestamp</li>
     *   <li>"key-id" - Key identifier used for signing</li>
     * </ul>
     * 
     * <p>The returned parameter names and values are appended to the query
     * string as-is, without any additional URL encoding. Both keys and values
     * must already be safe for inclusion in a URL query string (encoded if
     * necessary) before being returned.
     * @param context Contextual information about the URL being signed
     * @return Map of query parameters to append (e.g., "signature" -> "abc123").
     *         Returns empty map if no parameters should be added.
     *         Null values in the map will be ignored during URL construction.
     *         Returns null to skip signing (treated as empty map).
     */
    Map<String, String> sign(SigningContext context);
    
    /**
     * Context information provided to the signer.
     * 
     * <p>This interface provides read-only access to the URL components
     * that have been built so far, allowing the signer to generate an
     * appropriate signature based on the complete URL structure.
     */
    interface SigningContext {
        
        /**
         * Returns the protocol (scheme) of the URL.
         * 
         * @return The protocol ("http" or "https"), or empty string if 
         *         using protocol-relative generation mode
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
         * @return The port number, or -1 if using the default port
         *         (80 for http, 443 for https)
         */
        int getPort();
        
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
         * Multiple parameters are joined with "&" separators.
         * 
         * @return The encoded query string without leading "?" 
         *         (e.g., "key1=value1&key2=value2"), or empty string if no 
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
         * Parameters with null or blank values will have an empty string as the value.
         *
         * <p>The returned map is unmodifiable.
         *
         * @return An unmodifiable map of raw query parameter keys to values
         *         (e.g., {"key1" -> "value1", "key2" -> "value2"}),
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
         * <p>This is typically what you want to sign.
         * 
         * @return The complete unsigned URL string
         *         (e.g., "https://example.com/path?key=value")
         */
        String getUrl();
        
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
