# S3UrlBuilder Live Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tests tagged `live` send the presigned URLs `S3UrlBuilder` writes to real S3 buckets and check that S3
serves the right object or rejects the URL, run locally with `aws-vault exec dev -- mise run live-test`.

**Architecture:** A JUnit 5 class `S3UrlBuilderLiveTest`, tagged `live`, uploads fixture objects with the AWS SDK v1
in `@BeforeAll`, then GETs builder URLs with `HttpURLConnection`. A `liveTest` Gradle task runs only that tag; `test`
and `testOnJava21` exclude it. A mise task supplies the bucket names. Two buckets in the `dev` account: one in
`us-east-2` (SigV4 only) and one in `us-east-1` (global endpoint and SigV2).

**Tech Stack:** Java 8 (source and target), JUnit 5.13, Gradle 9 through `mise exec`, AWS SDK for Java v1 1.12.797
(already a test dependency), mise tasks, aws-vault.

**Spec:** `docs/superpowers/specs/2026-11-21-s3-live-tests-design.md`

**Issue:** https://github.com/Widen/urlbuilder/issues/57

## Global Constraints

- Java 8 language level in test code: no `var`, `List.of`, `InputStream.readAllBytes`, or other post-8 APIs.
  `commons-io` (`IOUtils.toString(InputStream, StandardCharsets.UTF_8)`) is already a test dependency.
- No change to `src/main`. A live case that fails is a finding to report, not something to fix in this plan.
- Live tests never run from `test`, `testOnJava21`, or `check`, and they are skipped (not failed) when the bucket
  variables or `AWS_ACCESS_KEY_ID` are missing.
- Bucket names: `widen-urlbuilder-live-test-us-east-2` (region `us-east-2`) and `widen-urlbuilder-live-test-us-east-1`
  (region `us-east-1`), passed as `URLBUILDER_LIVE_BUCKET_US_EAST_2` and `URLBUILDER_LIVE_BUCKET_US_EAST_1`.
- Credentials come only from `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and `AWS_SESSION_TOKEN`. Never print them.
- Run Gradle as `mise exec -- ./gradlew ...`, never `check`, `build`, or Spotless. Redirect output to a file and read
  its end: `mise exec -- ./gradlew test -q --console=plain > build/test-output.txt 2>&1; echo "exit=$?"`.
- Implementer subagents have no AWS credentials. They verify compilation and the skip behavior; the controller runs
  the live suite.
- Commit messages follow conventional commits, wrapped at 72 columns, with no `Co-Authored-By` or tool attribution.

## Review Focus

1. Running `liveTest` without credentials (a teammate without aws-vault): every live test reports skipped and the task
   exits 0. Task 1 Step 4 checks it.
2. A redirect from S3 (a 301 or 307 for a bucket reached through the wrong regional endpoint): `HttpURLConnection`
   must not follow it, or a wrong-region URL could pass. Task 1 sets `setInstanceFollowRedirects(false)`, and Task 3's
   wrong-region case relies on it.
3. A 4xx response: `getInputStream()` throws on 4xx, hiding S3's error code. The helper reads `getErrorStream()` for
   any status of 400 or more, so failure messages show S3's `<Code>`. Task 1's helper; Task 3 asserts on it.
4. A rerun against buckets that already hold the fixtures: `putObject` overwrites, so a second run passes unchanged.
   The controller runs the suite twice in Task 4.
5. A clock off by more than 15 minutes on the machine running the tests: S3 rejects every SigV4 URL. Not testable;
   the class JavaDoc names it as the first thing to check when every case fails.

---

### Task 0: Provision the buckets (controller only)

Uriah authorized creating these buckets and objects in the `dev` profile. Do not run any other AWS write.

- [ ] **Step 1: Check the credentials**

Run: `aws-vault exec dev -- aws sts get-caller-identity --query Arn --output text`
Expected: one ARN. If aws-vault needs an MFA prompt the Bash tool cannot answer, ask Uriah to run the commands below
with `!`.

- [ ] **Step 2: Create the buckets**

```sh
aws-vault exec dev -- aws s3api create-bucket --bucket widen-urlbuilder-live-test-us-east-2 --region us-east-2 \
  --create-bucket-configuration LocationConstraint=us-east-2
aws-vault exec dev -- aws s3api create-bucket --bucket widen-urlbuilder-live-test-us-east-1 --region us-east-1
```

Expected: each prints a `Location`. If a name is taken by another account (`BucketAlreadyExists`), stop and ask
Uriah for a new name, then update the Global Constraints and Task 1's mise task.

- [ ] **Step 3: Confirm the regions**

Run `aws-vault exec dev -- aws s3api get-bucket-location --bucket NAME` for each bucket.
Expected: `us-east-2` for the first and `null` (meaning `us-east-1`) for the second.

---

### Task 1: Live test harness and SigV4 success cases

**Files:**

- Modify: `build.gradle.kts` (the `tasks.withType<Test>()` block and the `testOnJava21` registration)
- Modify: `mise.toml`
- Modify: `src/test/java/com/widen/urlbuilder/S3UrlBuilderAwsSdkTest.java` (the `keys` field)
- Create: `src/test/java/com/widen/urlbuilder/S3UrlBuilderLiveTest.java`

**Interfaces:**

- Produces, in `S3UrlBuilderLiveTest`:
  - `static Stream<String> keys()`: the keys from `S3UrlBuilderAwsSdkTest.keys`, for `@MethodSource("keys")`.
  - `private static S3UrlBuilder signed(String bucket, String key)`: a builder for `bucket` and `key` with
    `usingSsl()`, `expireIn(Duration.ofMinutes(15))`, and the environment's credentials (the three-argument
    `usingCredentials` when `AWS_SESSION_TOKEN` is set, otherwise the two-argument one). Placement and endpoint are
    left to the caller.
  - `private static Response get(String url)`: opens `new URL(url)` with redirects off, reads the status, the
    `Content-Type` and `Content-Disposition` headers, and the body as UTF-8 (from `getErrorStream()` when the status
    is 400 or more).
  - `private static final class Response` with fields `int status`, `String body`, `String contentType`,
    `String contentDisposition`, and a `toString()` that returns status and body for assertion messages.
  - `private static void assertServes(String key, String url)`: asserts `status == 200` and `body.equals(key)`, with
    the URL and the response in the message.
  - Fields `bucketUsEast2` and `bucketUsEast1` read from the environment in `@BeforeAll`.
- Produces in Gradle: task `liveTest`; JUnit tag `live`.
- Produces in mise: task `live-test`.
- [ ] **Step 1: Exclude the `live` tag from the existing test tasks and add `liveTest`**

In `build.gradle.kts`, keep `useJUnitPlatform()` in `tasks.withType<Test>().configureEach` and add:

- `tasks.test { useJUnitPlatform { excludeTags("live") } }`
- `excludeTags("live")` in the `testOnJava21` registration's `useJUnitPlatform { }`
- `val liveTest = tasks.register<Test>("liveTest")` with description `"Runs the tests that call S3; needs AWS
  credentials in the environment."`, group verification, the same `testClassesDirs` and `classpath` as
  `testOnJava21`, `useJUnitPlatform { includeTags("live") }`, and `outputs.upToDateWhen { false }`. It uses the
  project's Java 8 toolchain by default. Do not add it to `check`.
- [ ] **Step 2: Add the mise task**

```toml
[tasks.live-test]
description = "Run the S3 live tests: aws-vault exec dev -- mise run live-test"
run = '''
URLBUILDER_LIVE_BUCKET_US_EAST_2="${URLBUILDER_LIVE_BUCKET_US_EAST_2:-widen-urlbuilder-live-test-us-east-2}" \
URLBUILDER_LIVE_BUCKET_US_EAST_1="${URLBUILDER_LIVE_BUCKET_US_EAST_1:-widen-urlbuilder-live-test-us-east-1}" \
./gradlew liveTest --console=plain
'''
```

The defaults live in `run`, not in a task `env` block, so a caller can still override them.

- [ ] **Step 3: Write the test class**

Make `S3UrlBuilderAwsSdkTest.keys` package-private (drop `private`). Create `S3UrlBuilderLiveTest`, annotated
`@Tag("live")`, with class JavaDoc that names the two buckets and their regions, the Task 0 `create-bucket`
commands, the run command, and the note that a machine clock off by more than 15 minutes fails every case.

`@BeforeAll static void uploadFixtures()`:

- `assumeTrue` that both bucket variables and `AWS_ACCESS_KEY_ID` are set, with the message
  `"set AWS credentials and the bucket variables; run: aws-vault exec dev -- mise run live-test"`.
- For each bucket, an `AmazonS3` from `AmazonS3ClientBuilder.standard()` with
  `EnvironmentVariableCredentialsProvider` and the bucket's region, then `putObject(bucket, key, key)` for every key.

Tests, each `@ParameterizedTest @MethodSource("keys")` unless noted:

```java
void signsV4VirtualHost(String key)        // signed(bucketUsEast2, key).inRegion("us-east-2")
void signsV4PathStyle(String key)          // same, plus usingBucketInPath()
void signsV4GlobalEndpoint(String key)     // signed(bucketUsEast1, key).withEndpoint("s3.amazonaws.com", "us-east-1")
@Test void signsV4OverHttp()               // key "test-key.txt", us-east-2, inRegion, usingSsl(false)
```

Each calls `assertServes(key, builder.toString())`.

- [ ] **Step 4: Verify the default tasks skip the live tests and `liveTest` skips without credentials**

Run: `mise exec -- ./gradlew test liveTest -q --console=plain > build/test-output.txt 2>&1; echo "exit=$?"`, with
`AWS_ACCESS_KEY_ID` unset in the environment. Expected: `exit=0`; no `S3UrlBuilderLiveTest` result under
`build/test-results/test/`; under `build/test-results/liveTest/`, every `S3UrlBuilderLiveTest` case is `<skipped>`.

- [ ] **Step 5: Commit**

```sh
git add build.gradle.kts mise.toml src/test/java/com/widen/urlbuilder/S3UrlBuilderAwsSdkTest.java \
  src/test/java/com/widen/urlbuilder/S3UrlBuilderLiveTest.java
git commit -m "test(s3): send SigV4 presigned URLs to real S3 buckets"
```

---

### Task 2: Response headers and SigV2

**Files:**

- Modify: `src/test/java/com/widen/urlbuilder/S3UrlBuilderLiveTest.java`

**Interfaces:**

- Consumes: `keys()`, `signed`, `get`, `Response`, `assertServes`, `bucketUsEast2`, `bucketUsEast1` from Task 1.

- [ ] **Step 1: Add the tests**

```java
@ParameterizedTest @MethodSource("keys")
void signsV2(String key)                 // signed(bucketUsEast1, key).usingSignatureVersion2(); assertServes

@ParameterizedTest @ValueSource(booleans = {false, true})
void overridesResponseHeaders(boolean signatureVersion2)
```

`overridesResponseHeaders` uses key `test-key.txt`, `withAttachmentFilename("Quarterly Report é+1.pdf")`, and
`withContentType("application/pdf")`: SigV4 on `bucketUsEast2` with `inRegion("us-east-2")`, or SigV2 on
`bucketUsEast1` with `usingSignatureVersion2()`. Assert:

```java
assertEquals(200, response.status, response.toString());
assertEquals("application/pdf", response.contentType);
assertEquals(HttpUtils.createContentDispositionHeader("attachment", "Quarterly Report é+1.pdf"),
    response.contentDisposition);
```

Suppress the deprecation warning on `usingSignatureVersion2()` the way `S3UrlBuilderAwsSdkTest` does, if it does.

- [ ] **Step 2: Verify it compiles and skips without credentials**

Run the Task 1 Step 4 command. Expected: the same result, with the new cases skipped.

- [ ] **Step 3: Commit**

```sh
git add src/test/java/com/widen/urlbuilder/S3UrlBuilderLiveTest.java
git commit -m "test(s3): check SigV2 and response headers against real S3"
```

---

### Task 3: Rejected URLs

**Files:**

- Modify: `src/test/java/com/widen/urlbuilder/S3UrlBuilderLiveTest.java`

**Interfaces:**

- Consumes: `signed`, `get`, `Response`, `bucketUsEast2` from Task 1.

All three use key `test-key.txt` on `bucketUsEast2`. Each asserts the status and that the body holds S3's error code,
with `response.toString()` as the message.

- [ ] **Step 1: Add the tests**

```java
@Test void rejectsExpiredUrl()
// usingClock(Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC)), expireIn(Duration.ofHours(1)),
// inRegion("us-east-2"). Expect 403 and "<Code>AccessDenied</Code>".

@Test void rejectsTamperedSignature()
// inRegion("us-east-2"); change the last hex digit of X-Amz-Signature (replace it with a different hex digit).
// Expect 403 and "<Code>SignatureDoesNotMatch</Code>".

@Test void rejectsWrongRegion()
// withEndpoint("s3.us-east-2.amazonaws.com", "us-east-1"). Expect 400 and "<Code>AuthorizationQueryParametersError</Code>".
```

`signed` sets `expireIn(15 minutes)`; `rejectsExpiredUrl` calls `usingClock` and `expireIn` after it, which
replace those values. If S3 returns a different error code than expected in Task 4, record what it returned and
correct the assertion; a 2xx is the only result that means the builder is wrong.

- [ ] **Step 2: Verify it compiles and skips without credentials**

Run the Task 1 Step 4 command. Expected: the same result, with the new cases skipped.

- [ ] **Step 3: Commit**

```sh
git add src/test/java/com/widen/urlbuilder/S3UrlBuilderLiveTest.java
git commit -m "test(s3): check that S3 rejects expired, tampered, and misregioned URLs"
```

---

### Task 4: Run the live suite (controller only)

- [ ] **Step 1: Run it**

Run: `aws-vault exec dev -- mise run live-test > build/live-output.txt 2>&1; echo "exit=$?"`. If aws-vault needs
MFA, ask Uriah to run `! aws-vault exec dev -- mise run live-test`.
Expected: `exit=0`, and every `S3UrlBuilderLiveTest` case passed (none skipped) in `build/test-results/liveTest/`.

- [ ] **Step 2: Run it again**

Same command. Expected: same result, proving reruns need no cleanup.

- [ ] **Step 3: Triage failures**

A success case that fails, or a rejection case that gets a 2xx, is a builder bug in layer 10 or earlier: report it to
Uriah with the case, the URL with `X-Amz-Credential`, `X-Amz-Security-Token`, and `X-Amz-Signature` (or SigV2's
`AWSAccessKeyId`, `x-amz-security-token`, and `Signature`) redacted, and S3's error body. Do not change
`src/main`. A rejection case that gets a different 4xx code than expected only needs its assertion corrected, in its
own commit (`test(s3): match the error code S3 returns for ...`).
