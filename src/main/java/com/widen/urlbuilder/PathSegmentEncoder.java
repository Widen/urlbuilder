package com.widen.urlbuilder;

/**
 * Encoder implementation for URL path segments following RFC 3986 Section 3.3.
 * <p>
 * Path segments allow more characters unencoded than query strings:
 * <pre>
 * pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
 * unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
 * sub-delims    = "!" / "$" / "&amp;" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
 * </pre>
 * <p>
 * Characters that MUST be encoded in path segments:
 * <ul>
 *   <li>General delimiters used as URI component separators: {@code /} {@code ?} {@code #} {@code [} {@code ]}</li>
 *   <li>The percent character {@code %} (unless part of percent-encoding)</li>
 *   <li>Space and other whitespace</li>
 *   <li>Any character outside the ASCII printable range (encoded as UTF-8)</li>
 * </ul>
 * <p>
 * This is the default encoder for path segments in {@link UrlBuilder}.
 *
 * @see QueryParameterEncoder
 * @see UrlBuilder#usingPathEncoder(Encoder)
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.3">RFC 3986 Section 3.3</a>
 * @since 3.0.0
 */
public class PathSegmentEncoder implements Encoder {

    // Characters that do NOT need encoding in path segments (pchar minus pct-encoded)
    // unreserved: A-Z a-z 0-9 - . _ ~
    // sub-delims: ! $ & ' ( ) * + , ; =
    // additional: : @
    private static final String SAFE_CHARS = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@";

    /**
     * {@inheritDoc}
     * <p>
     * This implementation preserves unreserved characters, sub-delimiters, colon, and at-sign
     * per RFC 3986 Section 3.3. All other characters are percent-encoded as UTF-8 bytes.
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
     *
     * @throws IllegalArgumentException if the decoded bytes are not UTF-8, as in {@code caf%E9}
     */
    @Override
    public String decode(String text) {
        return PercentCodec.decode(text);
    }
}
