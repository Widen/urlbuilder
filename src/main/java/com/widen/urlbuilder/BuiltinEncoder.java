package com.widen.urlbuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Encoder implementation that uses {@link URLEncoder} and {@link URLDecoder}.
 * <p>
 * <b>Warning:</b> This encoder uses {@code application/x-www-form-urlencoded} encoding
 * which is designed for HTML form submissions, not for URL path segments or query parameters
 * per RFC 3986. It over-encodes characters that are allowed in URLs.
 * <p>
 * <b>For path segments:</b> Use {@link PathSegmentEncoder} instead. This encoder incorrectly
 * encodes characters like {@code @}, {@code :}, and sub-delimiters ({@code !$&'()*+,;=})
 * which RFC 3986 Section 3.3 allows unencoded in path segments.
 * <p>
 * <b>For query parameters:</b> Use {@link QueryParameterEncoder} instead. While this encoder
 * produces valid query strings, {@link QueryParameterEncoder} provides cleaner RFC 3986
 * compliant encoding.
 * <p>
 * This class is retained for backward compatibility. New code should use the appropriate
 * encoder for each URL component:
 * <ul>
 *   <li>{@link PathSegmentEncoder} for URL path segments</li>
 *   <li>{@link QueryParameterEncoder} for query parameter keys and values</li>
 * </ul>
 *
 * @see PathSegmentEncoder
 * @see QueryParameterEncoder
 * @see UrlBuilder#usingPathEncoder(Encoder)
 * @see UrlBuilder#usingQueryEncoder(Encoder)
 */
public class BuiltinEncoder implements Encoder
{
    /**
     * Encode a string using {@link URLEncoder}.
     * <p>
     * Note: {@link URLEncoder} encodes spaces as {@code +}, but this method
     * converts them to {@code %20} for URL compatibility.
     *
     * @param text the string to encode
     * @return the encoded string
     * @throws RuntimeException if UTF-8 encoding is not available
     */
    @Override
    public String encode(String text)
    {
        try
        {
            String encoded = URLEncoder.encode(text, "UTF-8");
            return encoded.replace("+", "%20");
        }
        catch (UnsupportedEncodingException uee)
        {
            throw new RuntimeException("UTF-8 encoding not found.");
        }
    }

    /**
     * Decode a percent-encoded string using {@link URLDecoder}.
     *
     * @param text the encoded string to decode
     * @return the decoded string
     * @throws RuntimeException if UTF-8 encoding is not available
     */
    @Override
    public String decode(String text)
    {
        try
        {
            return URLDecoder.decode(text, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not found.");
        }
    }
}
