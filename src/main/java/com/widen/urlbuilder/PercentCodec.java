package com.widen.urlbuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Percent-encoding of UTF-8 text per RFC 3986 Section 2.1, shared by the encoders that differ only in the characters
 * they leave unencoded.
 */
final class PercentCodec {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private PercentCodec() {
    }

    /**
     * @param text the text to encode, may be null
     * @param safeChars the ASCII characters to leave unencoded
     * @return the encoded text, or an empty string if text is null
     */
    static String encode(String text, String safeChars) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            int unsignedByte = b & 0xFF;

            if (unsignedByte < 128 && safeChars.indexOf((char) unsignedByte) >= 0) {
                encoded.append((char) unsignedByte);
            } else {
                encoded.append('%');
                encoded.append(HEX_DIGITS[(unsignedByte >> 4) & 0x0F]);
                encoded.append(HEX_DIGITS[unsignedByte & 0x0F]);
            }
        }

        return encoded.toString();
    }

    /**
     * @param text the text to decode, may be null
     * @return the decoded text, or an empty string if text is null. A percent sign that does not start a valid
     *         sequence, such as {@code %GG}, is kept as it is.
     */
    static String decode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int i = 0;

        while (i < text.length()) {
            if (text.charAt(i) == '%' && i + 2 < text.length()) {
                int high = Character.digit(text.charAt(i + 1), 16);
                int low = Character.digit(text.charAt(i + 2), 16);

                if (high >= 0 && low >= 0) {
                    decoded.write((high << 4) | low);
                    i += 3;
                    continue;
                }
            }

            // Read a whole code point: half of a surrogate pair has no UTF-8 encoding of its own.
            int codePoint = text.codePointAt(i);
            byte[] utf8 = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
            decoded.write(utf8, 0, utf8.length);
            i += Character.charCount(codePoint);
        }

        return new String(decoded.toByteArray(), StandardCharsets.UTF_8);
    }
}
