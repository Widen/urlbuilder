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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for LegacyEncoder verifying v2.x compatibility.
 */
@SuppressWarnings("deprecation")
class LegacyEncoderTest {

    private LegacyEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new LegacyEncoder();
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

    @Test
    void encodesSpaceAsPercent20() {
        // v2 behavior: space -> %20 (not +)
        assertEquals("%20", encoder.encode(" "));
        assertEquals("hello%20world", encoder.encode("hello world"));
    }

    @Test
    void encodesAtSign() {
        // v2 behavior: @ is encoded (unlike RFC 3986 path encoding)
        assertEquals("user%40example.com", encoder.encode("user@example.com"));
    }

    @Test
    void encodesColon() {
        // v2 behavior: : is encoded (unlike RFC 3986 path encoding)
        assertEquals("12%3A30%3A00", encoder.encode("12:30:00"));
    }

    @Test
    void encodesSubDelims() {
        // v2 behavior: most sub-delims are encoded (unlike RFC 3986 path encoding)
        // Note: URLEncoder does NOT encode asterisk (*) per RFC 2396
        assertEquals("%21", encoder.encode("!"));
        assertEquals("%24", encoder.encode("$"));
        assertEquals("%26", encoder.encode("&"));
        assertEquals("%27", encoder.encode("'"));
        assertEquals("%28", encoder.encode("("));
        assertEquals("%29", encoder.encode(")"));
        assertEquals("*", encoder.encode("*"));  // URLEncoder leaves * unencoded
        assertEquals("%2B", encoder.encode("+"));
        assertEquals("%2C", encoder.encode(","));
        assertEquals("%3B", encoder.encode(";"));
        assertEquals("%3D", encoder.encode("="));
    }

    @Test
    void matchesV2Behavior() {
        // This test documents exact v2.x output for regression testing
        assertEquals("foo%20%26%20bar", encoder.encode("foo & bar"));
        assertEquals("user%40host%3A8080", encoder.encode("user@host:8080"));
    }

    @Test
    void decodesPercentEncodedCharacters() {
        assertEquals(" ", encoder.decode("%20"));
        assertEquals("@", encoder.decode("%40"));
        assertEquals(":", encoder.decode("%3A"));
    }

    @Test
    void roundTripEncodeDecode() {
        String[] testStrings = {
            "simple",
            "with spaces",
            "user@host:8080",
            "foo & bar"
        };
        
        for (String original : testStrings) {
            String encoded = encoder.encode(original);
            String decoded = encoder.decode(encoded);
            assertEquals(original, decoded, "Round-trip failed for: " + original);
        }
    }
}
