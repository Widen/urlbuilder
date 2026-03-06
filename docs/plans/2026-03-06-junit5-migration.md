# JUnit 5 Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate all tests from JUnit 4 to JUnit 5, convert Spock spec to JUnit 5 parameterized tests, and remove Groovy/Spock dependencies.

**Architecture:** Sequential migration using JUnit vintage engine for compatibility during transition. Convert each test file incrementally, then remove legacy dependencies. Spock parameterized tests become JUnit 5 `@ParameterizedTest` with `@ValueSource` or `@MethodSource`.

**Tech Stack:** JUnit 5.13.4, JUnit Jupiter, JUnit Platform

---

### Task 1: Add JUnit 5 Dependencies and Configure Test Platform

**Files:**
- Modify: `build.gradle.kts`

**Step 1: Update dependencies in build.gradle.kts**

Add JUnit 5 BOM, Jupiter, and vintage engine for backward compatibility. Add `useJUnitPlatform()` to test task.

Replace the test dependencies section:

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok:1.18.2")
    annotationProcessor("org.projectlombok:lombok:1.18.2")

    implementation("org.bouncycastle:bcprov-jdk15to18:1.80")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.80")

    // JUnit 4 (temporary, for vintage engine compatibility)
    testImplementation("junit:junit:4.8.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")

    // JUnit 5
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Other test dependencies
    testImplementation("commons-io:commons-io:2.4")
    testImplementation("org.slf4j:slf4j-simple:1.7.25")

    // Spock (temporary, until UrlBuilderSpec is converted)
    testImplementation("org.codehaus.groovy:groovy-all:2.4.9")
    testImplementation("org.spockframework:spock-core:1.1-groovy-2.4")
    testImplementation("cglib:cglib-nodep:3.2.5")
    testImplementation("org.objenesis:objenesis:2.6")
}

tasks.test {
    useJUnitPlatform()
}
```

**Step 2: Verify all existing tests still pass**

Run: `mise exec -- ./gradlew clean test`

Expected: BUILD SUCCESSFUL, all tests pass (vintage engine runs JUnit 4 tests)

**Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add JUnit 5 dependencies with vintage engine for migration"
```

---

### Task 2: Convert HttpUtilsTest.java to JUnit 5

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/HttpUtilsTest.java`

**Step 1: Update imports and convert test**

Replace entire file content:

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpUtilsTest
{
    @Test
    void testCreateContentDispositionHeader()
    {
        assertEquals(
            "inline; filename=\"foo.jpg\"",
            HttpUtils.createContentDispositionHeader("inline", "foo.jpg")
        );
        assertEquals(
            "inline; filename=\"hello world.jpg\"",
            HttpUtils.createContentDispositionHeader("inline", "hello world.jpg")
        );
        assertEquals(
            "inline; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%22world.jpg",
            HttpUtils.createContentDispositionHeader("inline", "hello\"world.jpg")
        );
        assertEquals(
            "inline; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%5Cworld.jpg",
            HttpUtils.createContentDispositionHeader("inline", "hello\\world.jpg")
        );
        assertEquals(
            "inline; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%25world.jpg",
            HttpUtils.createContentDispositionHeader("inline", "hello%world.jpg")
        );
        assertEquals(
            "attachment; filename=\"+oo.jpg\"; filename*=UTF-8''%2B%C6%92oo.jpg",
            HttpUtils.createContentDispositionHeader("attachment", "+ƒoo.jpg")
        );
        assertEquals(
            "attachment; filename=\"foo.jpg\"; filename*=UTF-8''%F0%A2%83%87%F0%A2%9E%B5%F0%A2%AB%95foo%F0%A2%AD%83%F0%A2%AF%8A%F0%A2%B1%91%F0%A2%B1%95.jpg",
            HttpUtils.createContentDispositionHeader("attachment", "𢃇𢞵𢫕foo𢭃𢯊𢱑𢱕.jpg")
        );
        assertEquals(
            "attachment; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%0Aworld.jpg",
            HttpUtils.createContentDispositionHeader("attachment", "hello\nworld.jpg")
        );
        assertEquals(
            "attachment; filename=\"resume.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf",
            HttpUtils.createContentDispositionHeader("attachment", "résumé.pdf")
        );
    }
}
```

**Step 2: Verify test passes**

Run: `mise exec -- ./gradlew test --tests HttpUtilsTest`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/test/java/com/widen/urlbuilder/HttpUtilsTest.java
git commit -m "test: convert HttpUtilsTest to JUnit 5"
```

---

### Task 3: Convert CloudfrontUrlBuilderTest.java to JUnit 5

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/CloudfrontUrlBuilderTest.java`

**Step 1: Update imports and annotations**

Replace entire file content:

```java
package com.widen.urlbuilder;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CloudfrontUrlBuilderTest
{

    private PrivateKey pem;
    private PrivateKey der;

    @BeforeEach
    void setup() throws IOException
    {
        pem = CloudfrontPrivateKeyUtils.fromPemString(IOUtils.toString(getClass().getResourceAsStream("/test-cf.pem")));
        der = CloudfrontPrivateKeyUtils.fromDerBinary(getClass().getResourceAsStream("/test-cf.der"));
    }

    @Test
    void secondUseOfBuilderEqualsNewed()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem).withAttachmentFilename("test.jpg").expireAt(new Date(1381356586000L));
        builder.toString();

        builder.withKey("/0/b/c/d/test2.jpeg");
        String secondUseBuilderValue = builder.toString();

        CloudfrontUrlBuilder builder2 = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test2.jpeg", "APKAIW7O5EPF5UBMJ7KQ", der, "BC").withAttachmentFilename("test.jpg").expireAt(new Date(1381356586000L));
        assertEquals(secondUseBuilderValue, builder2.toString());
    }

    @Test
    void testNonAsciiCharsInAttachment()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem).withAttachmentFilename("+ƒoo.jpg").expireAt(new Date(1381356586000L));

        assertEquals("http://dnnfhn216qiqy.cloudfront.net/0/b/c/d/test.jpeg?response-content-disposition=attachment%3B%20filename%3D%22%2Boo.jpg%22%3B%20filename*%3DUTF-8%27%27%252B%25C6%2592oo.jpg&Expires=1381356586&Signature=h8Z0hTcpvPzSxmgMjQGynOSCN-2pFTVnJQPG8bxXQ6rDWvVnVPvMOt3OrkACtLFf7NAhJbx4XpJTo3shlRYsG4E2cS5aRB6ko2N0C18hq3scySjZzLAMVLpqOTR6rK9j4Rc9dHpuZ6IlZ~qJ2xE8C516JvRXY4TLZp84WjBQZQOe6FiLuVy-sIFfAs5X1eqWgHCJKLgqBeozJlijH8jv3V1kTJADoGvOpvvKXDSjujv~u5QJ1pE6COo6vHn4PKNf4Dh-RiWU--Uqbtw26qo8fwQmBo6V4TJeQXwzWaZl74hwr7x4bUArdZLYQz892d3aHzdtZucKgIl~xMQy6kchVw__&Key-Pair-Id=APKAIW7O5EPF5UBMJ7KQ", builder.toString());
    }

}
```

**Step 2: Verify tests pass**

Run: `mise exec -- ./gradlew test --tests CloudfrontUrlBuilderTest`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/test/java/com/widen/urlbuilder/CloudfrontUrlBuilderTest.java
git commit -m "test: convert CloudfrontUrlBuilderTest to JUnit 5"
```

---

### Task 4: Convert S3UrlBuilderTest.java to JUnit 5

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/S3UrlBuilderTest.java`

**Step 1: Update imports and annotations**

Key changes:
- `org.junit.Test` → `org.junit.jupiter.api.Test`
- `org.junit.Ignore` → `org.junit.jupiter.api.Disabled`
- `org.junit.Assert.assertEquals` → `org.junit.jupiter.api.Assertions.assertEquals`

Update imports at top of file:

```java
package com.widen.urlbuilder;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
```

Replace `@Ignore` with `@Disabled` and remove `public` from test methods (JUnit 5 convention).

**Step 2: Verify tests pass**

Run: `mise exec -- ./gradlew test --tests S3UrlBuilderTest`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/test/java/com/widen/urlbuilder/S3UrlBuilderTest.java
git commit -m "test: convert S3UrlBuilderTest to JUnit 5"
```

---

### Task 5: Convert UrlBuilderTest.java to JUnit 5

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/UrlBuilderTest.java`

**Step 1: Update imports**

```java
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
```

**Step 2: Convert expected exception tests**

Replace:
```java
@Test(expected = UrlBuilder.NonParsableUrl.class)
public void testInvalidFQSpec()
{
    String url = new UrlBuilder("htt").toString();
}
```

With:
```java
@Test
void testInvalidFQSpec()
{
    assertThrows(UrlBuilder.NonParsableUrl.class, () ->
        new UrlBuilder("htt").toString());
}
```

Replace:
```java
@Test(expected = IllegalArgumentException.class)
public void testFullyQualifiedThrowsExceptionWhenHostnameNull()
{
    new UrlBuilder(null, "foo/bar/baz.html").modeFullyQualified().toString();
}
```

With:
```java
@Test
void testFullyQualifiedThrowsExceptionWhenHostnameNull()
{
    assertThrows(IllegalArgumentException.class, () ->
        new UrlBuilder(null, "foo/bar/baz.html").modeFullyQualified().toString());
}
```

Replace:
```java
@Test(expected = IllegalArgumentException.class)
public void testFullyQualifiedThrowsExceptionWhenHostnameBlank()
{
    new UrlBuilder("", "foo/bar/baz.html").modeFullyQualified().toString();
}
```

With:
```java
@Test
void testFullyQualifiedThrowsExceptionWhenHostnameBlank()
{
    assertThrows(IllegalArgumentException.class, () ->
        new UrlBuilder("", "foo/bar/baz.html").modeFullyQualified().toString());
}
```

**Step 3: Remove `public` from all test methods** (JUnit 5 convention allows package-private)

**Step 4: Verify tests pass**

Run: `mise exec -- ./gradlew test --tests UrlBuilderTest`

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/test/java/com/widen/urlbuilder/UrlBuilderTest.java
git commit -m "test: convert UrlBuilderTest to JUnit 5"
```

---

### Task 6: Convert UrlBuilderSpec.groovy to JUnit 5 and Merge into UrlBuilderTest.java

**Files:**
- Modify: `src/test/java/com/widen/urlbuilder/UrlBuilderTest.java`
- Delete: `src/test/groovy/com/widen/urlbuilder/UrlBuilderSpec.groovy`
- Delete: `src/test/groovy/` (entire directory)

**Step 1: Add parameterized test imports to UrlBuilderTest.java**

Add to imports:
```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;
```

**Step 2: Add round-trip parameterized test**

Add this test method to UrlBuilderTest class:

```java
@ParameterizedTest
@ValueSource(strings = {
    "http://my.host.com:8080/bar?a=b#foo",
    "http://my.host.com/bar?a=b#foo",
    "https://my.host.com/bar?a=b#foo",
    "https://my.host.com:8080/bar?a=b&c=d"
})
void roundTripParseAndToString(String url)
{
    UrlBuilder builder = new UrlBuilder(url);
    assertEquals(url, builder.toString());
}
```

**Step 3: Add path segments parameterized test**

Add test method and data provider:

```java
@ParameterizedTest
@MethodSource("pathSegmentTestCases")
void pathSegmentsAreParsedCorrectly(String url, String expectedPath, List<String> expectedSegments)
{
    UrlBuilder builder = new UrlBuilder(url);
    assertEquals(expectedPath, builder.getPath());
    assertEquals(expectedSegments, builder.getPathSegments());
}

static Stream<Arguments> pathSegmentTestCases()
{
    return Stream.of(
        Arguments.of("http://my.host.com", "/", List.of()),
        Arguments.of("http://my.host.com/foo/bar", "/foo/bar", List.of("foo", "bar")),
        Arguments.of("http://my.host.com/foo//bar/", "/foo/bar", List.of("foo", "bar"))
    );
}
```

**Step 4: Add query parameters as map test**

Add this test method:

```java
@Test
void queryParametersAsMap()
{
    UrlBuilder builder = new UrlBuilder("https://my.host.com/bar?a=x&b=2&c=3&c=4&a&d#foo");

    assertEquals(4, builder.getQueryParameters().size());
    assertEquals(List.of("x", ""), builder.getQueryParameters().get("a"));
    assertEquals(List.of("2"), builder.getQueryParameters().get("b"));
    assertEquals(List.of("3", "4"), builder.getQueryParameters().get("c"));
    assertEquals(List.of(""), builder.getQueryParameters().get("d"));
}
```

**Step 5: Verify tests pass**

Run: `mise exec -- ./gradlew test --tests UrlBuilderTest`

Expected: BUILD SUCCESSFUL with parameterized tests showing multiple iterations

**Step 6: Delete Groovy spec and directory**

Run:
```bash
rm -rf src/test/groovy
```

**Step 7: Verify all tests still pass**

Run: `mise exec -- ./gradlew clean test`

Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add src/test/java/com/widen/urlbuilder/UrlBuilderTest.java
git add -u  # stages the deletion
git commit -m "test: convert UrlBuilderSpec from Spock to JUnit 5 parameterized tests

- Add @ParameterizedTest with @ValueSource for round-trip URL parsing
- Add @ParameterizedTest with @MethodSource for path segment parsing
- Add queryParametersAsMap test
- Delete src/test/groovy/ directory"
```

---

### Task 7: Remove JUnit 4, Spock, and Groovy Dependencies

**Files:**
- Modify: `build.gradle.kts`

**Step 1: Update build.gradle.kts**

Remove `groovy` plugin from plugins block:

```kotlin
plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}
```

Update dependencies to final state:

```kotlin
dependencies {
    compileOnly("org.projectlombok:lombok:1.18.2")
    annotationProcessor("org.projectlombok:lombok:1.18.2")

    implementation("org.bouncycastle:bcprov-jdk15to18:1.80")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.80")

    // JUnit 5
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Other test dependencies
    testImplementation("commons-io:commons-io:2.4")
    testImplementation("org.slf4j:slf4j-simple:1.7.25")
}

tasks.test {
    useJUnitPlatform()
}
```

**Step 2: Verify clean build passes**

Run: `mise exec -- ./gradlew clean test`

Expected: 
- BUILD SUCCESSFUL
- No `:compileTestGroovy` task in output
- All tests pass

**Step 3: Verify no Groovy compilation**

Run: `mise exec -- ./gradlew test --info 2>&1 | grep -i groovy || echo "No Groovy tasks found"`

Expected: "No Groovy tasks found"

**Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "build: remove JUnit 4, Spock, and Groovy dependencies

- Remove groovy plugin
- Remove junit:junit, junit-vintage-engine
- Remove spock-core, groovy-all, cglib, objenesis
- Final test stack: JUnit 5.13.4"
```

---

### Task 8: Push Changes to PR

**Step 1: Push all commits**

Run: `git push`

**Step 2: Verify CI passes**

Check GitHub Actions for the PR.

---

## Summary

After completing all tasks:

- JUnit: 4.8.2 → 5.13.4
- Spock: Removed (converted to JUnit 5 parameterized tests)
- Groovy: Removed (no longer needed)
- All tests passing
- Clean dependency tree
