package com.widen.urlbuilder;

import java.util.Base64;

/**
 * Utility class for URL-safe Base64 encoding and decoding.
 *
 * <p>Uses the "URL and Filename safe" Base64 alphabet defined in
 * <a href="https://tools.ietf.org/html/rfc4648#section-5">RFC 4648 §5</a>,
 * which replaces {@code +} with {@code -} and {@code /} with {@code _},
 * and omits padding ({@code =}) characters.</p>
 */
public final class UrlSafeBase64
{
    private UrlSafeBase64()
    {
        // utility class
    }

    /**
     * Encodes the given byte array to a URL-safe Base64 string without padding.
     *
     * @param data the bytes to encode
     * @return URL-safe Base64 encoded string
     */
    public static String encode(byte[] data)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Decodes a URL-safe Base64 string back to bytes.
     *
     * <p>Accepts input with or without padding characters.</p>
     *
     * @param encoded the URL-safe Base64 encoded string
     * @return the decoded bytes
     */
    public static byte[] decode(String encoded)
    {
        return Base64.getUrlDecoder().decode(encoded);
    }
}

