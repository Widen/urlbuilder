# PR #21 Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address all active review comments on PR #21 (URL signing feature).

**Architecture:** Six independent, targeted fixes — no structural changes except the `int`→`Integer` port change which touches the public `UrlSigner.SigningContext` interface, `SigningContextImpl`, and their tests.

**Tech Stack:** Java 8+, JUnit 5, Gradle

---

## Active Comments Being Fixed

| # | File | Issue |
|---|------|-------|
| 1 | `S3UrlBuilder.java`, `CloudfrontUrlBuilder.java` | `@since 3.0.0` on `expireAt(Instant)` should be `@since 3.1.0` |
| 2 | `README.md` | Inline signing lambda calls checked-exception methods without try/catch |
| 3 | `HmacSigningExampleTest.java` | Trailing `,` in signed string (`context.getUrl() + ","`) |
| 4 | `CloudfrontUrlBuilder.java` | 7-space indent in `expireAt(Date)` body (should be 8) |
| 5 | `SigningContextImpl.java` / `UrlSigner.java` | Use `Integer` (nullable) instead of `int` with `-1` sentinel for port |
| 6 | `UrlSafeBase64.java` | `Base64.getUrlEncoder().withoutPadding()` called every invocation; should be a static field |

---

## Task 1: Fix `@since` version tags

**Files:**
- Modify: `src/main/java/com/widen/urlbuilder/S3UrlBuilder.java` (around line 224)
- Modify: `src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java` (around line 241)

- [ ] **Step 1: Update S3UrlBuilder**

Change `@since 3.0.0` to `@since 3.1.0` on `expireAt(Instant)`:

```java
     * @throws IllegalArgumentException if instant is null
     * @since 3.1.0
     */
    public S3UrlBuilder expireAt(Instant instant)
```

- [ ] **Step 2: Update CloudfrontUrlBuilder**

Change `@since 3.0.0` to `@since 3.1.0` on `expireAt(Instant)`:

```java
     * @throws IllegalArgumentException if instant is null
     * @since 3.1.0
     */
    public CloudfrontUrlBuilder expireAt(Instant instant)
```

- [ ] **Step 3: Verify build still passes**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/S3UrlBuilder.java \
        src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java
git commit -m "fix: align expireAt(Instant) @since to 3.1.0 in S3 and CloudFront builders"
```

---

## Task 2: Fix README signing example (unchecked exceptions)

**Files:**
- Modify: `README.md` (around line 200–210)

The inline lambda in the "Basic Usage" example calls `Mac.getInstance(...)` and `mac.init(...)`, both of which throw checked exceptions. Since `UrlSigner.sign()` doesn't declare them, the snippet won't compile. Fix by delegating to a `try/catch` helper, matching the pattern shown in the Reusable Signers example below it.

- [ ] **Step 1: Replace the inline lambda snippet**

Replace this block in README.md:

```java
builder.usingUrlSigner(context -> {
    // Sign the complete unsigned URL
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256"));
    byte[] hash = mac.doFinal(context.getUrl().getBytes());
    String signature = UrlSafeBase64.encode(hash);
    
    return Collections.singletonMap("signature", signature);
});
```

With:

```java
builder.usingUrlSigner(context -> {
    String signature = hmacSha256(context.getUrl(), SECRET_KEY);
    return Collections.singletonMap("signature", signature);
});
```

And add a helper snippet immediately after the closing code fence (before "### Reusable Signers"):

```java
// Helper to compute HMAC-SHA256 as URL-safe base64
private static String hmacSha256(String data, String key) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return UrlSafeBase64.encode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
        throw new RuntimeException("HMAC signing failed", e);
    }
}
```

Also remove the now-unused import line `import javax.crypto.Mac;` / `import javax.crypto.spec.SecretKeySpec;` from the top of the Basic Usage block (they're in the helper now), unless the example still needs them. Keep the imports if retained.

- [ ] **Step 2: Verify build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "fix: wrap checked crypto exceptions in README signing example"
```

---

## Task 3: Remove trailing comma in HmacSigningExampleTest

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/examples/HmacSigningExampleTest.java` (around line 33)

The `exampleSimpleHmacSigning` test signs `context.getUrl() + ","`. The trailing comma is unintentional (noted by reviewer). Removing it changes the signature, so the expected URL assertion must be updated.

New expected signature for `http://cdn.example.com/videos/movie.mp4?user=john` with key `my-secret-key-12345`:
- Raw URL-safe base64 HMAC-SHA256: `trISZB-s6WYVxetzmgdIBCOrLj7GF-po2jnmQHAtQjM`
- After `QueryParameterEncoder().encode(...)` (no special chars to encode): `trISZB-s6WYVxetzmgdIBCOrLj7GF-po2jnmQHAtQjM`

- [ ] **Step 1: Remove the trailing comma**

Change:
```java
String signature = hmacSha256(context.getUrl() + ",", SECRET_KEY);
```
To:
```java
String signature = hmacSha256(context.getUrl(), SECRET_KEY);
```

- [ ] **Step 2: Update the expected URL in the assertEquals**

Change:
```java
assertEquals("http://cdn.example.com/videos/movie.mp4?user=john&signature=SYeZxiQq-n5_nEs2b-gaHWVKB7HebQEwyy_M65BpV6M", signedUrl);
```
To:
```java
assertEquals("http://cdn.example.com/videos/movie.mp4?user=john&signature=trISZB-s6WYVxetzmgdIBCOrLj7GF-po2jnmQHAtQjM", signedUrl);
```

- [ ] **Step 3: Run the test to confirm it passes**

Run: `./gradlew test --tests "com.widen.urlbuilder.examples.HmacSigningExampleTest.exampleSimpleHmacSigning" --info`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/widen/urlbuilder/examples/HmacSigningExampleTest.java
git commit -m "fix: remove accidental trailing comma in HmacSigningExampleTest signing input"
```

---

## Task 4: Fix 7-space indent in CloudfrontUrlBuilder.expireAt(Date)

**Files:**
- Modify: `src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java` (around line 225)

The `expireAt(Date)` method body is indented 7 spaces; the rest of the class uses 8 (4 class + 4 method body).

- [ ] **Step 1: Fix indentation**

Change the 3 body lines from 7-space to 8-space indent:

```java
    public CloudfrontUrlBuilder expireAt(Date date)
    {
        InternalUtils.checkNotNull(date, "date");
        expireDate.instant = date.toInstant();
        return this;
    }
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "com.widen.urlbuilder.CloudfrontUrlBuilderTest" --info`
Expected: all pass

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java
git commit -m "fix: correct indentation in CloudfrontUrlBuilder.expireAt(Date)"
```

---

## Task 5: Change port from `int`/`-1` sentinel to `Integer`/`null`

**Files:**
- Modify: `src/main/java/com/widen/urlbuilder/UrlSigner.java` — `SigningContext.getPort()` interface method
- Modify: `src/main/java/com/widen/urlbuilder/SigningContextImpl.java` — field + constructor
- Modify: `src/main/java/com/widen/urlbuilder/UrlBuilder.java` — `SigningContextImpl` construction site
- Modify: `src/test/java/com/widen/urlbuilder/SigningContextTest.java` — all `-1` usages and assertions
- Modify: `src/test/java/com/widen/urlbuilder/UrlBuilderSigningTest.java` — port assertions
- Modify: `src/test/java/com/widen/urlbuilder/UrlSignerTest.java` — stub implementation
- Modify: `src/test/java//com/widen/urlbuilder/UrlSignerEdgeCaseTest.java` — port assertion

The signing API has not shipped, so this is a safe change.

- [ ] **Step 1: Update `UrlSigner.SigningContext.getPort()` interface**

In `UrlSigner.java`, change:
```java
         * @return The port number, or -1 if using the default port
         *         (80 for http, 443 for https)
         */
        int getPort();
```
To:
```java
         * @return The port number, or {@code null} if using the default port
         *         (80 for http, 443 for https)
         */
        Integer getPort();
```

- [ ] **Step 2: Update `SigningContextImpl` field and constructor**

In `SigningContextImpl.java`, change the field:
```java
    private final Integer port;
```

Change the constructor parameter:
```java
    SigningContextImpl(String protocol, String hostname, Integer port,
```

Update the constructor JavaDoc:
```java
     * @param port The port number or {@code null} for default
```

- [ ] **Step 3: Update `UrlBuilder` callsite**

In `UrlBuilder.java`, around line 806, change:
```java
        // Return -1 for default ports (80/443) or if port is not set
        int effectivePort = port;
        if (port <= 0 || port == 80 || port == 443) {
            effectivePort = -1;
        }
        
        return new SigningContextImpl(
            protocol,
            hostname != null ? hostname : "",
            effectivePort,
```
To:
```java
        // Use null for default ports (80/443) or if port is not set
        Integer effectivePort = (port > 0 && port != 80 && port != 443) ? port : null;
        
        return new SigningContextImpl(
            protocol,
            hostname != null ? hostname : "",
            effectivePort,
```

- [ ] **Step 4: Update tests — `SigningContextTest.java`**

All constructor calls passing `-1` for port change to `null`:
```java
// Before:
    -1,
// After:
    null,
```

The one assertion checking for default port:
```java
// Before:
assertEquals(-1, context.getPort());
// After:
assertNull(context.getPort());
```

The one assertion checking for explicit port 8080 remains:
```java
assertEquals(8080, context.getPort());
```

- [ ] **Step 5: Update tests — `UrlBuilderSigningTest.java`**

```java
// Before:
assertEquals(-1, context.getPort());
// After:
assertNull(context.getPort());
```

The explicit port assertion (8080) is unchanged.

- [ ] **Step 6: Update tests — `UrlSignerTest.java`**

The stub implementation:
```java
// Before:
@Override public int getPort() { return -1; }
// After:
@Override public Integer getPort() { return null; }
```

- [ ] **Step 7: Update tests — `UrlSignerEdgeCaseTest.java`**

No default-port assertions in this file (only explicit 8080 check), so it needs no change beyond compiling cleanly.

- [ ] **Step 8: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/UrlSigner.java \
        src/main/java/com/widen/urlbuilder/SigningContextImpl.java \
        src/main/java/com/widen/urlbuilder/UrlBuilder.java \
        src/test/java/com/widen/urlbuilder/SigningContextTest.java \
        src/test/java/com/widen/urlbuilder/UrlBuilderSigningTest.java \
        src/test/java/com/widen/urlbuilder/UrlSignerTest.java
git commit -m "fix: change SigningContext.getPort() from int/-1 to Integer/null for explicit default"
```

---

## Task 6: Cache static `Encoder` in `UrlSafeBase64`

**Files:**
- Modify: `src/main/java/com/widen/urlbuilder/UrlSafeBase64.java`

`Base64.getUrlEncoder().withoutPadding()` allocates a new `Encoder` instance on every `encode()` call. Store it in a static field.

- [ ] **Step 1: Add static field and update encode method**

Change:
```java
    public static String encode(byte[] data)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
```
To:
```java
    private static final Base64.Encoder URL_SAFE_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public static String encode(byte[] data)
    {
        return URL_SAFE_ENCODER.encodeToString(data);
    }
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "com.widen.urlbuilder.UrlSafeBase64Test" --info`
Expected: all pass

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/widen/urlbuilder/UrlSafeBase64.java
git commit -m "fix: cache static URL-safe Base64 encoder in UrlSafeBase64"
```

---

## Final Verification

- [ ] **Run full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Confirm no unintended diff**

Run: `git diff origin/feat/on-create-builder-actions`
Expected: only the 6 targeted changes above
