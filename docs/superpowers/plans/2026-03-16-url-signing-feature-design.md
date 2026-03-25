# URL Signing Feature Implementation Plan

## Overview

Add a generic URL signing capability to `UrlBuilder` that allows custom functions to execute during `toString()` to sign URLs. This will enable HMAC signing, CloudFront signing, and other signature schemes through a unified interface.

## Current State Analysis

### Existing Implementations

1. **CloudfrontUrlBuilder** (`CloudfrontUrlBuilder.java:117-147`)
   - Creates a policy JSON with expiration
   - Signs with RSA/SHA1 using private key
   - Adds query parameters: `Expires`, `Signature`, `Key-Pair-Id`
   - Uses `NoEncodingEncoder` to prevent double-encoding signatures

2. **S3UrlBuilder** (`S3UrlBuilder.java:443-483`)
   - Creates canonical string with HTTP verb, headers, resource
   - Signs with HMAC-SHA1 using secret key
   - Adds query parameters: `Signature`, `Expires`, `AWSAccessKeyId`
   - Includes query parameters in signature calculation

### Key Observations

- Both sign during `toString()` call
- Both add query parameters to the URL after signing
- Signatures must avoid encoding (use `NoEncodingEncoder`)
- Signing requires access to URL parts before final assembly
- Current approach duplicates URL construction logic

## Design Goals

1. **Flexible**: Support both class instances and Java 8 lambda functions
2. **Generic**: Work for HMAC, RSA, and custom signing schemes
3. **Backward Compatible**: Existing CloudFront/S3 builders continue working
4. **Clean API**: Fluent interface consistent with existing patterns
5. **Testable**: Easy to mock and test signing logic

## Proposed Solution

### 1. Functional Interface: `UrlSigner`

Create a new functional interface that receives URL context and returns signing parameters.

**File**: `src/main/java/com/widen/urlbuilder/UrlSigner.java`

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
 * have been added but before the final URL string is constructed.
 * 
 * <p>Example usage:
 * <pre>{@code
 * UrlBuilder builder = UrlBuilder.forHost("cdn.example.com")
 *     .withPath("/videos/movie.mp4")
 *     .usingUrlSigner(context -> {
 *         String signature = hmacSha256(context.getUrl(), secretKey);
 *         return Collections.singletonMap("signature", signature);
 *     });
 * }</pre>
 * 
 * @since 3.0.0
 */
@FunctionalInterface
public interface UrlSigner {
    
    /**
     * Sign the URL and return parameters to append.
     * 
     * @param context Contextual information about the URL being signed
     * @return Map of query parameters to append (e.g., "Signature" -> "abc123")
     *         Returns empty map if no parameters should be added.
     *         Null values in the map will be ignored.
     */
    Map<String, String> sign(SigningContext context);
    
    /**
     * Context information provided to the signer.
     */
    interface SigningContext {
        /**
         * @return The protocol (http/https) or empty if protocol-relative
         */
        String getProtocol();
        
        /**
         * @return The hostname
         */
        String getHostname();
        
        /**
         * @return The port number, or -1 if using default port
         */
        int getPort();
        
        /**
         * @return The encoded path (e.g., "/path/to/resource")
         */
        String getEncodedPath();
        
        /**
         * @return The encoded query string without leading "?" 
         *         (e.g., "key1=value1&key2=value2"), or empty string if no params
         */
        String getEncodedQuery();
        
        /**
         * @return The fragment without leading "#", or null if no fragment
         */
        String getFragment();
        
        /**
         * @return The complete unsigned URL string
         */
        String getUrl();
        
        /**
         * @return True if using SSL (https)
         */
        boolean isSsl();
        
        /**
         * @return The generation mode (FULLY_QUALIFIED, PROTOCOL_RELATIVE, etc.)
         */
        GenerationMode getGenerationMode();
    }
}
```

### 2. UrlBuilder Integration

Modify `UrlBuilder` to support URL signers.

**Changes to `UrlBuilder.java`:**

```java
// Add field (after line 76)
private UrlSigner urlSigner;

// Add builder method (after line 339)
/**
 * Set a URL signer to sign the URL during toString() generation.
 * 
 * <p>The signer will be invoked after all path segments and query 
 * parameters have been processed but before the final URL string 
 * is returned.
 * 
 * <p>Example:
 * <pre>{@code
 * UrlBuilder.forHost("example.com")
 *     .usingUrlSigner(context -> {
 *         String signature = hmacSha256(context.getUrl(), SECRET_KEY);
 *         Map<String, String> params = new HashMap<>();
 *         params.put("signature", signature);
 *         params.put("expires", String.valueOf(System.currentTimeMillis() / 1000 + 3600));
 *         return params;
 *     });
 * }</pre>
 * 
 * @param signer The URL signer, or null to disable signing
 * @return This builder
 * @since 3.0.0
 */
public UrlBuilder usingUrlSigner(UrlSigner signer) {
    this.urlSigner = signer;
    return this;
}

// Modify toString() method (lines 636-681)
@Override
public String toString() {
    // ... existing validation and construction ...
    
    // NEW: Invoke signer before final assembly
    if (urlSigner != null) {
        SigningContext context = buildSigningContext(sb.toString(), generationMode);
        Map<String, String> signatureParams = urlSigner.sign(context);
        
        if (signatureParams != null && !signatureParams.isEmpty()) {
            for (Map.Entry<String, String> entry : signatureParams.entrySet()) {
                if (entry.getValue() != null) {
                    // Add signature params with NoEncodingEncoder to prevent double-encoding
                    addParameter(entry.getKey(), entry.getValue(), NoEncodingEncoder.INSTANCE);
                }
            }
            // Rebuild URL string with signature parameters
            sb = new StringBuilder();
            // ... reconstruct with new params ...
        }
    }
    
    // ... continue with fragment handling ...
    return sb.toString();
}

// Add helper method
private SigningContext buildSigningContext(String unsignedUrl, GenerationMode mode) {
    return new SigningContextImpl(
        ssl ? "https" : "http",
        hostname,
        port,
        encodePathSegments(),
        buildParams(),
        fragment,
        unsignedUrl,
        ssl,
        mode
    );
}
```

### 3. SigningContext Implementation

**File**: `src/main/java/com/widen/urlbuilder/SigningContextImpl.java` (package-private)

```java
package com.widen.urlbuilder;

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
    
    @Override public String getProtocol() { return protocol; }
    @Override public String getHostname() { return hostname; }
    @Override public int getPort() { return port; }
    @Override public String getEncodedPath() { return encodedPath; }
    @Override public String getEncodedQuery() { return encodedQuery; }
    @Override public String getFragment() { return fragment; }
    @Override public String getUrl() { return url; }
    @Override public boolean isSsl() { return ssl; }
    @Override public GenerationMode getGenerationMode() { return generationMode; }
}
```

### 4. NoEncodingEncoder Enhancement

Ensure `NoEncodingEncoder` is accessible as a singleton.

**File**: `src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java`

```java
package com.widen.urlbuilder;

/**
 * Encoder that performs no encoding/decoding.
 * Used for pre-encoded values like signatures.
 * 
 * @since 3.0.0
 */
public final class NoEncodingEncoder implements Encoder {
    
    /**
     * Singleton instance for reuse.
     */
    public static final NoEncodingEncoder INSTANCE = new NoEncodingEncoder();
    
    private NoEncodingEncoder() {
        // Private constructor for singleton
    }
    
    @Override
    public String encode(String text) {
        return text;
    }
    
    @Override
    public String decode(String text) {
        return text;
    }
}
```

### 5. CloudfrontUrlBuilder Refactoring

Refactor to use the new `UrlSigner` interface internally.

**File**: `src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java`

```java
// Modify toString() method (lines 117-147)
@Override
public String toString() {
    UrlBuilder builder = UrlBuilder.forHost(hostname, GenerationMode.FULLY_QUALIFIED);
    builder.usingCustomGenerationMode(customGenerationMode);
    builder.usingSsl();
    builder.withPath(path);
    builder.usingUrlSigner(this::signUrl); // NEW: Use signer
    
    // Add disposition/content-type if needed
    if (responseContentDisposition != null) {
        builder.addParameter("response-content-disposition", responseContentDisposition);
    }
    if (responseContentType != null) {
        builder.addParameter("response-content-type", responseContentType);
    }
    
    return builder.toString();
}

// Add signing method
private Map<String, String> signUrl(UrlSigner.SigningContext context) {
    String policy = Policy.fromExpirationDate(expireTime);
    String signedPolicy = credentials.sign(policy);
    
    Map<String, String> params = new LinkedHashMap<>();
    params.put("Expires", String.valueOf(expireTime / 1000L));
    params.put("Signature", signedPolicy);
    params.put("Key-Pair-Id", credentials.keyPairId);
    return params;
}
```

### 6. S3UrlBuilder Refactoring (Optional)

S3 signing is more complex because it signs the canonical request including HTTP verb and headers. This may not be suitable for the generic signer, but we could provide a helper.

**Option A**: Keep S3UrlBuilder as-is (signing is too specific)

**Option B**: Extract signing logic to a reusable `S3UrlSigner` class

```java
// File: src/main/java/com/widen/urlbuilder/S3UrlSigner.java
public class S3UrlSigner implements UrlSigner {
    private final String awsAccessKeyId;
    private final String awsSecretKey;
    private final String httpVerb;
    private final Date expireTime;
    private final String bucket;
    
    public S3UrlSigner(String accessKeyId, String secretKey, String httpVerb, 
                       Date expireTime, String bucket) {
        this.awsAccessKeyId = accessKeyId;
        this.awsSecretKey = secretKey;
        this.httpVerb = httpVerb;
        this.expireTime = expireTime;
        this.bucket = bucket;
    }
    
    @Override
    public Map<String, String> sign(SigningContext context) {
        String canonicalResource = "/" + bucket + context.getEncodedPath();
        String stringToSign = buildCanonicalString(
            httpVerb, 
            "", // MD5
            "", // Content-Type
            expireTime.getTime() / 1000L,
            canonicalResource,
            context.getEncodedQuery()
        );
        
        String signature = AmazonAWSJavaSDKInternal.sign(stringToSign, awsSecretKey);
        
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Signature", signature);
        params.put("Expires", String.valueOf(expireTime.getTime() / 1000L));
        params.put("AWSAccessKeyId", awsAccessKeyId);
        return params;
    }
    
    // ... helper methods ...
}
```

**Recommendation**: Start with **Option A** to keep S3 logic as-is, since S3 signing requires context beyond URL structure (HTTP verb, bucket name, etc.). The generic signer is better suited for simpler schemes like HMAC-based URL signing.

## Implementation Plan

### Phase 1: Core Infrastructure (High Priority)

1. **Create `UrlSigner` interface**
   - Define functional interface with `sign()` method
   - Define `SigningContext` nested interface
   - Add comprehensive Javadoc with examples
   - File: `src/main/java/com/widen/urlbuilder/UrlSigner.java`

2. **Create `SigningContextImpl` class**
   - Implement immutable context holder
   - Package-private visibility
   - File: `src/main/java/com/widen/urlbuilder/SigningContextImpl.java`

3. **Create/Enhance `NoEncodingEncoder`**
   - Make public with singleton pattern
   - Add Javadoc explaining usage with signatures
   - File: `src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java`

4. **Integrate into `UrlBuilder`**
   - Add `urlSigner` field
   - Add `usingUrlSigner()` builder method
   - Modify `toString()` to invoke signer
   - Add `buildSigningContext()` helper method
   - Ensure signature params use `NoEncodingEncoder`

### Phase 2: CloudFront Refactoring (Medium Priority)

5. **Refactor `CloudfrontUrlBuilder.toString()`**
   - Extract signing logic to private method
   - Use `usingUrlSigner()` with method reference
   - Remove manual parameter addition
   - Verify existing tests still pass

6. **Update CloudFront tests**
   - Ensure all existing tests pass
   - Add test for direct `UrlSigner` usage with CloudFront keys

### Phase 3: Documentation & Examples (Medium Priority)

7. **Create example signers**
   - HMAC-SHA256 URL signer example
   - MD5 hash signer example
   - Add to README or examples directory

8. **Update documentation**
   - Update README with URL signing section
   - Add Javadoc examples to `UrlBuilder`
   - Document migration path for custom implementations

### Phase 4: Testing (High Priority)

9. **Unit tests for `UrlSigner`**
   - Test lambda function usage
   - Test class instance usage
   - Test with empty parameter map
   - Test with null parameter map
   - Test with null values in map
   - File: `src/test/java/com/widen/urlbuilder/UrlSignerTest.java`

10. **Integration tests**
    - Test HMAC signing end-to-end
    - Test multiple signature parameters
    - Test signature parameter encoding
    - Test interaction with existing query params
    - File: `src/test/java/com/widen/urlbuilder/UrlBuilderSigningTest.java`

11. **Edge case tests**
    - Test signer with protocol-relative URLs
    - Test signer with hostname-relative URLs
    - Test signer with fragments
    - Test signer exception handling

## API Usage Examples

### Example 1: Simple HMAC Signing (Lambda)

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collections;

String SECRET_KEY = "my-secret-key";

UrlBuilder builder = UrlBuilder.forHost("api.example.com")
    .withPath("/v1/data")
    .addParameter("user", "12345")
    .usingUrlSigner(context -> {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(context.getUrl().getBytes());
            String signature = Base64.getEncoder().encodeToString(hash);
            return Collections.singletonMap("signature", signature);
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    });

String signedUrl = builder.toString();
// https://api.example.com/v1/data?user=12345&signature=abc123...
```

### Example 2: Reusable Signer Class

```java
public class HmacUrlSigner implements UrlSigner {
    private final String secretKey;
    private final String algorithm;
    
    public HmacUrlSigner(String secretKey, String algorithm) {
        this.secretKey = secretKey;
        this.algorithm = algorithm;
    }
    
    @Override
    public Map<String, String> sign(SigningContext context) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secretKey.getBytes(), algorithm));
            byte[] hash = mac.doFinal(context.getUrl().getBytes());
            String signature = Base64.getEncoder().encodeToString(hash);
            
            Map<String, String> params = new HashMap<>();
            params.put("signature", signature);
            params.put("timestamp", String.valueOf(System.currentTimeMillis()));
            return params;
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }
}

// Usage
HmacUrlSigner signer = new HmacUrlSigner("my-secret", "HmacSHA256");
String signedUrl = UrlBuilder.forHost("cdn.example.com")
    .withPath("/media/video.mp4")
    .usingUrlSigner(signer)
    .toString();
```

### Example 3: Expiring URLs

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
        String toSign = context.getUrl() + expiresAt;
        
        String signature = hmacSha256(toSign, secretKey);
        
        Map<String, String> params = new LinkedHashMap<>();
        params.put("expires", String.valueOf(expiresAt));
        params.put("signature", signature);
        return params;
    }
    
    private String hmacSha256(String data, String key) {
        // ... HMAC implementation ...
    }
}
```

## Testing Strategy

### Test Coverage Areas

1. **Functional Interface Compatibility**
   - Lambda expressions
   - Method references
   - Anonymous classes
   - Named classes implementing interface

2. **Signing Context**
   - All context fields populated correctly
   - Different generation modes
   - Protocol-relative URLs
   - Hostname-relative URLs
   - URLs with/without fragments
   - URLs with existing query parameters

3. **Parameter Handling**
   - Signature parameters appended correctly
   - Signature parameters NOT double-encoded
   - Empty map returns original URL
   - Null map handled gracefully
   - Null values in map ignored

4. **Integration**
   - CloudFront signing still works
   - S3 signing still works
   - Custom signers work alongside encoders
   - Multiple calls to toString() re-sign correctly

5. **Error Handling**
   - Signer throws exception
   - Signer returns invalid characters
   - Concurrent usage

### Test Files to Create

1. `UrlSignerTest.java` - Unit tests for interface
2. `UrlBuilderSigningTest.java` - Integration tests
3. `SigningContextTest.java` - Context data tests
4. `NoEncodingEncoderTest.java` - Encoder tests

### Test Files to Update

1. `CloudfrontUrlBuilderTest.java` - Verify refactoring didn't break anything
2. `S3UrlBuilderTest.java` - Verify S3 still works
3. `UrlBuilderTest.java` - Add basic signing examples

## Backward Compatibility

### Breaking Changes: NONE

- All existing APIs remain unchanged
- CloudFront and S3 builders continue working exactly as before
- New `usingUrlSigner()` is optional

### Deprecations: NONE

- No existing methods deprecated

### New Public APIs

- `UrlSigner` interface (public)
- `UrlSigner.SigningContext` interface (public)
- `UrlBuilder.usingUrlSigner()` method (public)
- `NoEncodingEncoder` class and `INSTANCE` field (public)

## Edge Cases & Considerations

### 1. URL Reconstruction Issue

**Problem**: After signing, we need to reconstruct the URL with new parameters. Current `toString()` returns a final string.

**Solution Options**:
- **A**: Build URL twice (once for signing context, once for final output)
- **B**: Invoke signer earlier in pipeline and add params before first URL construction
- **C**: Make toString() non-idempotent (invoke signer only on first call)

**Recommendation**: **Option B** - Invoke signer after params are added but before URL string construction. Modify the flow:

```java
public String toString() {
    // 1. Validate hostname
    // 2. Build base URL parts (protocol, host, port, path)
    // 3. Build query string
    // 4. Create unsigned URL string for context
    // 5. Invoke signer
    // 6. Add signature params to queryParams list
    // 7. Rebuild query string with signature params
    // 8. Append fragment
    // 9. Return final string
}
```

### 2. Thread Safety

**Issue**: Multiple threads calling `toString()` with a signer that modifies state.

**Solution**: Document that signers should be stateless or thread-safe. Provide examples of immutable signers.

### 3. Encoding Edge Cases

**Issue**: What if signer returns parameters with special characters?

**Solution**: Signature parameter values use `NoEncodingEncoder` by default. Document that signers should return properly encoded values if needed.

### 4. Fragment Handling

**Issue**: Should fragments be included in the signing context?

**Solution**: Yes - provide fragment in context but document that fragments typically aren't signed (client-side only). Leave decision to signer implementation.

### 5. Multiple Signers

**Issue**: What if user wants to apply multiple signers?

**Solution**: Not supported in v1. Users can create a composite signer:

```java
public class CompositeUrlSigner implements UrlSigner {
    private final List<UrlSigner> signers;
    
    public CompositeUrlSigner(UrlSigner... signers) {
        this.signers = Arrays.asList(signers);
    }
    
    @Override
    public Map<String, String> sign(SigningContext context) {
        Map<String, String> allParams = new LinkedHashMap<>();
        for (UrlSigner signer : signers) {
            allParams.putAll(signer.sign(context));
        }
        return allParams;
    }
}
```

### 6. Signer Mutation of UrlBuilder

**Issue**: What if signer tries to call `addParameter()` on the builder?

**Solution**: Signer receives only context (immutable view), not the builder itself. This prevents circular dependencies and keeps signing logic pure.

## Performance Considerations

- Signing adds one additional method call and map construction
- URL may be constructed twice (once for context, once for final output)
- Consider caching signed URL if toString() called multiple times (future optimization)

## Documentation Requirements

1. **Javadoc**
   - Full API documentation for all new public classes/interfaces
   - Code examples in class-level and method-level docs
   - Link to examples from `UrlBuilder` main docs

2. **README Updates**
   - New section: "URL Signing"
   - HMAC example
   - CloudFront example (showing it uses this feature)
   - Link to Javadoc

3. **Migration Guide**
   - How to implement custom signers
   - How CloudFront uses the feature internally
   - Best practices for secure signing

4. **Examples**
   - Create `examples/` directory with working examples
   - HMAC signer example
   - JWT signer example
   - Expiring URL example

## Version & Release

- Target version: **3.1.0**
- Semantic versioning: Minor version bump (new features, backward compatible)
- Update `build.gradle.kts` version after implementation

## Success Criteria

1. ✅ Generic `UrlSigner` interface supports lambdas and classes
2. ✅ CloudFrontUrlBuilder refactored to use new feature
3. ✅ All existing tests pass
4. ✅ New tests achieve >90% code coverage for signing feature
5. ✅ Documentation includes working examples
6. ✅ Zero breaking changes to public API
7. ✅ Performance impact <5% for unsigned URLs
8. ✅ Performance impact <10% for signed URLs

## Open Questions

1. **Should S3UrlBuilder be refactored?**
   - Recommendation: No (too complex, requires HTTP verb & headers)
   - Alternative: Provide `S3UrlSigner` helper class in future version

2. **Should signing context include raw (unencoded) path segments?**
   - Recommendation: No (signers should work with encoded URL)
   - Rationale: Consistency with how URLs are transmitted

3. **Should we support signing before encoding?**
   - Recommendation: No for v1 (adds complexity)
   - Future: Add `PreEncodingUrlSigner` if needed

4. **Should signature parameters support custom encoders per-param?**
   - Recommendation: No (use NoEncodingEncoder for all)
   - Rationale: Signatures are typically base64/hex, don't need encoding

## Implementation Order

1. Create `NoEncodingEncoder` (needed by everything)
2. Create `UrlSigner` interface and `SigningContext` (API definition)
3. Create `SigningContextImpl` (implementation detail)
4. Modify `UrlBuilder.toString()` (core integration)
5. Add `UrlBuilder.usingUrlSigner()` (API method)
6. Write unit tests for new classes
7. Write integration tests for signing flow
8. Refactor `CloudfrontUrlBuilder`
9. Update `CloudfrontUrlBuilderTest`
10. Write documentation and examples
11. Update README

## Risks & Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Breaking CloudFront | High | Low | Comprehensive test suite, manual verification |
| Performance regression | Medium | Low | Benchmark before/after, lazy signing |
| API confusion | Medium | Medium | Clear documentation, examples, Javadoc |
| Thread safety issues | High | Low | Document signer requirements, provide thread-safe examples |
| Encoding bugs | High | Medium | Extensive tests with special characters, Unicode |

## Future Enhancements (Post v3.1.0)

1. **Caching** - Cache signed URL if toString() called multiple times
2. **Pre-encoding signing** - Sign before URL encoding applied
3. **Async signing** - Support for async signing operations
4. **Composite signers** - Built-in support for multiple signers
5. **Signature verification** - Utilities for verifying signed URLs
6. **Common signers library** - Package of pre-built signers (HMAC, JWT, etc.)

---

## Summary

This feature provides a clean, flexible way to sign URLs using Java 8 functional interfaces while maintaining full backward compatibility. The design allows both simple lambda-based signing and complex class-based implementations, making it suitable for HMAC, RSA, JWT, and other signing schemes.

The refactoring of `CloudfrontUrlBuilder` demonstrates the feature's power and serves as a reference implementation for users building custom signers.
