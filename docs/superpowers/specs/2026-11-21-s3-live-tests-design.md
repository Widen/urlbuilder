# S3UrlBuilder Live Tests

**Repo:** `Widen/urlbuilder`, branch `hardening-11-s3-live-tests` (layer 11 of stack #51, on top of
`hardening-10-s3-sigv4`). Follows the SigV4 design (`2026-11-20-s3-sigv4-design.md`).

## Intent

The offline tests show that `S3UrlBuilder` writes the same presigned URLs as the AWS SDK. They do not show that S3
accepts them. Live tests send the builder's URLs to real S3 buckets so that Uriah can trust the signing before the 3.0
release. Uriah runs them locally with his own `dev` credentials; they never run in CI.

## Goals

- S3 serves the right object for every presigned GET the builder writes, for every case listed under Coverage.
- S3 rejects URLs that should fail: an expired URL, a tampered signature, and the wrong signing region.
- One command runs them: `aws-vault exec dev -- mise run live-test`.

## Non-goals

- Running in CI, or credentials other than an aws-vault session.
- Long-term keys without a session token (aws-vault always issues a session token).
- `usingBucketInHostname`, which needs DNS for a custom hostname.
- Anything but GET.

## Anti-goals

- Live tests that run from `test` or `check`, or that fail when no bucket is configured. A build without AWS access must
  stay green.

## Approaches

1. **Tagged JUnit tests behind a separate Gradle task (chosen).** Reuses the SDK test dependency and JUnit assertions.
2. **A shell script with curl.** Easy to poke at by hand, but duplicates the key list and adds a second toolchain.
3. **JUnit tests that create and delete their own bucket each run.** No setup, but a failed run leaves a bucket behind.

Uriah picked approach 1.

## Design

- **Buckets** in the `dev` account, created once with the AWS CLI, default settings (public access blocked, no
  versioning). My call: two buckets.
  - `widen-urlbuilder-live-test-us-east-2`: `us-east-2` accepts only SigV4, so it proves the default signature.
  - `widen-urlbuilder-live-test-us-east-1`: covers the global endpoint `s3.amazonaws.com` and the SigV2 opt-in, which
    `us-east-1` still accepts.
- **Gradle:** `test` and `testOnJava21` exclude the JUnit tag `live`. A new `liveTest` task runs only that tag, on the
  Java 8 toolchain, and never counts as up to date.
- **mise task** `live-test` sets the two bucket names as environment variables and runs `./gradlew liveTest`.
- **Test class** `S3UrlBuilderLiveTest`, tagged `live`. It is skipped, through a JUnit assumption, when the bucket
  variables or `AWS_ACCESS_KEY_ID` are missing. It reads `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and
  `AWS_SESSION_TOKEN`.
  - Setup uploads each key from the SDK test's `keys` list to both buckets with the SDK v1 client. Each object's body is
    its key in UTF-8, so a response proves which object S3 served. Uploads overwrite, so reruns need no cleanup.
  - Each case builds a URL, GETs it with `HttpURLConnection`, and asserts the status. A success case also asserts the
    body and, where set, the `Content-Disposition` and `Content-Type` response headers.
- **JavaDoc** on the test class names the buckets, the one-time CLI commands that create them, and the run command.

## Coverage

- SigV4 in `us-east-2` with `inRegion`, every key, virtual-host and path-style, over HTTPS.
- SigV4 in `us-east-1` with `withEndpoint("s3.amazonaws.com", "us-east-1")`, every key.
- SigV2 in `us-east-1` with `usingSignatureVersion2()`, every key.
- `withAttachmentFilename("Quarterly Report é+1.pdf")` and `withContentType("text/plain")`, under SigV4 and SigV2.
- One plain-HTTP SigV4 URL.
- Rejections, each expecting 403 or 400:
  - expired: signed through the package-private clock two hours in the past with a one-hour expiration;
  - tampered: one character of `X-Amz-Signature` changed;
  - wrong region: a `us-east-2` bucket signed for `us-east-1`.

## Left to the builder

- How keys are shared with `S3UrlBuilderAwsSdkTest` (a package-private constant is fine).
- Exact assertion on rejection status codes, as long as a 2xx fails the test.
- Teardown of the buckets is out of scope; deleting them needs Uriah's approval.
