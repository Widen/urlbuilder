package com.widen.urlbuilder;

/**
 * Interface for URL encoding and decoding operations.
 * <p>
 * Different parts of a URL have different encoding requirements per RFC 3986:
 * <ul>
 *   <li><b>Path segments</b> - Use {@link PathSegmentEncoder} which allows sub-delimiters
 *       ({@code !$&'()*+,;=}) and {@code :@} unencoded</li>
 *   <li><b>Query parameters</b> - Use {@link QueryParameterEncoder} which encodes all
 *       special characters including sub-delimiters</li>
 * </ul>
 * <p>
 * Custom implementations can be provided via {@link UrlBuilder#usingPathEncoder(Encoder)}
 * and {@link UrlBuilder#usingQueryEncoder(Encoder)}.
 *
 * @see PathSegmentEncoder
 * @see QueryParameterEncoder
 * @see LegacyPathEncoder
 * @see NoEncodingEncoder
 */
public interface Encoder
{
    /**
     * Encode a string for safe inclusion in a URL component.
     * <p>
     * Characters that are not safe for the target URL component should be
     * percent-encoded (e.g., space becomes {@code %20}).
     *
     * @param text the string to encode, may be null
     * @return the encoded string, or empty string if input is null
     */
    String encode(String text);

    /**
     * Decode a percent-encoded string back to its original form.
     * <p>
     * Percent-encoded sequences (e.g., {@code %20}) are converted back to
     * their original characters (e.g., space).
     *
     * @param text the encoded string to decode, may be null
     * @return the decoded string, or empty string if input is null
     */
    String decode(String text);
}
