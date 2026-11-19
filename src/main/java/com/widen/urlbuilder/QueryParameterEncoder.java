package com.widen.urlbuilder;

/**
 * Encoder implementation for URL query parameter keys and values following RFC 3986 Section 3.4.
 * <p>
 * Query strings have stricter encoding requirements than path segments because
 * characters like {@code &} and {@code =} are used as delimiters in the common
 * {@code key=value&key2=value2} format.
 * <p>
 * Characters that are safe (not encoded) in query parameters:
 * <ul>
 *   <li>Unreserved characters: {@code A-Z a-z 0-9 - . _ ~}</li>
 * </ul>
 * <p>
 * Characters that MUST be encoded:
 * <ul>
 *   <li>Sub-delimiters: {@code ! $ &amp; ' ( ) * + , ; =}</li>
 *   <li>General delimiters: {@code : / ? # [ ] @}</li>
 *   <li>Space and other whitespace</li>
 *   <li>The percent character {@code %} (unless part of percent-encoding)</li>
 *   <li>Any character outside the ASCII printable range (encoded as UTF-8)</li>
 * </ul>
 * <p>
 * This is the default encoder for query parameters in {@link UrlBuilder}.
 *
 * @see PathSegmentEncoder
 * @see UrlBuilder#usingQueryEncoder(Encoder)
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.4">RFC 3986 Section 3.4</a>
 * @since 3.0.0
 */
public class QueryParameterEncoder implements Encoder {

    // Only unreserved characters are safe in query parameter keys/values
    // unreserved: A-Z a-z 0-9 - . _ ~
    private static final String SAFE_CHARS = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    /**
     * {@inheritDoc}
     * <p>
     * This implementation only preserves unreserved characters per RFC 3986.
     * All other characters including sub-delimiters are percent-encoded as UTF-8 bytes.
     */
    @Override
    public String encode(String text) {
        return PercentCodec.encode(text, SAFE_CHARS);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Decodes percent-encoded sequences back to their original UTF-8 characters.
     * Invalid percent sequences (e.g., {@code %GG}) are passed through unchanged.
     */
    @Override
    public String decode(String text) {
        return PercentCodec.decode(text);
    }
}
