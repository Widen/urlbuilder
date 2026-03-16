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
 * Tests for QueryParameterEncoder following RFC 3986 Section 3.4.
 */
class QueryParameterEncoderTest {

    private QueryParameterEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new QueryParameterEncoder();
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
    void encodesSubDelims() {
        // sub-delims per RFC 3986: ! $ & ' ( ) * + , ; =
        assertEquals("%21", encoder.encode("!"));
        assertEquals("%24", encoder.encode("$"));
        assertEquals("%26", encoder.encode("&"));
        assertEquals("%27", encoder.encode("'"));
        assertEquals("%28", encoder.encode("("));
        assertEquals("%29", encoder.encode(")"));
        assertEquals("%2A", encoder.encode("*"));
        assertEquals("%2B", encoder.encode("+"));
        assertEquals("%3B", encoder.encode(";"));
        assertEquals("%3D", encoder.encode("="));
    }

    @Test
    void encodesComma() {
        assertEquals("%2C", encoder.encode(","));
    }

    @Test
    void encodesColonAndAtSign() {
        assertEquals("%3A", encoder.encode(":"));
        assertEquals("%40", encoder.encode("@"));
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
        "], %5D",
        "/, %2F"
    })
    void encodesGenDelims(String input, String expected) {
        assertEquals(expected, encoder.encode(input));
    }

    @Test
    void encodesPercent() {
        assertEquals("%25", encoder.encode("%"));
    }

    @Test
    void encodesNonAscii() {
        assertEquals("%C3%A9", encoder.encode("\u00e9"));
        assertEquals("%E4%B8%AD", encoder.encode("\u4e2d"));
    }

    @Test
    void decodesNullAsEmptyString() {
        assertEquals("", encoder.decode(null));
    }

    @Test
    void decodesPercentEncodedCharacters() {
        assertEquals(" ", encoder.decode("%20"));
        assertEquals("&", encoder.decode("%26"));
        assertEquals("=", encoder.decode("%3D"));
    }

    @Test
    void roundTripEncodeDecode() {
        String[] testStrings = {
            "simple",
            "with spaces",
            "key=value&other=test",
            "email@example.com",
            "unicode\u00e9"
        };
        
        for (String original : testStrings) {
            String encoded = encoder.encode(original);
            String decoded = encoder.decode(encoded);
            assertEquals(original, decoded, "Round-trip failed for: " + original);
        }
    }
}
