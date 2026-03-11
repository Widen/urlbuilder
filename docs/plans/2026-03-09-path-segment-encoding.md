# Path Segment Encoding Fix Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix URL encoding by implementing separate encoders for path segments and query parameters per RFC 3986, with a backward-compatible "v2" encoder option for migration.

**Architecture:** Create three new encoder implementations:
1. `PathSegmentEncoder` - RFC 3986 compliant path encoding (default in v3)
2. `QueryParameterEncoder` - RFC 3986 compliant query parameter encoding (default in v3)
3. `LegacyEncoder` - v2-compatible encoding for users who need backward compatibility (sets both path and query encoders)

The `UrlBuilder` will use separate encoder fields for paths vs query params, with a convenience method to enable legacy/v2 encoding behavior.

**Tech Stack:** Java 8+, JUnit 5, Gradle

---

## Background: RFC 3986 Encoding Rules

Per RFC 3986:
- **Unreserved characters** (never encoded): `A-Z a-z 0-9 - . _ ~`
- **Path segment** (`pchar`): unreserved + sub-delims + `:` + `@` (these should NOT be encoded in paths)
- **Sub-delimiters**: `! $ & ' ( ) * + , ; =`
- **Query string**: Different rules - `&` and `=` are typically used as delimiters so must be encoded when they appear in values

**The Problem:** The old `BuiltinEncoder` used `java.net.URLEncoder` which encodes for `application/x-www-form-urlencoded` (HTML forms), not for URL path segments. It over-encoded characters like `@`, `:`, `$`, `!`, etc. that are valid in path segments per RFC 3986.

**Example of current incorrect behavior:**
- Input path: `user@example.com`
- Current output: `user%40example.com` (incorrect - `@` should not be encoded in path)
- Correct output: `user@example.com`

---

## Encoder Strategy

| Component | v2 Behavior | v3 Behavior (RFC 3986) |
|-----------|-------------|------------------------|
| Path segments | `LegacyEncoder` (over-encodes) | `PathSegmentEncoder` |
| Query params | `LegacyEncoder` (over-encodes) | `QueryParameterEncoder` |

### New Encoder Classes

| Class | Purpose | Safe Characters |
|-------|---------|-----------------|
| `PathSegmentEncoder` | RFC 3986 path encoding | unreserved + sub-delims + `:@` |
| `QueryParameterEncoder` | RFC 3986 query encoding | unreserved only (encodes sub-delims) |
| `LegacyEncoder` | v2-compatible encoding (both path + query) | Uses `java.net.URLEncoder` |

### User Migration Path

```java
// v3 default - RFC 3986 compliant
new UrlBuilder("host.com", "user@example.com")  // -> http://host.com/user@example.com

// Enable v2 compatibility for gradual migration
new UrlBuilder("host.com", "user@example.com")
    .usingLegacyEncoding()  // -> http://host.com/user%40example.com
```

---

## Breaking Change Analysis

**Impact Assessment:**

| Character | v2 (Current) | v3 (RFC 3986) | Impact |
|-----------|--------------|---------------|--------|
| `@` | Encoded as `%40` | Not encoded | **Breaking** |
| `:` | Encoded as `%3A` | Not encoded | **Breaking** |
| `$` | Encoded as `%24` | Not encoded | **Breaking** |
| `!` | Encoded as `%21` | Not encoded | **Breaking** |
| `'` | Encoded as `%27` | Not encoded | **Breaking** |
| `(` `)` | Encoded | Not encoded | **Breaking** |
| `*` | Encoded as `%2A` | Not encoded | **Breaking** |
| `+` | Encoded as `%2B` | Not encoded | **Breaking** |
| `,` | Encoded as `%2C` | Not encoded | **Breaking** |
| `;` | Encoded as `%3B` | Not encoded | **Breaking** |
| `=` | Encoded as `%3D` | Not encoded | **Breaking** |
| `&` | Encoded as `%26` | Not encoded | **Breaking** |
| Space | Encoded as `%20` | Encoded as `%20` | No change |

**Recommendation:** This should be a **major version bump** (2.x -> 3.0.0) since it changes observable output behavior.

---

## Tasks

### Task 1: Add Failing Tests for Path Segment Encoding

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/UrlBuilderPathTest.java`

**Step 1: Write failing tests for characters that should NOT be encoded in paths**

Add these tests to `UrlBuilderPathTest.java`:

```java
@Test
void doesNotEncodeAtSignInPath() {
    String url = new UrlBuilder("my.host.com", "user@example.com").toString();
    assertEquals("http://my.host.com/user@example.com", url);
}

@Test
void doesNotEncodeColonInPath() {
    String url = new UrlBuilder("my.host.com", "time:12:30:00").toString();
    assertEquals("http://my.host.com/time:12:30:00", url);
}

@Test
void doesNotEncodeSubDelimsInPath() {
    // sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
    String url = new UrlBuilder("my.host.com", "file!name$var&test'quote(paren)*star+plus,comma;semi=equals").toString();
    assertEquals("http://my.host.com/file!name$var&test'quote(paren)*star+plus,comma;semi=equals", url);
}

@Test
void doesNotEncodeTildeInPath() {
    String url = new UrlBuilder("my.host.com", "~user/home").toString();
    assertEquals("http://my.host.com/~user/home", url);
}

@Test
void encodesGenDelimsInPath() {
    // gen-delims that ARE NOT allowed unencoded in path segments: ? # [ ]
    // Note: / is allowed but treated as segment delimiter
    String url = new UrlBuilder("my.host.com", "path?query#fragment").toString();
    assertEquals("http://my.host.com/path%3Fquery%23fragment", url);
}

@Test
void encodesSquareBracketsInPath() {
    String url = new UrlBuilder("my.host.com", "array[0]").toString();
    assertEquals("http://my.host.com/array%5B0%5D", url);
}

@Test
void encodesPercentInPath() {
    String url = new UrlBuilder("my.host.com", "100%complete").toString();
    assertEquals("http://my.host.com/100%25complete", url);
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "UrlBuilderPathTest" -i`
Expected: Multiple test failures showing characters being incorrectly encoded

**Step 3: Commit failing tests**

```bash
git add src/test/java/com/widen/urlbuilder/UrlBuilderPathTest.java
git commit -m "test: add failing tests for RFC 3986 path segment encoding

Adds tests proving that path segments are incorrectly over-encoding
characters that RFC 3986 allows unencoded in path segments (pchar).
See: https://github.com/Widen/urlbuilder/issues/12"
```

---

### Task 2: Create PathSegmentEncoder Class

**Files:**
- Create: `src/main/java/com/widen/urlbuilder/PathSegmentEncoder.java`

**Step 1: Create the PathSegmentEncoder implementation**

```java
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

        return baos.toString(StandardCharsets.UTF_8);
    }
}
```

**Step 2: Run compilation to verify no syntax errors**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit the new encoder class**

```bash
git add src/main/java/com/widen/urlbuilder/PathSegmentEncoder.java
git commit -m "feat: add PathSegmentEncoder for RFC 3986 compliant path encoding

Implements path segment encoding per RFC 3986 Section 3.3. Path segments
allow unreserved chars, sub-delims, colon, and at-sign unencoded.
Only encodes: space, gen-delims (?#[]), percent, and non-ASCII chars."
```

---

### Task 3: Create QueryParameterEncoder Class

**Files:**
- Create: `src/main/java/com/widen/urlbuilder/QueryParameterEncoder.java`

**Step 1: Create the QueryParameterEncoder implementation**

```java
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
 *   <li>Unreserved characters: A-Z a-z 0-9 - . _ ~</li>
 * </ul>
 * <p>
 * Characters that MUST be encoded:
 * <ul>
 *   <li>Sub-delimiters: ! $ &amp; ' ( ) * + , ; =</li>
 *   <li>General delimiters: : / ? # [ ] @</li>
 *   <li>Space and other whitespace</li>
 *   <li>The percent character "%" (unless part of percent-encoding)</li>
 *   <li>Any character outside the ASCII printable range</li>
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.4">RFC 3986 Section 3.4</a>
 * @since 3.0.0
 */
public class QueryParameterEncoder implements Encoder {

    // Only unreserved characters are safe in query parameter keys/values
    // unreserved: A-Z a-z 0-9 - . _ ~
    private static final String SAFE_CHARS = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

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

        return baos.toString(StandardCharsets.UTF_8);
    }
}
```

**Step 2: Run compilation to verify no syntax errors**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit the new encoder class**

```bash
git add src/main/java/com/widen/urlbuilder/QueryParameterEncoder.java
git commit -m "feat: add QueryParameterEncoder for RFC 3986 compliant query encoding

Implements query parameter encoding per RFC 3986 Section 3.4. Only
unreserved characters (A-Z a-z 0-9 - . _ ~) are safe. Sub-delimiters
and other special characters are percent-encoded."
```

---

### Task 4: Create LegacyEncoder Class (v2 Compatibility)

**Files:**
- Create: `src/main/java/com/widen/urlbuilder/LegacyEncoder.java`

**Step 1: Create the LegacyEncoder implementation**

```java
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
 *     .usingLegacyEncoding()
 *     .toString();
 * </pre>
 *
 * @see PathSegmentEncoder
 * @see UrlBuilder#usingLegacyEncoding()
 * @since 3.0.0
 * @deprecated Use {@link PathSegmentEncoder} for new code. This encoder exists only for
 *             backward compatibility with v2.x URL output.
 */
@Deprecated
public class LegacyEncoder implements Encoder {

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
```

**Step 2: Run compilation to verify no syntax errors**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit the legacy encoder class**

```bash
git add src/main/java/com/widen/urlbuilder/LegacyEncoder.java
git commit -m "feat: add LegacyEncoder for v2.x backward compatibility

Provides v2.x-compatible path encoding for users migrating to v3.x
who need to maintain existing URL formats. Marked as @Deprecated to
encourage migration to RFC 3986 compliant PathSegmentEncoder."
```

---

### Task 5: Add Unit Tests for All Encoders

**Files:**
- Create: `src/test/java/com/widen/urlbuilder/PathSegmentEncoderTest.java`
- Create: `src/test/java/com/widen/urlbuilder/QueryParameterEncoderTest.java`
- Create: `src/test/java/com/widen/urlbuilder/LegacyEncoderTest.java`

**Step 1: Write tests for PathSegmentEncoder**

```java
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

    @ParameterizedTest
    @CsvSource({
        "!, !",
        "$, $",
        "&, &",
        "', '",
        "(, (",
        "), )",
        "*, *",
        "+, +",
        ";, ;",
        "=, ="
    })
    void doesNotEncodeSubDelims(String input, String expected) {
        assertEquals(expected, encoder.encode(input));
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
```

**Step 2: Write tests for QueryParameterEncoder**

```java
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

    @ParameterizedTest
    @CsvSource({
        "!, %21",
        "$, %24",
        "&, %26",
        "', %27",
        "(, %28",
        "), %29",
        "*, %2A",
        "+, %2B",
        ";, %3B",
        "=, %3D"
    })
    void encodesSubDelims(String input, String expected) {
        assertEquals(expected, encoder.encode(input));
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
```

**Step 3: Write tests for LegacyEncoder**

```java
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
        // v2 behavior: sub-delims are encoded (unlike RFC 3986 path encoding)
        assertEquals("%21", encoder.encode("!"));
        assertEquals("%24", encoder.encode("$"));
        assertEquals("%26", encoder.encode("&"));
        assertEquals("%27", encoder.encode("'"));
        assertEquals("%28", encoder.encode("("));
        assertEquals("%29", encoder.encode(")"));
        assertEquals("%2A", encoder.encode("*"));
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
```

**Step 4: Run all encoder tests**

Run: `./gradlew test --tests "*EncoderTest" -i`
Expected: All tests pass

**Step 5: Commit encoder tests**

```bash
git add src/test/java/com/widen/urlbuilder/PathSegmentEncoderTest.java
git add src/test/java/com/widen/urlbuilder/QueryParameterEncoderTest.java
git add src/test/java/com/widen/urlbuilder/LegacyEncoderTest.java
git commit -m "test: add comprehensive tests for all encoder implementations

- PathSegmentEncoderTest: verifies RFC 3986 path segment encoding
- QueryParameterEncoderTest: verifies RFC 3986 query parameter encoding
- LegacyEncoderTest: verifies v2.x backward compatibility"
```

---

### Task 6: Integrate Encoders into UrlBuilder

**Files:**
- Modify: `src/main/java/com/widen/urlbuilder/UrlBuilder.java`

**Step 1: Add encoder fields**

After line 58, add:

```java
private Encoder pathEncoder = new PathSegmentEncoder();

private Encoder queryEncoder = new QueryParameterEncoder();
```

**Step 2: Add method to enable legacy path encoding**

After the `usingEncoder` method (around line 214), add:

```java
/**
 * Enable v2.x-compatible path encoding for backward compatibility.
 * <p>
 * By default, v3.x uses RFC 3986 compliant path encoding which does not encode
 * characters like {@code @}, {@code :}, and sub-delimiters in path segments.
 * <p>
 * Call this method if you need to maintain URL compatibility with v2.x output,
 * for example if you have signed URLs or caches keyed by URL strings.
 * <p>
 * Example:
 * <pre>
 * // v3 default: http://host.com/user@example.com
 * new UrlBuilder("host.com", "user@example.com").toString();
 * 
 * // v2 compatible: http://host.com/user%40example.com
 * new UrlBuilder("host.com", "user@example.com")
 *     .usingLegacyEncoding()
 *     .toString();
 * </pre>
 *
 * @return this builder for method chaining
 * @see LegacyEncoder
 * @since 3.0.0
 */
@SuppressWarnings("deprecation")
public UrlBuilder usingLegacyEncoding() {
    this.pathEncoder = new LegacyEncoder();
    return this;
}

/**
 * Set a custom encoder for path segments.
 * <p>
 * By default, {@link PathSegmentEncoder} is used for RFC 3986 compliant encoding.
 *
 * @param encoder the encoder to use for path segments
 * @return this builder for method chaining
 * @since 3.0.0
 */
public UrlBuilder usingPathEncoder(Encoder encoder) {
    this.pathEncoder = encoder;
    return this;
}

/**
 * Set a custom encoder for query parameters.
 * <p>
 * By default, {@link QueryParameterEncoder} is used for RFC 3986 compliant encoding.
 *
 * @param encoder the encoder to use for query parameters
 * @return this builder for method chaining
 * @since 3.0.0
 */
public UrlBuilder usingQueryEncoder(Encoder encoder) {
    this.queryEncoder = encoder;
    return this;
}
```

**Step 3: Update makePathSegments to use pathEncoder**

In the `makePathSegments` method, change:

```java
list.add(encodeValue(s));
```

to:

```java
list.add(pathEncoder.encode(s));
```

**Step 4: Update QueryParam to use queryEncoder**

Change the `QueryParam` constructor and class to use the builder's `queryEncoder`:

In the `addParameter` methods, change how QueryParam is created. Update the `addParameter(String key, Object value)` method:

```java
public UrlBuilder addParameter(String key, Object value) {
    if (StringUtilsInternal.isNotBlank(key)) {
        queryParams.add(new QueryParam(key, value != null ? value.toString() : null, queryEncoder));
    }
    return this;
}
```

Also update the `UrlBuilder(String spec)` constructor's parameter parsing to use `queryEncoder`:

```java
if (keyValue.length == 2) {
    addParameter(queryEncoder.decode(keyValue[0]), queryEncoder.decode(keyValue[1]));
}
else if (keyValue.length == 1) {
    addParameter(queryEncoder.decode(keyValue[0]), "");
}
```

And update `decodeValue` to use `queryEncoder`:

```java
private String decodeValue(String value) {
    if (value == null) {
        return "";
    }
    return queryEncoder.decode(value);
}
```

**Step 5: Deprecate the old usingEncoder method**

Update the existing `usingEncoder` method:

```java
/**
 * @param encoder alternative URL encoder
 * @deprecated Use {@link #usingPathEncoder(Encoder)} and {@link #usingQueryEncoder(Encoder)} instead.
 *             This method sets both path and query encoders to the same encoder, which may not be
 *             RFC 3986 compliant.
 */
@Deprecated
public UrlBuilder usingEncoder(Encoder encoder) {
    this.pathEncoder = encoder;
    this.queryEncoder = encoder;
    this.encoder = encoder;
    return this;
}
```

**Step 6: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 7: Commit the integration**

```bash
git add src/main/java/com/widen/urlbuilder/UrlBuilder.java
git commit -m "feat: integrate separate path and query encoders into UrlBuilder

BREAKING CHANGE: Path segments now use RFC 3986 compliant encoding.
Characters like @, :, and sub-delims (!$&'()*+,;=) are no longer
percent-encoded in paths.

- Add pathEncoder field (default: PathSegmentEncoder)
- Add queryEncoder field (default: QueryParameterEncoder)  
- Add usingLegacyEncoding() for v2.x backward compatibility
- Add usingPathEncoder() and usingQueryEncoder() for customization
- Deprecate usingEncoder() in favor of specific encoder setters

Closes #12"
```

---

### Task 7: Add Tests for Legacy Path Encoding Mode

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/UrlBuilderPathTest.java`

**Step 1: Add tests for legacy encoding mode**

Add these tests to `UrlBuilderPathTest.java`:

```java
@Test
void legacyModeEncodesAtSign() {
    String url = new UrlBuilder("my.host.com", "user@example.com")
        .usingLegacyEncoding()
        .toString();
    assertEquals("http://my.host.com/user%40example.com", url);
}

@Test
void legacyModeEncodesColon() {
    String url = new UrlBuilder("my.host.com", "time:12:30:00")
        .usingLegacyEncoding()
        .toString();
    assertEquals("http://my.host.com/time%3A12%3A30%3A00", url);
}

@Test
void legacyModeEncodesSubDelims() {
    String url = new UrlBuilder("my.host.com", "foo & bar")
        .usingLegacyEncoding()
        .toString();
    assertEquals("http://my.host.com/foo%20%26%20bar", url);
}

@Test
void legacyModeMatchesV2Output() {
    // Document exact v2.x output for migration testing
    String url = new UrlBuilder("my.host.com", "user@host:8080")
        .usingLegacyEncoding()
        .addParameter("ref", "user@host:8080")
        .toString();
    // Both path and query encode @ and : in legacy mode
    assertEquals("http://my.host.com/user%40host%3A8080?ref=user%40host%3A8080", url);
}
```

**Step 2: Run tests**

Run: `./gradlew test --tests "UrlBuilderPathTest" -i`
Expected: All tests pass

**Step 3: Commit the tests**

```bash
git add src/test/java/com/widen/urlbuilder/UrlBuilderPathTest.java
git commit -m "test: add tests for usingLegacyEncoding() v2 compatibility

Verifies that usingLegacyEncoding() produces v2.x-compatible
URL output for users who need backward compatibility during migration."
```

---

### Task 8: Verify Query Parameter Encoding

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/UrlBuilderQueryParamsTest.java`

**Step 1: Add tests confirming query param encoding behavior**

Add these tests to `UrlBuilderQueryParamsTest.java`:

```java
@Test
void encodesAtSignInQueryParameter() {
    String url = new UrlBuilder("my.host.com", "/path")
        .addParameter("email", "user@example.com")
        .toString();
    assertEquals("http://my.host.com/path?email=user%40example.com", url);
}

@Test
void encodesColonInQueryParameter() {
    String url = new UrlBuilder("my.host.com", "/path")
        .addParameter("time", "12:30:00")
        .toString();
    assertEquals("http://my.host.com/path?time=12%3A30%3A00", url);
}

@Test
void encodesSubDelimsInQueryParameter() {
    String url = new UrlBuilder("my.host.com", "/path")
        .addParameter("special", "a!b$c")
        .toString();
    assertEquals("http://my.host.com/path?special=a%21b%24c", url);
}

@Test
void pathAndQueryEncodeDifferently() {
    // Same content in path vs query should encode differently
    String url = new UrlBuilder("my.host.com", "user@host:8080")
        .addParameter("ref", "user@host:8080")
        .toString();
    // Path: @ and : not encoded (RFC 3986)
    // Query: @ and : are encoded  
    assertEquals("http://my.host.com/user@host:8080?ref=user%40host%3A8080", url);
}
```

**Step 2: Update the existing ampersand test**

Change the existing `encodesAmpersandInPathAndParameters` test:

```java
@Test
void encodesAmpersandInPathAndParameters()
{
    // Path: space encoded, but & is not encoded (it's a sub-delim allowed in paths per RFC 3986)
    // Query: & in values must be encoded to distinguish from parameter separator
    String url = new UrlBuilder("my.host.com", "foo & bar").addParameter("1&2", "3&4").addParameter("a", "b&c").toString();
    assertEquals("http://my.host.com/foo%20&%20bar?1%262=3%264&a=b%26c", url);
}
```

**Step 3: Run tests**

Run: `./gradlew test --tests "UrlBuilderQueryParamsTest" -i`
Expected: All tests pass

**Step 4: Commit the tests**

```bash
git add src/test/java/com/widen/urlbuilder/UrlBuilderQueryParamsTest.java
git commit -m "test: verify different encoding rules for path vs query

Confirms that path segments and query parameters use different
encoding rules per RFC 3986. Path allows sub-delims unencoded,
query encodes them."
```

---

### Task 9: Run Full Test Suite

**Files:**
- None (verification only)

**Step 1: Run complete test suite**

Run: `./gradlew test`
Expected: All tests pass

**Step 2: Verify test count**

Run: `./gradlew test --info 2>&1 | grep -E "tests? (completed|passed|failed)"`
Expected: Should show increased test count with 0 failures

**Step 3: List all changes for review**

Run: `git log --oneline -15`
Expected: Shows all commits from this implementation

---

## Summary of Changes

| File | Change Type | Description |
|------|-------------|-------------|
| `PathSegmentEncoder.java` | New | RFC 3986 compliant path segment encoder |
| `QueryParameterEncoder.java` | New | RFC 3986 compliant query parameter encoder |
| `LegacyEncoder.java` | New | v2.x-compatible path encoder (deprecated) |
| `PathSegmentEncoderTest.java` | New | Tests for PathSegmentEncoder |
| `QueryParameterEncoderTest.java` | New | Tests for QueryParameterEncoder |
| `LegacyEncoderTest.java` | New | Tests for LegacyEncoder |
| `UrlBuilder.java` | Modified | Separate path/query encoders, legacy mode |
| `UrlBuilderPathTest.java` | Modified | Tests for RFC 3986 paths + legacy mode |
| `UrlBuilderQueryParamsTest.java` | Modified | Tests showing different encoding rules |

## API Changes Summary

### New Classes
- `PathSegmentEncoder` - RFC 3986 path encoding (default)
- `QueryParameterEncoder` - RFC 3986 query encoding (default)
- `LegacyEncoder` - v2.x compatibility (deprecated)

### New Methods on UrlBuilder
- `usingLegacyEncoding()` - Enable v2.x path encoding
- `usingPathEncoder(Encoder)` - Custom path encoder
- `usingQueryEncoder(Encoder)` - Custom query encoder

### Deprecated
- `usingEncoder(Encoder)` - Use specific encoder setters instead

---

## Migration Guide for Users

### Default Behavior Change (v2 → v3)

| Character in Path | v2 Output | v3 Output |
|-------------------|-----------|-----------|
| `user@host` | `user%40host` | `user@host` |
| `12:30:00` | `12%3A30%3A00` | `12:30:00` |
| `foo & bar` | `foo%20%26%20bar` | `foo%20&%20bar` |

### Migration Options

**Option 1: Accept new RFC 3986 behavior (recommended)**
```java
// Just upgrade - URLs will be RFC 3986 compliant
new UrlBuilder("host.com", "user@example.com").toString();
// Output: http://host.com/user@example.com
```

**Option 2: Enable v2 compatibility during migration**
```java
// Maintain v2.x URL format
new UrlBuilder("host.com", "user@example.com")
    .usingLegacyEncoding()
    .toString();
// Output: http://host.com/user%40example.com
```

**Option 3: Custom encoder**
```java
// Use your own encoding logic
new UrlBuilder("host.com", "path")
    .usingPathEncoder(myCustomEncoder)
    .usingQueryEncoder(myOtherEncoder)
    .toString();
```

### Breaking Change Checklist

When upgrading to v3, review:

1. **Tests** - Update assertions expecting percent-encoded `@`, `:`, sub-delims in paths
2. **Signed URLs** - Re-sign if signatures were computed on encoded URL strings
3. **Caches** - Clear caches keyed by URL strings
4. **URL comparisons** - Update any string-based URL equality checks
5. **External systems** - Verify consuming systems handle both encoded/unencoded forms
