package com.widen.urlbuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Legacy encoder that provides backward compatibility with urlbuilder v2.x path encoding behavior.
 * <p>
 * This encoder uses {@link URLEncoder} which implements {@code application/x-www-form-urlencoded}
 * encoding. This is technically incorrect for URL path segments per RFC 3986, but is provided
 * for users who need to maintain URL compatibility during migration from v2.x to v3.x.
 * <p>
 * <b>Note:</b> New code should use {@link PathSegmentEncoder} for RFC 3986 compliant path encoding.
 * This class is provided only for backward compatibility.
 * <p>
 * Usage:
 * <pre>
 * // Enable v2-compatible path encoding
 * new UrlBuilder("host.com", "path")
 *     .usingLegacyPathEncoding()
 *     .toString();
 * </pre>
 *
 * @see PathSegmentEncoder
 * @see UrlBuilder#usingLegacyPathEncoding()
 * @since 3.0.0
 * @deprecated Use {@link PathSegmentEncoder} for new code. This encoder exists only for
 *             backward compatibility with v2.x URL output.
 */
@Deprecated
public class LegacyPathEncoder implements Encoder {

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses {@link URLEncoder} for v2.x-compatible encoding.
     * Spaces are encoded as {@code %20} (not {@code +}).
     *
     * @throws RuntimeException if UTF-8 encoding is not available
     */
    @Override
    public String encode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        try {
            String encoded = URLEncoder.encode(text, "UTF-8");
            // URLEncoder encodes space as '+', but URLs use '%20'
            return encoded.replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not found.", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses {@link URLDecoder} for decoding.
     *
     * @throws RuntimeException if UTF-8 encoding is not available
     */
    @Override
    public String decode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not found.", e);
        }
    }
}
