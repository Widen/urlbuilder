/*
 * Copyright 2010 Widen Enterprises, Inc.
 * Madison, Wisconsin USA -- www.widen.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.widen.urlbuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for PathSegmentEncoder following RFC 3986 Section 3.3.
 */
class PathSegmentEncoderTest {

    private PathSegmentEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new PathSegmentEncoder();
    }

    @Test
    void encodesNullAsEmptyString() {
        assertEquals("", encoder.encode(null));
    }

    @Test
    void encodesEmptyStringAsEmptyString() {
        assertEquals("", encoder.encode(""));
    }

    @Test
    void doesNotEncodeAlphanumeric() {
        assertEquals("ABCxyz123", encoder.encode("ABCxyz123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", ".", "_", "~"})
    void doesNotEncodeUnreservedSpecialChars(String ch) {
        assertEquals(ch, encoder.encode(ch));
    }

    @Test
    void doesNotEncodeSubDelims() {
        // sub-delims per RFC 3986: ! $ & ' ( ) * + , ; =
        assertEquals("!", encoder.encode("!"));
        assertEquals("$", encoder.encode("$"));
        assertEquals("&", encoder.encode("&"));
        assertEquals("'", encoder.encode("'"));
        assertEquals("(", encoder.encode("("));
        assertEquals(")", encoder.encode(")"));
        assertEquals("*", encoder.encode("*"));
        assertEquals("+", encoder.encode("+"));
        assertEquals(";", encoder.encode(";"));
        assertEquals("=", encoder.encode("="));
    }

    @Test
    void doesNotEncodeComma() {
        assertEquals(",", encoder.encode(","));
    }

    @Test
    void doesNotEncodeColonAndAtSign() {
        assertEquals(":", encoder.encode(":"));
        assertEquals("@", encoder.encode("@"));
    }

    @Test
    void encodesSpace() {
        assertEquals("%20", encoder.encode(" "));
        assertEquals("hello%20world", encoder.encode("hello world"));
    }

    @ParameterizedTest
    @CsvSource({
        "?, %3F",
        "#, %23",
        "[, %5B",
        "], %5D"
    })
    void encodesGenDelims(String input, String expected) {
        assertEquals(expected, encoder.encode(input));
    }

    @Test
    void encodesSlash() {
        assertEquals("%2F", encoder.encode("/"));
    }

    @Test
    void encodesPercent() {
        assertEquals("%25", encoder.encode("%"));
        assertEquals("100%25", encoder.encode("100%"));
    }

    @Test
    void encodesNonAscii() {
        assertEquals("%C3%A9", encoder.encode("\u00e9"));  // e-acute
        assertEquals("%E4%B8%AD", encoder.encode("\u4e2d"));  // Chinese character
        assertEquals("caf%C3%A9", encoder.encode("caf\u00e9"));
    }

    @Test
    void handlesComplexPath() {
        String input = "user@host:8080/path with spaces?query#frag";
        String expected = "user@host:8080%2Fpath%20with%20spaces%3Fquery%23frag";
        assertEquals(expected, encoder.encode(input));
    }

    @Test
    void decodesNullAsEmptyString() {
        assertEquals("", encoder.decode(null));
    }

    @Test
    void decodesEmptyStringAsEmptyString() {
        assertEquals("", encoder.decode(""));
    }

    @Test
    void decodesPercentEncodedCharacters() {
        assertEquals(" ", encoder.decode("%20"));
        assertEquals("hello world", encoder.decode("hello%20world"));
        assertEquals("?", encoder.decode("%3F"));
    }

    @Test
    void decodesUtf8() {
        assertEquals("\u00e9", encoder.decode("%C3%A9"));
        assertEquals("caf\u00e9", encoder.decode("caf%C3%A9"));
    }

    @Test
    void roundTripEncodeDecode() {
        String[] testStrings = {
            "simple",
            "with spaces",
            "special!@#$%",
            "unicode\u00e9\u4e2d",
            "path/segment?query#frag"
        };
        
        for (String original : testStrings) {
            String encoded = encoder.encode(original);
            String decoded = encoder.decode(encoded);
            assertEquals(original, decoded, "Round-trip failed for: " + original);
        }
    }
}
