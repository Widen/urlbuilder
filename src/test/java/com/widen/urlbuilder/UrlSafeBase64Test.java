package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class UrlSafeBase64Test {

    @Test
    void encodeSimpleString() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String encoded = UrlSafeBase64.encode(data);
        assertEquals("SGVsbG8sIFdvcmxkIQ", encoded);
    }

    @Test
    void encodeProducesNoPadding() {
        String encoded = UrlSafeBase64.encode("a".getBytes(StandardCharsets.UTF_8));
        assertFalse(encoded.contains("="), "Encoded string should not contain padding characters");
        assertEquals("YQ", encoded);
    }

    @Test
    void encodeUsesUrlSafeAlphabet() {
        byte[] data = {(byte) 0xfb, (byte) 0xef, (byte) 0xbe, (byte) 0xff, (byte) 0xef, (byte) 0xbe};
        String encoded = UrlSafeBase64.encode(data);
        assertFalse(encoded.contains("+"), "Encoded string should not contain '+'");
        assertFalse(encoded.contains("/"), "Encoded string should not contain '/'");
        assertTrue(encoded.contains("-") || encoded.contains("_"),
            "Encoded string should use URL-safe characters '-' or '_'");
    }

    @Test
    void decodeRoundTrip() {
        byte[] original = "urlbuilder-signing-test".getBytes(StandardCharsets.UTF_8);
        String encoded = UrlSafeBase64.encode(original);
        byte[] decoded = UrlSafeBase64.decode(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    void decodeAcceptsPaddedInput() {
        byte[] decoded = UrlSafeBase64.decode("YQ==");
        assertArrayEquals("a".getBytes(StandardCharsets.UTF_8), decoded);
    }

    @Test
    void decodeAcceptsUnpaddedInput() {
        byte[] decoded = UrlSafeBase64.decode("YQ");
        assertArrayEquals("a".getBytes(StandardCharsets.UTF_8), decoded);
    }

    @Test
    void encodeEmptyArray() {
        assertEquals("", UrlSafeBase64.encode(new byte[0]));
    }

    @Test
    void decodeEmptyString() {
        assertArrayEquals(new byte[0], UrlSafeBase64.decode(""));
    }

    @Test
    void roundTripWithBinaryData() {
        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) {
            binary[i] = (byte) i;
        }
        String encoded = UrlSafeBase64.encode(binary);
        byte[] decoded = UrlSafeBase64.decode(encoded);
        assertArrayEquals(binary, decoded);
    }

    @Test
    void encodedOutputIsSafeForUrls() {
        byte[] hmacOutput = {
            (byte) 0x3e, (byte) 0xfb, (byte) 0x4b, (byte) 0x0d,
            (byte) 0x70, (byte) 0x3f, (byte) 0xbf, (byte) 0xfc,
            (byte) 0xa9, (byte) 0x7e, (byte) 0x3b, (byte) 0x8a
        };
        String encoded = UrlSafeBase64.encode(hmacOutput);
        assertFalse(encoded.contains("+"));
        assertFalse(encoded.contains("/"));
        assertFalse(encoded.contains("="));
    }
}
