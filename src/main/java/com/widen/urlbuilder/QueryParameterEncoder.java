package com.widen.urlbuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * {@inheritDoc}
     * <p>
     * This implementation only preserves unreserved characters per RFC 3986.
     * All other characters including sub-delimiters are percent-encoded as UTF-8 bytes.
     */
    @Override
    public String encode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            int unsignedByte = b & 0xFF;
            
            if (unsignedByte < 128 && SAFE_CHARS.indexOf((char) unsignedByte) >= 0) {
                // Safe ASCII character - no encoding needed
                encoded.append((char) unsignedByte);
            } else {
                // Percent-encode this byte
                encoded.append('%');
                encoded.append(HEX_DIGITS[(unsignedByte >> 4) & 0x0F]);
                encoded.append(HEX_DIGITS[unsignedByte & 0x0F]);
            }
        }

        return encoded.toString();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Decodes percent-encoded sequences back to their original UTF-8 characters.
     * Invalid percent sequences (e.g., {@code %GG}) are passed through unchanged.
     */
    @Override
    public String decode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);
            
            if (c == '%' && i + 2 < text.length()) {
                int high = Character.digit(text.charAt(i + 1), 16);
                int low = Character.digit(text.charAt(i + 2), 16);
                
                if (high >= 0 && low >= 0) {
                    baos.write((high << 4) | low);
                    i += 3;
                    continue;
                }
            }
            
            // Write character as UTF-8 bytes
            if (c < 128) {
                baos.write(c);
            } else {
                byte[] charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                baos.write(charBytes, 0, charBytes.length);
            }
            i++;
        }

        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
}
