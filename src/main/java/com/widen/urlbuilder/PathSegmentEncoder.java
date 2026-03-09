package com.widen.urlbuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
 *   <li>General delimiters used as URI component separators: "/" "?" "#" "[" "]"</li>
 *   <li>The percent character "%" (unless part of percent-encoding)</li>
 *   <li>Any character outside the allowed set</li>
 * </ul>
 *
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

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

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
