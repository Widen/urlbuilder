# JUnit 5 Migration Design

**Date:** 2026-03-06  
**Project:** urlbuilder  
**Current State:** JUnit 4.8.2, Spock 1.1, Groovy 2.4.9

## Overview

Migrate all tests from JUnit 4 to JUnit 5 and convert Spock specification to JUnit 5 parameterized tests, allowing removal of Spock and Groovy dependencies.

## Migration Approach

**Sequential with Vintage Engine**: Add JUnit 5 alongside JUnit 4 using vintage engine for compatibility, convert each test file incrementally, then remove legacy dependencies.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| JUnit version | 5.13.4 | User specified |
| Migration strategy | Sequential with vintage engine | Safe, incremental validation |
| Spock conversion target | Merge into UrlBuilderTest.java | Tests similar functionality |
| Parameterized tests | @ValueSource / @MethodSource | Native JUnit 5 approach |

## Dependency Changes

### Transitional State (During Migration)

```kotlin
// Keep JUnit 4 temporarily
testImplementation("junit:junit:4.8.2")
testRuntimeOnly("org.junit.vintage:junit-vintage-engine")

// Add JUnit 5
testImplementation(platform("org.junit:junit-bom:5.13.4"))
testImplementation("org.junit.jupiter:junit-jupiter")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

### Final State

```kotlin
testImplementation(platform("org.junit:junit-bom:5.13.4"))
testImplementation("org.junit.jupiter:junit-jupiter")
testImplementation("org.slf4j:slf4j-simple:1.7.25")
testImplementation("commons-io:commons-io:2.4")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

### Dependencies to Remove

- `junit:junit:4.8.2`
- `org.junit.vintage:junit-vintage-engine`
- `org.codehaus.groovy:groovy-all:2.4.9`
- `org.spockframework:spock-core:1.1-groovy-2.4`
- `cglib:cglib-nodep:3.2.5`
- `org.objenesis:objenesis:2.6`

### Plugin to Remove

- `groovy` plugin from plugins block

## JUnit 4 to JUnit 5 Migration Pattern

### Import Changes

```java
// Before (JUnit 4)
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.assertEquals;

// After (JUnit 5)
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
```

### Annotation Mapping

| JUnit 4 | JUnit 5 |
|---------|---------|
| `@Test` | `@Test` (different import) |
| `@Ignore` | `@Disabled` |
| `@Before` | `@BeforeEach` |
| `@After` | `@AfterEach` |
| `@BeforeClass` | `@BeforeAll` |
| `@AfterClass` | `@AfterAll` |
| `@Test(expected = X.class)` | `assertThrows(X.class, () -> ...)` |

### Exception Testing

```java
// Before (JUnit 4)
@Test(expected = IllegalArgumentException.class)
public void testThrows() {
    new UrlBuilder("", "foo").modeFullyQualified().toString();
}

// After (JUnit 5)
@Test
void testThrows() {
    assertThrows(IllegalArgumentException.class, () -> 
        new UrlBuilder("", "foo").modeFullyQualified().toString());
}
```

## Spock to JUnit 5 Conversion

### Tests to Convert

| Spock Test | Conversion |
|------------|------------|
| `def "Happy path"()` | Skip - already exists in UrlBuilderTest |
| `@Unroll def "Round-trip parse..."()` | `@ParameterizedTest` + `@ValueSource` |
| `def "Path segments are parsed..."()` | `@ParameterizedTest` + `@MethodSource` |
| `def "Query parameters as map"()` | Regular `@Test` method |

### Parameterized Test Examples

```java
// Simple list -> @ValueSource
@ParameterizedTest
@ValueSource(strings = {
    "http://my.host.com:8080/bar?a=b#foo",
    "http://my.host.com/bar?a=b#foo"
})
void roundTripParseAndToString(String url) {
    assertEquals(url, new UrlBuilder(url).toString());
}

// Table with multiple columns -> @MethodSource
@ParameterizedTest
@MethodSource("pathSegmentTestCases")
void pathSegmentsAreParsedCorrectly(String url, String path, List<String> segments) {
    UrlBuilder builder = new UrlBuilder(url);
    assertEquals(path, builder.getPath());
    assertEquals(segments, builder.getPathSegments());
}

static Stream<Arguments> pathSegmentTestCases() {
    return Stream.of(
        Arguments.of("http://my.host.com", "/", List.of()),
        Arguments.of("http://my.host.com/foo/bar", "/foo/bar", List.of("foo", "bar"))
    );
}
```

## Migration Phases

| Phase | Description | Validation |
|-------|-------------|------------|
| 1 | Add JUnit 5 deps + vintage engine + useJUnitPlatform() | `./gradlew test` |
| 2 | Convert UrlBuilderTest.java | `./gradlew test` |
| 3 | Convert S3UrlBuilderTest.java | `./gradlew test` |
| 4 | Convert CloudfrontUrlBuilderTest.java | `./gradlew test` |
| 5 | Convert HttpUtilsTest.java | `./gradlew test` |
| 6 | Convert UrlBuilderSpec.groovy, merge into UrlBuilderTest.java | `./gradlew test` |
| 7 | Remove JUnit 4, Spock, Groovy deps + plugin, delete src/test/groovy/ | `./gradlew clean test` |

## Files Changed

| File | Action |
|------|--------|
| `build.gradle.kts` | Modify (deps, plugin) |
| `src/test/java/com/widen/urlbuilder/UrlBuilderTest.java` | Modify |
| `src/test/java/com/widen/urlbuilder/S3UrlBuilderTest.java` | Modify |
| `src/test/java/com/widen/urlbuilder/CloudfrontUrlBuilderTest.java` | Modify |
| `src/test/java/com/widen/urlbuilder/HttpUtilsTest.java` | Modify |
| `src/test/groovy/` | Delete (entire directory) |

## Verification

- `./gradlew clean test` passes
- No Groovy compilation tasks run (`:compileTestGroovy` should not appear)
- All parameterized tests execute with multiple iterations
