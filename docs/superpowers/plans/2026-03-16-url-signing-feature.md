# URL Signing Feature Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add generic URL signing capability to UrlBuilder that executes custom functions during `toString()` for HMAC, RSA, and other signature schemes.

**Architecture:** Introduce `UrlSigner` functional interface that receives URL context and returns signature parameters. Integrate into `UrlBuilder.toString()` to invoke signer before final URL assembly. Refactor `CloudfrontUrlBuilder` to use the new generic feature.

**Tech Stack:** Java 8, JUnit 5, Gradle

---

## Chunk 1: Core Infrastructure

### File Structure

**New Files:**
- `src/main/java/com/widen/urlbuilder/UrlSigner.java` - Functional interface for URL signing
- `src/main/java/com/widen/urlbuilder/SigningContextImpl.java` - Implementation of SigningContext
- `src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java` - Public encoder for pre-encoded values
- `src/test/java/com/widen/urlbuilder/NoEncodingEncoderTest.java` - Tests for NoEncodingEncoder
- `src/test/java/com/widen/urlbuilder/SigningContextTest.java` - Tests for SigningContext
- `src/test/java/com/widen/urlbuilder/UrlSignerTest.java` - Tests for UrlSigner interface

**Modified Files:**
- `src/main/java/com/widen/urlbuilder/UrlBuilder.java:636-681` - Integrate signing into toString()
- `src/test/java/com/widen/urlbuilder/UrlBuilderTest.java` - Add basic signing tests

---

### Task 1: Create NoEncodingEncoder

**Files:**
- Create: `src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java`
- Test: `src/test/java/com/widen/urlbuilder/NoEncodingEncoderTest.java`

- [ ] **Step 1: Write failing tests for NoEncodingEncoder**

Create test file with encoding/decoding tests:

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoEncodingEncoderTest {
    
    @Test
    void testEncodeReturnsOriginalString() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "hello+world=test/abc";
        assertEquals(input, encoder.encode(input));
    }
    
    @Test
    void testDecodeReturnsOriginalString() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "hello+world=test/abc";
        assertEquals(input, encoder.decode(input));
    }
    
    @Test
    void testEncodeWithSpecialCharacters() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "abc123!@#$%^&*()_+-=[]{}|;:',.<>?/~`";
        assertEquals(input, encoder.encode(input));
    }
    
    @Test
    void testEncodeWithUnicode() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "hello世界🌍";
        assertEquals(input, encoder.encode(input));
    }
    
    @Test
    void testInstanceIsSingleton() {
        NoEncodingEncoder instance1 = NoEncodingEncoder.INSTANCE;
        NoEncodingEncoder instance2 = NoEncodingEncoder.INSTANCE;
        assertSame(instance1, instance2);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests NoEncodingEncoderTest`
Expected: FAIL with "NoEncodingEncoder does not exist"

- [ ] **Step 3: Implement NoEncodingEncoder**

Create implementation:

```java
package com.widen.urlbuilder;

/**
 * Encoder that performs no encoding or decoding.
 * 
 * <p>This encoder is useful for values that are already properly encoded,
 * such as cryptographic signatures that should be preserved exactly as-is.
 * 
 * <p>Example usage:
 * <pre>{@code
 * builder.addParameter("signature", base64Signature, NoEncodingEncoder.INSTANCE);
 * }</pre>
 * 
 * @since 3.0.0
 */
public final class NoEncodingEncoder implements Encoder {
    
    /**
     * Singleton instance for reuse.
     */
    public static final NoEncodingEncoder INSTANCE = new NoEncodingEncoder();
    
    private NoEncodingEncoder() {
        // Private constructor for singleton pattern
    }
    
    /**
     * Returns the input string unchanged.
     * 
     * @param text The text to encode
     * @return The same text, unmodified
     */
    @Override
    public String encode(String text) {
        return text;
    }
    
    /**
     * Returns the input string unchanged.
     * 
     * @param text The text to decode
     * @return The same text, unmodified
     */
    @Override
    public String decode(String text) {
        return text;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests NoEncodingEncoderTest`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java
git add src/test/java/com/widen/urlbuilder/NoEncodingEncoderTest.java
git commit -m "feat: add NoEncodingEncoder for pre-encoded values"
```

---

### Task 2: Create UrlSigner Interface

**Files:**
- Create: `src/main/java/com/widen/urlbuilder/UrlSigner.java`
- Test: `src/test/java/com/widen/urlbuilder/UrlSignerTest.java`

- [ ] **Step 1: Write failing test for UrlSigner**

Create test with lambda and class implementations:

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UrlSignerTest {
    
    @Test
    void testLambdaImplementation() {
        UrlSigner signer = context -> {
            Map<String, String> params = new HashMap<>();
            params.put("signature", "test-sig");
            return params;
        };
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertEquals(1, result.size());
        assertEquals("test-sig", result.get("signature"));
    }
    
    @Test
    void testClassImplementation() {
        UrlSigner signer = new TestSigner();
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertEquals(2, result.size());
        assertEquals("class-sig", result.get("signature"));
        assertEquals("12345", result.get("timestamp"));
    }
    
    @Test
    void testEmptyMapReturn() {
        UrlSigner signer = context -> Collections.emptyMap();
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testNullValueInMap() {
        UrlSigner signer = context -> {
            Map<String, String> params = new HashMap<>();
            params.put("signature", "test-sig");
            params.put("empty", null);
            return params;
        };
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertEquals(2, result.size());
        assertNull(result.get("empty"));
    }
    
    private UrlSigner.SigningContext createMockContext() {
        return new UrlSigner.SigningContext() {
            @Override public String getProtocol() { return "https"; }
            @Override public String getHostname() { return "example.com"; }
            @Override public int getPort() { return -1; }
            @Override public String getEncodedPath() { return "/path"; }
            @Override public String getEncodedQuery() { return "key=value"; }
            @Override public String getFragment() { return null; }
            @Override public String getUrl() { return "https://example.com/path?key=value"; }
            @Override public boolean isSsl() { return true; }
            @Override public GenerationMode getGenerationMode() { return GenerationMode.FULLY_QUALIFIED; }
        };
    }
    
    private static class TestSigner implements UrlSigner {
        @Override
        public Map<String, String> sign(SigningContext context) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("signature", "class-sig");
            params.put("timestamp", "12345");
            return params;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests UrlSignerTest`
Expected: FAIL with "UrlSigner does not exist"

- [ ] **Step 3: Implement UrlSigner interface**

Create interface with nested SigningContext:

```java
package com.widen.urlbuilder;

import java.util.Map;

/**
 * Functional interface for signing URLs during toString() generation.
 * 
 * <p>A URL signer receives contextual information about the URL being built
 * and returns a map of query parameters to append to the URL (typically 
 * including a signature parameter).
 * 
 * <p>The signer is invoked after all path segments and query parameters 
 * have been added but before the final URL string is constructed. This allows
 * the signer to inspect the complete unsigned URL and generate appropriate
 * signature parameters.
 * 
 * <p>Example usage with lambda:
 * <pre>{@code
 * UrlBuilder builder = UrlBuilder.forHost("cdn.example.com")
 *     .withPath("/videos/movie.mp4")
 *     .usingUrlSigner(context -> {
 *         String signature = hmacSha256(context.getUrl(), secretKey);
 *         return Collections.singletonMap("signature", signature);
 *     });
 * }</pre>
 * 
 * <p>Example usage with class:
 * <pre>{@code
 * public class HmacUrlSigner implements UrlSigner {
 *     private final String secretKey;
 *     
 *     public HmacUrlSigner(String secretKey) {
 *         this.secretKey = secretKey;
 *     }
 *     
 *     public Map<String, String> sign(SigningContext context) {
 *         String signature = hmacSha256(context.getUrl(), secretKey);
 *         Map<String, String> params = new HashMap<>();
 *         params.put("signature", signature);
 *         params.put("expires", String.valueOf(System.currentTimeMillis() / 1000 + 3600));
 *         return params;
 *     }
 * }
 * 
 * UrlBuilder builder = UrlBuilder.forHost("cdn.example.com")
 *     .usingUrlSigner(new HmacUrlSigner(SECRET_KEY));
 * }</pre>
 * 
 * @since 3.0.0
 */
@FunctionalInterface
public interface UrlSigner {
    
    /**
     * Sign the URL and return parameters to append.
     * 
     * <p>The returned map should contain query parameter names and values
     * that will be appended to the URL. Common examples include:
     * <ul>
     *   <li>"signature" - The cryptographic signature</li>
     *   <li>"expires" - Expiration timestamp</li>
     *   <li>"key-id" - Key identifier used for signing</li>
     * </ul>
     * 
     * <p>Parameter values will be added using {@link NoEncodingEncoder} by default,
     * meaning they will not be URL-encoded. If your signature needs encoding,
     * ensure it is properly encoded before returning it.
     * 
     * @param context Contextual information about the URL being signed
     * @return Map of query parameters to append (e.g., "signature" -> "abc123").
     *         Returns empty map if no parameters should be added.
     *         Null values in the map will be ignored during URL construction.
     *         Returns null to skip signing (treated as empty map).
     */
    Map<String, String> sign(SigningContext context);
    
    /**
     * Context information provided to the signer.
     * 
     * <p>This interface provides read-only access to the URL components
     * that have been built so far, allowing the signer to generate an
     * appropriate signature based on the complete URL structure.
     */
    interface SigningContext {
        
        /**
         * Returns the protocol (scheme) of the URL.
         * 
         * @return The protocol ("http" or "https"), or empty string if 
         *         using protocol-relative generation mode
         */
        String getProtocol();
        
        /**
         * Returns the hostname of the URL.
         * 
         * @return The hostname (e.g., "example.com", "cdn.example.com")
         */
        String getHostname();
        
        /**
         * Returns the port number of the URL.
         * 
         * @return The port number, or -1 if using the default port
         *         (80 for http, 443 for https)
         */
        int getPort();
        
        /**
         * Returns the encoded path of the URL.
         * 
         * <p>The path will be properly encoded according to RFC 3986 Section 3.3.
         * Multiple path segments are joined with "/" separators.
         * 
         * @return The encoded path (e.g., "/path/to/resource", "/my%20file.txt"),
         *         or empty string if no path is set
         */
        String getEncodedPath();
        
        /**
         * Returns the encoded query string of the URL.
         * 
         * <p>The query string will be properly encoded according to RFC 3986 Section 3.4.
         * Multiple parameters are joined with "&" separators.
         * 
         * @return The encoded query string without leading "?" 
         *         (e.g., "key1=value1&key2=value2"), or empty string if no 
         *         query parameters are set
         */
        String getEncodedQuery();
        
        /**
         * Returns the fragment of the URL.
         * 
         * <p>Note: Fragments are typically not included in signatures since
         * they are processed client-side only and not sent to the server.
         * 
         * @return The fragment without leading "#" (e.g., "section1"), 
         *         or null if no fragment is set
         */
        String getFragment();
        
        /**
         * Returns the complete unsigned URL string.
         * 
         * <p>This is the full URL that would be generated if no signing
         * was applied. It includes protocol, hostname, port, path, query
         * parameters, but not the fragment (unless included in your signing scheme).
         * 
         * <p>This is typically what you want to sign.
         * 
         * @return The complete unsigned URL string
         *         (e.g., "https://example.com/path?key=value")
         */
        String getUrl();
        
        /**
         * Returns whether the URL uses SSL (https).
         * 
         * @return true if using https, false if using http
         */
        boolean isSsl();
        
        /**
         * Returns the generation mode of the URL.
         * 
         * @return The generation mode (FULLY_QUALIFIED, PROTOCOL_RELATIVE, 
         *         HOSTNAME_RELATIVE, etc.)
         */
        GenerationMode getGenerationMode();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests UrlSignerTest`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/UrlSigner.java
git add src/test/java/com/widen/urlbuilder/UrlSignerTest.java
git commit -m "feat: add UrlSigner functional interface"
```

---

### Task 3: Create SigningContextImpl

**Files:**
- Create: `src/main/java/com/widen/urlbuilder/SigningContextImpl.java`
- Test: `src/test/java/com/widen/urlbuilder/SigningContextTest.java`

- [ ] **Step 1: Write failing test for SigningContextImpl**

Create test verifying all fields:

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningContextTest {
    
    @Test
    void testAllFieldsPopulated() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            8080,
            "/path/to/resource",
            "key1=value1&key2=value2",
            "section",
            "https://example.com:8080/path/to/resource?key1=value1&key2=value2",
            true,
            GenerationMode.FULLY_QUALIFIED
        );
        
        assertEquals("https", context.getProtocol());
        assertEquals("example.com", context.getHostname());
        assertEquals(8080, context.getPort());
        assertEquals("/path/to/resource", context.getEncodedPath());
        assertEquals("key1=value1&key2=value2", context.getEncodedQuery());
        assertEquals("section", context.getFragment());
        assertEquals("https://example.com:8080/path/to/resource?key1=value1&key2=value2", context.getUrl());
        assertTrue(context.isSsl());
        assertEquals(GenerationMode.FULLY_QUALIFIED, context.getGenerationMode());
    }
    
    @Test
    void testDefaultPort() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            null,
            "https://example.com/path",
            true,
            GenerationMode.FULLY_QUALIFIED
        );
        
        assertEquals(-1, context.getPort());
    }
    
    @Test
    void testEmptyQueryAndPath() {
        SigningContextImpl context = new SigningContextImpl(
            "http",
            "example.com",
            -1,
            "",
            "",
            null,
            "http://example.com",
            false,
            GenerationMode.FULLY_QUALIFIED
        );
        
        assertEquals("", context.getEncodedPath());
        assertEquals("", context.getEncodedQuery());
        assertNull(context.getFragment());
        assertFalse(context.isSsl());
    }
    
    @Test
    void testProtocolRelativeMode() {
        SigningContextImpl context = new SigningContextImpl(
            "",
            "example.com",
            -1,
            "/path",
            "",
            null,
            "//example.com/path",
            true,
            GenerationMode.PROTOCOL_RELATIVE
        );
        
        assertEquals("", context.getProtocol());
        assertEquals(GenerationMode.PROTOCOL_RELATIVE, context.getGenerationMode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests SigningContextTest`
Expected: FAIL with "SigningContextImpl does not exist"

- [ ] **Step 3: Implement SigningContextImpl**

Create immutable implementation:

```java
package com.widen.urlbuilder;

/**
 * Immutable implementation of {@link UrlSigner.SigningContext}.
 * 
 * <p>Package-private as this is an internal implementation detail.
 * Users interact with the {@link UrlSigner.SigningContext} interface.
 * 
 * @since 3.0.0
 */
final class SigningContextImpl implements UrlSigner.SigningContext {
    
    private final String protocol;
    private final String hostname;
    private final int port;
    private final String encodedPath;
    private final String encodedQuery;
    private final String fragment;
    private final String url;
    private final boolean ssl;
    private final GenerationMode generationMode;
    
    /**
     * Creates a new signing context with all fields.
     * 
     * @param protocol The protocol (http/https) or empty string for protocol-relative
     * @param hostname The hostname
     * @param port The port number or -1 for default
     * @param encodedPath The encoded path
     * @param encodedQuery The encoded query string without leading "?"
     * @param fragment The fragment without leading "#", or null
     * @param url The complete unsigned URL
     * @param ssl True if using SSL
     * @param generationMode The generation mode
     */
    SigningContextImpl(String protocol, String hostname, int port,
                       String encodedPath, String encodedQuery, String fragment,
                       String url, boolean ssl, GenerationMode generationMode) {
        this.protocol = protocol;
        this.hostname = hostname;
        this.port = port;
        this.encodedPath = encodedPath;
        this.encodedQuery = encodedQuery;
        this.fragment = fragment;
        this.url = url;
        this.ssl = ssl;
        this.generationMode = generationMode;
    }
    
    @Override
    public String getProtocol() {
        return protocol;
    }
    
    @Override
    public String getHostname() {
        return hostname;
    }
    
    @Override
    public int getPort() {
        return port;
    }
    
    @Override
    public String getEncodedPath() {
        return encodedPath;
    }
    
    @Override
    public String getEncodedQuery() {
        return encodedQuery;
    }
    
    @Override
    public String getFragment() {
        return fragment;
    }
    
    @Override
    public String getUrl() {
        return url;
    }
    
    @Override
    public boolean isSsl() {
        return ssl;
    }
    
    @Override
    public GenerationMode getGenerationMode() {
        return generationMode;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests SigningContextTest`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/SigningContextImpl.java
git add src/test/java/com/widen/urlbuilder/SigningContextTest.java
git commit -m "feat: add SigningContextImpl for URL signing context"
```

---

### Task 4: Integrate UrlSigner into UrlBuilder

**Files:**
- Modify: `src/main/java/com/widen/urlbuilder/UrlBuilder.java`
- Test: `src/test/java/com/widen/urlbuilder/UrlBuilderSigningTest.java`

- [ ] **Step 1: Write failing integration test**

Create comprehensive integration test:

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UrlBuilderSigningTest {
    
    @Test
    void testSimpleSignerWithLambda() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("/test")
            .usingUrlSigner(context -> {
                return Collections.singletonMap("signature", "abc123");
            });
        
        String url = builder.toString();
        assertEquals("http://example.com/test?signature=abc123", url);
    }
    
    @Test
    void testSignerWithExistingQueryParams() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("/test")
            .addParameter("user", "john")
            .addParameter("id", "42")
            .usingUrlSigner(context -> {
                // Verify context includes existing params
                assertTrue(context.getEncodedQuery().contains("user=john"));
                assertTrue(context.getEncodedQuery().contains("id=42"));
                return Collections.singletonMap("signature", "xyz");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("user=john"));
        assertTrue(url.contains("id=42"));
        assertTrue(url.contains("signature=xyz"));
    }
    
    @Test
    void testSignerWithSpecialCharactersNotEncoded() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> {
                // Return signature with special chars that should NOT be encoded
                // Common in base64: +, /, =
                // Common in URL-safe base64: -, _
                // Common in hex: no special chars but test anyway
                return Collections.singletonMap("sig", "abc+123=def/xyz-_~");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=abc+123=def/xyz-_~"), 
            "Signature should not be URL encoded - special chars must be preserved");
    }
    
    @Test
    void testSignerReturnsMultipleParams() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("signature", "sig123");
                params.put("expires", "1234567890");
                params.put("keyid", "key-abc");
                return params;
            });
        
        String url = builder.toString();
        assertTrue(url.contains("signature=sig123"));
        assertTrue(url.contains("expires=1234567890"));
        assertTrue(url.contains("keyid=key-abc"));
    }
    
    @Test
    void testSignerReturnsEmptyMap() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("/test")
            .usingUrlSigner(context -> Collections.emptyMap());
        
        String url = builder.toString();
        assertEquals("http://example.com/test", url);
    }
    
    @Test
    void testSignerReturnsNull() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("/test")
            .usingUrlSigner(context -> null);
        
        String url = builder.toString();
        assertEquals("http://example.com/test", url);
    }
    
    @Test
    void testSignerWithNullValueInMap() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> {
                Map<String, String> params = new HashMap<>();
                params.put("signature", "sig123");
                params.put("nullparam", null);
                return params;
            });
        
        String url = builder.toString();
        assertTrue(url.contains("signature=sig123"));
        assertFalse(url.contains("nullparam"));
    }
    
    @Test
    void testNoSignerSet() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("/test");
        
        String url = builder.toString();
        assertEquals("http://example.com/test", url);
    }
    
    @Test
    void testSigningContextHasCorrectData() {
        final boolean[] signerCalled = {false};
        
        UrlBuilder builder = UrlBuilder.forHost("example.com", GenerationMode.FULLY_QUALIFIED)
            .usingSsl()
            .withPath("/path/to/resource")
            .addParameter("key", "value")
            .withFragment("section")
            .usingUrlSigner(context -> {
                signerCalled[0] = true;
                
                assertEquals("https", context.getProtocol());
                assertEquals("example.com", context.getHostname());
                assertEquals(-1, context.getPort());
                assertEquals("/path/to/resource", context.getEncodedPath());
                assertTrue(context.getEncodedQuery().contains("key=value"));
                assertEquals("section", context.getFragment());
                assertTrue(context.isSsl());
                assertEquals(GenerationMode.FULLY_QUALIFIED, context.getGenerationMode());
                
                // URL should not include fragment
                String url = context.getUrl();
                assertTrue(url.startsWith("https://example.com/path/to/resource?"));
                assertTrue(url.contains("key=value"));
                assertFalse(url.contains("#section"));
                
                return Collections.singletonMap("sig", "test");
            });
        
        builder.toString();
        assertTrue(signerCalled[0], "Signer should have been called");
    }
    
    @Test
    void testSignerWithCustomPort() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPort(8080)
            .usingUrlSigner(context -> {
                assertEquals(8080, context.getPort());
                assertTrue(context.getUrl().contains(":8080"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.contains(":8080"));
    }
    
    @Test
    void testSignerWithProtocolRelativeMode() {
        UrlBuilder builder = UrlBuilder.forHost("example.com", GenerationMode.PROTOCOL_RELATIVE)
            .usingUrlSigner(context -> {
                assertEquals("", context.getProtocol());
                assertEquals(GenerationMode.PROTOCOL_RELATIVE, context.getGenerationMode());
                assertTrue(context.getUrl().startsWith("//example.com"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.startsWith("//example.com"));
    }
    
    @Test
    void testMultipleToStringCallsReSignWithoutMutation() {
        final int[] callCount = {0};
        
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .addParameter("original", "param")
            .usingUrlSigner(context -> {
                callCount[0]++;
                return Collections.singletonMap("sig", "call" + callCount[0]);
            });
        
        String url1 = builder.toString();
        assertTrue(url1.contains("sig=call1"));
        assertTrue(url1.contains("original=param"));
        
        String url2 = builder.toString();
        assertTrue(url2.contains("sig=call2"));
        assertTrue(url2.contains("original=param"));
        
        // CRITICAL: Verify no signature accumulation - each URL should have exactly ONE sig param
        assertEquals(1, url1.split("sig=").length - 1, "URL1 should have exactly one sig param");
        assertEquals(1, url2.split("sig=").length - 1, "URL2 should have exactly one sig param");
        
        // Verify original param wasn't duplicated either
        assertEquals(1, url1.split("original=").length - 1);
        assertEquals(1, url2.split("original=").length - 1);
        
        assertEquals(2, callCount[0]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests UrlBuilderSigningTest`
Expected: FAIL with "cannot find symbol: method usingUrlSigner"

- [ ] **Step 3: Add urlSigner field to UrlBuilder**

Edit file: `src/main/java/com/widen/urlbuilder/UrlBuilder.java`

Find the field declarations (around line 76) and add the following field:

```java
private UrlSigner urlSigner;
```

- [ ] **Step 4: Add usingUrlSigner() builder method**

Add method after line 339 (after `usingQueryEncoder()`):

```java
/**
 * Set a URL signer to sign the URL during toString() generation.
 * 
 * <p>The signer will be invoked each time toString() is called, after all 
 * path segments and query parameters have been processed but before the 
 * final URL string is returned. This allows the signer to inspect the 
 * complete unsigned URL and append signature parameters.
 * 
 * <p>Signature parameters returned by the signer are added using 
 * {@link NoEncodingEncoder} to prevent double-encoding of signature values.
 * 
 * <p>Example with lambda:
 * <pre>{@code
 * UrlBuilder.forHost("cdn.example.com")
 *     .withPath("/video.mp4")
 *     .usingUrlSigner(context -> {
 *         String signature = hmacSha256(context.getUrl(), SECRET_KEY);
 *         return Collections.singletonMap("signature", signature);
 *     });
 * }</pre>
 * 
 * <p>Example with class:
 * <pre>{@code
 * UrlBuilder.forHost("cdn.example.com")
 *     .usingUrlSigner(new HmacUrlSigner(SECRET_KEY));
 * }</pre>
 * 
 * @param signer The URL signer, or null to disable signing
 * @return This builder for chaining
 * @since 3.0.0
 */
public UrlBuilder usingUrlSigner(UrlSigner signer) {
    this.urlSigner = signer;
    return this;
}
```

- [ ] **Step 5: Modify toString() to invoke signer WITHOUT mutating builder state**

IMPORTANT: The signer must NOT mutate the builder's queryParams list. We build params temporarily for signing.

Edit file: `src/main/java/com/widen/urlbuilder/UrlBuilder.java`

Find the existing `toString()` method starting at line 636 (currently lines 636-681).

Replace it with this NON-MUTATING implementation:

```java
@Override
public String toString() {
    StringBuilder url = new StringBuilder();

    if (GenerationMode.FULLY_QUALIFIED.equals(mode) && StringUtilsInternal.isBlank(hostname)) {
        throw new IllegalArgumentException("Hostname cannot be blank when generation mode is FULLY_QUALIFIED.");
    }

    if (GenerationMode.FULLY_QUALIFIED.equals(mode)) {
        if (ssl) {
            url.append("https://").append(hostname);
        }
        else {
            url.append("http://").append(hostname);
        }
    }
    else if (GenerationMode.PROTOCOL_RELATIVE.equals(mode)) {
        url.append("//").append(hostname);
    }

    if (!GenerationMode.HOSTNAME_RELATIVE.equals(mode)) {
        if (port != 80 && port != 443 && port > 0) {
            url.append(":").append(port);
        }
    }

    url.append("/");

    if (!path.isEmpty()) {
        url.append(StringUtilsInternal.join(encodePathSegments(), "/"));

        if (trailingPathSlash) {
            url.append("/");
        }
    }

    // Build query string - with signing if present
    String queryString;
    if (urlSigner != null) {
        // Build unsigned URL for signing context
        String unsignedQueryString = buildParams();
        StringBuilder unsignedUrl = new StringBuilder(url);
        if (!unsignedQueryString.isEmpty()) {
            unsignedUrl.append("?").append(unsignedQueryString);
        }
        
        // Invoke signer
        UrlSigner.SigningContext context = buildSigningContext(unsignedUrl.toString());
        Map<String, String> signatureParams = urlSigner.sign(context);
        
        // Build final query string with signature params (without mutating queryParams list)
        queryString = buildParamsWithSignature(signatureParams);
    } else {
        queryString = buildParams();
    }

    if (!queryString.isEmpty()) {
        url.append("?");
        url.append(queryString);
    }

    if (StringUtilsInternal.isNotBlank(fragment)) {
        url.append("#").append(fragment);
    }

    return url.toString();
}
```

**Key Changes from Original:**
1. Preserves original variable naming (`url` not `sb`, `mode` not `generationMode`)
2. Does NOT call `addParameter()` - prevents state mutation
3. Uses new helper method `buildParamsWithSignature()` that doesn't modify queryParams
4. Fragment handling comes after query string (matches original flow)

- [ ] **Step 6: Add helper methods for signing**

Add these two private methods after the `toString()` method (after line 681):

```java
/**
 * Build query string with additional signature parameters WITHOUT mutating queryParams list.
 * 
 * @param signatureParams Additional parameters from signer (may be null or empty)
 * @return Query string with original params + signature params
 */
private String buildParamsWithSignature(Map<String, String> signatureParams) {
    StringBuilder params = new StringBuilder();
    boolean first = true;

    // Add original query parameters
    for (QueryParam qp : queryParams) {
        if (!first) {
            params.append("&");
        }
        params.append(qp.toString());
        first = false;
    }

    // Add signature parameters (without encoding - they're pre-encoded)
    if (signatureParams != null && !signatureParams.isEmpty()) {
        for (Map.Entry<String, String> entry : signatureParams.entrySet()) {
            if (entry.getValue() != null) {
                if (!first) {
                    params.append("&");
                }
                // Use NoEncodingEncoder for signature params
                params.append(NoEncodingEncoder.INSTANCE.encode(entry.getKey()));
                params.append("=");
                params.append(NoEncodingEncoder.INSTANCE.encode(entry.getValue()));
                first = false;
            }
        }
    }

    return params.toString();
}

/**
 * Build a SigningContext with current URL state.
 * 
 * @param unsignedUrl The complete unsigned URL
 * @return A SigningContext populated with current state
 */
private UrlSigner.SigningContext buildSigningContext(String unsignedUrl) {
    String protocol = "";
    if (GenerationMode.FULLY_QUALIFIED.equals(mode)) {
        protocol = ssl ? "https" : "http";
    }
    
    String encodedPath = "";
    if (!path.isEmpty()) {
        encodedPath = "/" + StringUtilsInternal.join(encodePathSegments(), "/");
        if (trailingPathSlash) {
            encodedPath += "/";
        }
    } else {
        encodedPath = "/";
    }
    
    String encodedQuery = buildParams();
    
    int effectivePort = port;
    if (port <= 0) {
        effectivePort = -1;
    }
    
    return new SigningContextImpl(
        protocol,
        hostname != null ? hostname : "",
        effectivePort,
        encodedPath,
        encodedQuery,
        fragment,
        unsignedUrl,
        ssl,
        mode
    );
}
```

**Why two methods:**
1. `buildParamsWithSignature()` - Combines original + signature params WITHOUT mutating state
2. `buildSigningContext()` - Creates immutable context for signer

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew test --tests UrlBuilderSigningTest`
Expected: All 13 tests PASS

- [ ] **Step 8: Verify NoEncodingEncoder is used for signature params**

Run specific test to verify no double-encoding:

Run: `./gradlew test --tests UrlBuilderSigningTest.testSignerWithSpecialCharactersNotEncoded`
Expected: PASS - confirms signature "abc+123=def/xyz" is not encoded to "abc%2B123%3Ddef%2Fxyz"

- [ ] **Step 9: Run all existing tests to verify no regression**

Run: `./gradlew test`
Expected: All existing tests still PASS

**If tests fail:**
- Check for compilation errors first
- Review error messages for specific test failures
- Verify no changes to existing UrlBuilder behavior (path encoding, query params, etc.)
- If unsure, revert changes and re-review implementation

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/UrlBuilder.java
git add src/test/java/com/widen/urlbuilder/UrlBuilderSigningTest.java
git commit -m "feat: integrate URL signing into UrlBuilder.toString()"
```

---

## Chunk 2: CloudFront Refactoring

### Task 5: Refactor CloudfrontUrlBuilder to use UrlSigner

**Files:**
- Modify: `src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java:117-147`
- Test: `src/test/java/com/widen/urlbuilder/CloudfrontUrlBuilderTest.java`

- [ ] **Step 1: Run existing CloudFront tests to establish baseline**

Run: `./gradlew test --tests CloudfrontUrlBuilderTest`
Expected: Tests PASS (or some @Disabled if missing keys)

- [ ] **Step 2: Refactor CloudfrontUrlBuilder.toString()**

Edit `src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java`:

Find the `toString()` method (line 117) and replace with:

```java
@Override
public String toString() {
    UrlBuilder builder = UrlBuilder.forHost(hostname, GenerationMode.FULLY_QUALIFIED);
    builder.usingCustomGenerationMode(customGenerationMode);
    builder.usingSsl();
    builder.withPath(path);

    if (responseContentDisposition != null) {
        builder.addParameter("response-content-disposition", responseContentDisposition);
    }

    if (responseContentType != null) {
        builder.addParameter("response-content-type", responseContentType);
    }

    // Use the new URL signer feature
    builder.usingUrlSigner(this::signUrl);

    return builder.toString();
}
```

- [ ] **Step 3: Add signUrl() method to CloudfrontUrlBuilder**

Add this private method after toString():

```java
/**
 * Sign the URL using CloudFront credentials.
 * 
 * @param context The signing context
 * @return Map of CloudFront signing parameters
 */
private Map<String, String> signUrl(UrlSigner.SigningContext context) {
    String policy = Policy.fromExpirationDate(expireTime);
    String signedPolicy = credentials.sign(policy);

    Map<String, String> params = new java.util.LinkedHashMap<>();
    params.put("Expires", String.valueOf(expireTime / 1000L));
    params.put("Signature", signedPolicy);
    params.put("Key-Pair-Id", credentials.keyPairId);
    return params;
}
```

- [ ] **Step 4: Run CloudFront tests to verify refactoring**

Run: `./gradlew test --tests CloudfrontUrlBuilderTest`
Expected: All tests still PASS (same results as baseline)

- [ ] **Step 5: Run full test suite**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java
git commit -m "refactor: CloudfrontUrlBuilder uses generic UrlSigner feature"
```

---

## Chunk 3: Documentation and Examples

### Task 6: Add HMAC Signing Example Test

**Files:**
- Create: `src/test/java/com/widen/urlbuilder/examples/HmacSigningExampleTest.java`

- [ ] **Step 1: Create example test with HMAC signing**

Create comprehensive example:

```java
package com.widen.urlbuilder.examples;

import com.widen.urlbuilder.UrlBuilder;
import com.widen.urlbuilder.UrlSigner;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Examples demonstrating URL signing with HMAC.
 * 
 * These examples show real-world usage patterns for signing URLs
 * with HMAC-SHA256 signatures.
 */
class HmacSigningExampleTest {
    
    private static final String SECRET_KEY = "my-secret-key-12345";
    
    @Test
    void exampleSimpleHmacSigning() {
        // Simple lambda-based HMAC signing
        String signedUrl = UrlBuilder.forHost("cdn.example.com")
            .withPath("/videos/movie.mp4")
            .addParameter("user", "john")
            .usingUrlSigner(context -> {
                String signature = hmacSha256(context.getUrl(), SECRET_KEY);
                return Collections.singletonMap("signature", signature);
            })
            .toString();
        
        assertTrue(signedUrl.contains("signature="));
        assertTrue(signedUrl.contains("user=john"));
    }
    
    @Test
    void exampleReusableHmacSigner() {
        // Reusable signer class
        HmacUrlSigner signer = new HmacUrlSigner(SECRET_KEY, "HmacSHA256");
        
        String url1 = UrlBuilder.forHost("cdn.example.com")
            .withPath("/file1.pdf")
            .usingUrlSigner(signer)
            .toString();
        
        String url2 = UrlBuilder.forHost("cdn.example.com")
            .withPath("/file2.pdf")
            .usingUrlSigner(signer)
            .toString();
        
        assertTrue(url1.contains("signature="));
        assertTrue(url2.contains("signature="));
        assertNotEquals(url1, url2); // Different signatures
    }
    
    @Test
    void exampleExpiringUrlSigner() {
        // Signer that adds expiration timestamp
        ExpiringHmacSigner signer = new ExpiringHmacSigner(SECRET_KEY, 3600);
        
        String signedUrl = UrlBuilder.forHost("api.example.com")
            .withPath("/v1/data")
            .usingUrlSigner(signer)
            .toString();
        
        assertTrue(signedUrl.contains("signature="));
        assertTrue(signedUrl.contains("expires="));
    }
    
    @Test
    void exampleSigningWithSsl() {
        String signedUrl = UrlBuilder.forHost("secure.example.com")
            .usingSsl()
            .withPath("/secure/document.pdf")
            .usingUrlSigner(context -> {
                // Verify we're signing HTTPS URL
                assertTrue(context.isSsl());
                assertTrue(context.getUrl().startsWith("https://"));
                
                String signature = hmacSha256(context.getUrl(), SECRET_KEY);
                return Collections.singletonMap("sig", signature);
            })
            .toString();
        
        assertTrue(signedUrl.startsWith("https://"));
        assertTrue(signedUrl.contains("sig="));
    }
    
    // Helper method for HMAC-SHA256
    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
    
    /**
     * Reusable HMAC URL signer.
     */
    private static class HmacUrlSigner implements UrlSigner {
        private final String secretKey;
        private final String algorithm;
        
        public HmacUrlSigner(String secretKey, String algorithm) {
            this.secretKey = secretKey;
            this.algorithm = algorithm;
        }
        
        @Override
        public Map<String, String> sign(SigningContext context) {
            String signature = hmacSha256(context.getUrl(), secretKey);
            
            Map<String, String> params = new HashMap<>();
            params.put("signature", signature);
            params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            return params;
        }
    }
    
    /**
     * HMAC signer with expiration support.
     */
    private static class ExpiringHmacSigner implements UrlSigner {
        private final String secretKey;
        private final long expirationSeconds;
        
        public ExpiringHmacSigner(String secretKey, long expirationSeconds) {
            this.secretKey = secretKey;
            this.expirationSeconds = expirationSeconds;
        }
        
        @Override
        public Map<String, String> sign(SigningContext context) {
            long expiresAt = System.currentTimeMillis() / 1000 + expirationSeconds;
            
            // Sign URL + expiration timestamp
            String toSign = context.getUrl() + expiresAt;
            String signature = hmacSha256(toSign, secretKey);
            
            Map<String, String> params = new LinkedHashMap<>();
            params.put("expires", String.valueOf(expiresAt));
            params.put("signature", signature);
            return params;
        }
    }
}
```

- [ ] **Step 2: Run example tests**

Run: `./gradlew test --tests HmacSigningExampleTest`
Expected: All 4 example tests PASS

- [ ] **Step 3: Commit examples**

```bash
git add src/test/java/com/widen/urlbuilder/examples/HmacSigningExampleTest.java
git commit -m "docs: add HMAC signing examples"
```

---

### Task 7: Update README with URL Signing Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Read current README**

Read: `README.md`

- [ ] **Step 2: Add URL Signing section**

Add the following section after the "Custom Encoders" section:

```markdown
## URL Signing

URLBuilder supports signing URLs during generation using the `UrlSigner` interface. This is useful for implementing HMAC signatures, RSA signing (like CloudFront), or other URL signing schemes.

### Basic Usage

Sign URLs using a lambda function:

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collections;

String SECRET_KEY = "my-secret-key";

String signedUrl = UrlBuilder.forHost("cdn.example.com")
    .withPath("/videos/movie.mp4")
    .addParameter("user", "john")
    .usingUrlSigner(context -> {
        // Sign the complete unsigned URL
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(context.getUrl().getBytes());
        String signature = Base64.getEncoder().encodeToString(hash);
        
        return Collections.singletonMap("signature", signature);
    })
    .toString();

// Result: http://cdn.example.com/videos/movie.mp4?user=john&signature=abc123...
```

### Reusable Signers

Create reusable signer classes:

```java
public class HmacUrlSigner implements UrlSigner {
    private final String secretKey;
    
    public HmacUrlSigner(String secretKey) {
        this.secretKey = secretKey;
    }
    
    @Override
    public Map<String, String> sign(SigningContext context) {
        String signature = hmacSha256(context.getUrl(), secretKey);
        
        Map<String, String> params = new HashMap<>();
        params.put("signature", signature);
        params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        return params;
    }
    
    private String hmacSha256(String data, String key) {
        // HMAC implementation...
    }
}

// Usage
HmacUrlSigner signer = new HmacUrlSigner("my-secret");
String signedUrl = UrlBuilder.forHost("cdn.example.com")
    .withPath("/media/video.mp4")
    .usingUrlSigner(signer)
    .toString();
```

### Expiring URLs

Create signed URLs with expiration:

```java
public class ExpiringHmacSigner implements UrlSigner {
    private final String secretKey;
    private final long expirationSeconds;
    
    public ExpiringHmacSigner(String secretKey, long expirationSeconds) {
        this.secretKey = secretKey;
        this.expirationSeconds = expirationSeconds;
    }
    
    @Override
    public Map<String, String> sign(SigningContext context) {
        long expiresAt = System.currentTimeMillis() / 1000 + expirationSeconds;
        
        // Sign URL + expiration
        String toSign = context.getUrl() + expiresAt;
        String signature = hmacSha256(toSign, secretKey);
        
        Map<String, String> params = new LinkedHashMap<>();
        params.put("expires", String.valueOf(expiresAt));
        params.put("signature", signature);
        return params;
    }
}

// Create URL that expires in 1 hour
ExpiringHmacSigner signer = new ExpiringHmacSigner("my-secret", 3600);
String signedUrl = UrlBuilder.forHost("api.example.com")
    .withPath("/v1/data")
    .usingUrlSigner(signer)
    .toString();
```

### Signing Context

The `SigningContext` provides access to URL components:

- `getUrl()` - Complete unsigned URL (what you typically sign)
- `getProtocol()` - Protocol (http/https)
- `getHostname()` - Hostname
- `getPort()` - Port number or -1
- `getEncodedPath()` - URL-encoded path
- `getEncodedQuery()` - URL-encoded query string
- `getFragment()` - Fragment (not typically signed)
- `isSsl()` - Whether using HTTPS
- `getGenerationMode()` - URL generation mode

### CloudFront Signing

CloudFront URL signing is built on this feature:

```java
CloudfrontUrlBuilder.forDistribution("d123.cloudfront.net")
    .withPath("/videos/movie.mp4")
    .usingCredentials(credentials)
    .expiringAt(expirationDate)
    .toString();
// Internally uses UrlSigner to add Expires, Signature, and Key-Pair-Id parameters
```

### Best Practices

1. **Keep signers stateless** - Signers should be thread-safe
2. **Sign the complete URL** - Use `context.getUrl()` in most cases
3. **Don't include fragments** - Fragments are client-side only
4. **Use base64/hex encoding** - Signature values are not URL-encoded by default
5. **Add expiration** - Prevent signature reuse with expiration timestamps

See `HmacSigningExampleTest.java` for more examples.
```

- [ ] **Step 3: Commit README update**

```bash
git add README.md
git commit -m "docs: add URL signing documentation to README"
```

---

## Chunk 4: Final Testing and Verification

### Task 8: Edge Case Tests

**Files:**
- Create: `src/test/java/com/widen/urlbuilder/UrlSignerEdgeCaseTest.java`

- [ ] **Step 1: Write edge case tests**

Create comprehensive edge case coverage:

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for URL signing feature.
 */
class UrlSignerEdgeCaseTest {
    
    @Test
    void testSignerThrowsException() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> {
                throw new RuntimeException("Signing failed");
            });
        
        assertThrows(RuntimeException.class, () -> builder.toString());
    }
    
    @Test
    void testSignerWithUnicodeCharacters() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("/文件")
            .usingUrlSigner(context -> {
                // Path should be encoded in context
                assertTrue(context.getEncodedPath().contains("%"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithEmptyPath() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> {
                assertEquals("", context.getEncodedPath());
                assertEquals("http://example.com", context.getUrl());
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertEquals("http://example.com?sig=test", url);
    }
    
    @Test
    void testSignerWithFragmentOnly() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withFragment("section")
            .usingUrlSigner(context -> {
                assertEquals("section", context.getFragment());
                // URL should not include fragment for signing
                assertFalse(context.getUrl().contains("#"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("#section"));
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithProtocolRelativeUrl() {
        UrlBuilder builder = UrlBuilder.forHost("example.com", GenerationMode.PROTOCOL_RELATIVE)
            .withPath("/test")
            .usingUrlSigner(context -> {
                assertEquals("", context.getProtocol());
                assertEquals(GenerationMode.PROTOCOL_RELATIVE, context.getGenerationMode());
                assertTrue(context.getUrl().startsWith("//example.com"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.startsWith("//example.com"));
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithHostnameRelativeUrl() {
        UrlBuilder builder = UrlBuilder.forHost("example.com", GenerationMode.HOSTNAME_RELATIVE)
            .withPath("/test")
            .usingUrlSigner(context -> {
                assertEquals(GenerationMode.HOSTNAME_RELATIVE, context.getGenerationMode());
                assertTrue(context.getUrl().startsWith("/test"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertEquals("/test?sig=test", url);
    }
    
    @Test
    void testSignerWithNonStandardPort() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPort(8080)
            .usingUrlSigner(context -> {
                assertEquals(8080, context.getPort());
                assertTrue(context.getUrl().contains(":8080"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.contains(":8080"));
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithStandardHttpPort() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPort(80)
            .usingUrlSigner(context -> {
                assertEquals(80, context.getPort());
                // Standard port should not appear in URL
                assertFalse(context.getUrl().contains(":80"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertFalse(url.contains(":80"));
    }
    
    @Test
    void testSignerWithStandardHttpsPort() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingSsl()
            .withPort(443)
            .usingUrlSigner(context -> {
                assertEquals(443, context.getPort());
                // Standard HTTPS port should not appear in URL
                assertFalse(context.getUrl().contains(":443"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertFalse(url.contains(":443"));
    }
    
    @Test
    void testSignerWithSpecialCharactersInQuery() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .addParameter("key", "value with spaces")
            .usingUrlSigner(context -> {
                // Query should be encoded
                assertTrue(context.getEncodedQuery().contains("value%20with%20spaces") ||
                          context.getEncodedQuery().contains("value+with+spaces"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testMultipleSignersNotSupported() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> Collections.singletonMap("sig1", "test1"))
            .usingUrlSigner(context -> Collections.singletonMap("sig2", "test2"));
        
        String url = builder.toString();
        
        // Last signer wins
        assertTrue(url.contains("sig2=test2"));
        assertFalse(url.contains("sig1=test1"));
    }
    
    @Test
    void testSignerCanBeCleared() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> Collections.singletonMap("sig", "test"))
            .usingUrlSigner(null);
        
        String url = builder.toString();
        assertFalse(url.contains("sig="));
    }
    
    @Test
    void testSignerWithVeryLongSignature() {
        String longSignature = String.join("", Collections.nCopies(1000, "a"));
        
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> Collections.singletonMap("sig", longSignature));
        
        String url = builder.toString();
        assertTrue(url.contains("sig=" + longSignature));
    }
    
    @Test
    void testSignerWithEmptyStringValue() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .usingUrlSigner(context -> Collections.singletonMap("sig", ""));
        
        String url = builder.toString();
        assertTrue(url.contains("sig="));
    }
}
```

- [ ] **Step 2: Run edge case tests**

Run: `./gradlew test --tests UrlSignerEdgeCaseTest`
Expected: All 15 tests PASS

- [ ] **Step 3: Commit edge case tests**

```bash
git add src/test/java/com/widen/urlbuilder/UrlSignerEdgeCaseTest.java
git commit -m "test: add edge case tests for URL signing"
```

---

### Task 9: Verify Full Test Suite

**Files:**
- All test files

- [ ] **Step 1: Run complete test suite**

Run: `./gradlew test`
Expected: All tests PASS (including existing tests)

- [ ] **Step 2: Check test coverage**

Run: `./gradlew test jacocoTestReport`
Check: `build/reports/jacoco/test/html/index.html`
Expected: >90% coverage for new classes

- [ ] **Step 3: Run build to verify no compilation issues**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Verify no warnings**

Check output for warnings
Expected: No deprecation or unchecked warnings

---

### Task 10: Final Integration Test

**Files:**
- Create: `src/test/java/com/widen/urlbuilder/UrlSigningIntegrationTest.java`

- [ ] **Step 1: Write comprehensive integration test**

Create end-to-end integration test:

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for URL signing feature.
 * 
 * Tests complete workflows from builder creation to signed URL generation.
 */
class UrlSigningIntegrationTest {
    
    private static final String SECRET_KEY = "integration-test-key";
    
    @Test
    void testCompleteHmacSigningWorkflow() {
        // Build a complex URL with signing
        String signedUrl = UrlBuilder.forHost("cdn.example.com")
            .usingSsl()
            .withPort(8443)
            .withPath("videos")
            .withPath("2024")
            .withPath("movie.mp4")
            .addParameter("user", "john_doe")
            .addParameter("quality", "1080p")
            .addParameter("start", "60")
            .withFragment("chapter2")
            .usingUrlSigner(context -> {
                // Verify context has all expected data
                assertEquals("https", context.getProtocol());
                assertEquals("cdn.example.com", context.getHostname());
                assertEquals(8443, context.getPort());
                assertTrue(context.isSsl());
                assertTrue(context.getEncodedPath().contains("videos"));
                assertTrue(context.getEncodedQuery().contains("user=john_doe"));
                assertEquals("chapter2", context.getFragment());
                
                // Generate signature
                String signature = hmacSha256(context.getUrl(), SECRET_KEY);
                long expires = System.currentTimeMillis() / 1000 + 3600;
                
                Map<String, String> params = new LinkedHashMap<>();
                params.put("expires", String.valueOf(expires));
                params.put("signature", signature);
                return params;
            })
            .toString();
        
        // Verify final URL structure
        assertTrue(signedUrl.startsWith("https://cdn.example.com:8443/"));
        assertTrue(signedUrl.contains("videos/2024/movie.mp4"));
        assertTrue(signedUrl.contains("user=john_doe"));
        assertTrue(signedUrl.contains("quality=1080p"));
        assertTrue(signedUrl.contains("start=60"));
        assertTrue(signedUrl.contains("expires="));
        assertTrue(signedUrl.contains("signature="));
        assertTrue(signedUrl.endsWith("#chapter2"));
    }
    
    @Test
    void testSigningWithCustomEncoders() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("file with spaces.pdf")
            .addParameter("name", "value with spaces")
            .usingUrlSigner(context -> {
                // Verify encoding is applied before signing
                assertTrue(context.getEncodedPath().contains("%20") || 
                          context.getEncodedPath().contains("+"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSigningInteractionWithExistingFeatures() {
        // Test that signing works with all existing UrlBuilder features
        UrlBuilder builder = UrlBuilder.forHost("api.example.com", GenerationMode.FULLY_QUALIFIED)
            .usingSsl()
            .withPath("v1", "users", "123")
            .addParameter("format", "json")
            .addParameter("fields", "id,name,email")
            .usingQueryEncoder(new QueryParameterEncoder()) // Custom encoder
            .usingUrlSigner(context -> {
                String signature = hmacSha256(context.getUrl(), SECRET_KEY);
                return Collections.singletonMap("sig", signature);
            })
            .withFragment("profile");
        
        String url = builder.toString();
        
        assertTrue(url.startsWith("https://api.example.com/v1/users/123"));
        assertTrue(url.contains("format=json"));
        assertTrue(url.contains("fields=id,name,email"));
        assertTrue(url.contains("sig="));
        assertTrue(url.endsWith("#profile"));
    }
    
    @Test
    void testReusableSignerAcrossMultipleBuilders() {
        UrlSigner reusableSigner = context -> {
            String signature = hmacSha256(context.getUrl(), SECRET_KEY);
            return Collections.singletonMap("sig", signature);
        };
        
        String url1 = UrlBuilder.forHost("cdn1.example.com")
            .withPath("/file1.pdf")
            .usingUrlSigner(reusableSigner)
            .toString();
        
        String url2 = UrlBuilder.forHost("cdn2.example.com")
            .withPath("/file2.pdf")
            .usingUrlSigner(reusableSigner)
            .toString();
        
        assertTrue(url1.contains("sig="));
        assertTrue(url2.contains("sig="));
        assertNotEquals(url1, url2);
    }
    
    @Test
    void testSigningDoesNotAffectOriginalBuilder() {
        UrlBuilder builder = UrlBuilder.forHost("example.com")
            .withPath("/test")
            .addParameter("key", "value");
        
        // Add signer
        builder.usingUrlSigner(context -> Collections.singletonMap("sig", "test1"));
        String url1 = builder.toString();
        
        // Change signer
        builder.usingUrlSigner(context -> Collections.singletonMap("sig", "test2"));
        String url2 = builder.toString();
        
        // URLs should have different signatures
        assertTrue(url1.contains("sig=test1"));
        assertTrue(url2.contains("sig=test2"));
    }
    
    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `./gradlew test --tests UrlSigningIntegrationTest`
Expected: All 5 integration tests PASS

- [ ] **Step 3: Run complete test suite one final time**

Run: `./gradlew clean test`
Expected: All tests PASS with no failures

- [ ] **Step 4: Commit integration tests**

```bash
git add src/test/java/com/widen/urlbuilder/UrlSigningIntegrationTest.java
git commit -m "test: add integration tests for URL signing feature"
```

---

## Summary

This implementation plan adds a generic URL signing feature to URLBuilder using Java 8 functional interfaces. The feature:

1. **Supports both lambdas and classes** through `@FunctionalInterface UrlSigner`
2. **Provides complete context** via `SigningContext` interface with all URL components
3. **Integrates cleanly** into existing `toString()` flow without breaking changes
4. **Refactors CloudFront** to use the generic feature as reference implementation
5. **Includes comprehensive tests** covering unit, integration, edge cases, and examples
6. **Documents usage** in README with HMAC and expiring URL examples

**Total Tasks**: 10 tasks across 4 chunks
**Files Created**: 8 new files
**Files Modified**: 3 existing files
**Test Coverage**: >90% for new code

The plan follows TDD principles with tests written before implementation, frequent commits after each passing test, and comprehensive verification at each step.
