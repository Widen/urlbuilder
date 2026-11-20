# S3UrlBuilder Signature Version 4

**Repo:** `Widen/urlbuilder`, branch `hardening-10-s3-sigv4` (layer 10 of stack #51, on top of
`hardening-9-s3-key-encoding`). Version 3.0.0, unreleased.

## Intent

AWS is turning off Signature Version 2 (SigV2) for S3. Regions launched after January 30, 2014 accept only Signature
Version 4 (SigV4). Eight regions launched before 2014 still accept SigV2 for every bucket, whatever its creation date,
and an account can deny SigV2 anywhere with the `s3:signatureversion` IAM condition key. AWS recommends moving every
application to SigV4
([AWS blog, updated August 4, 2026](https://aws.amazon.com/blogs/aws/amazon-s3-update-sigv2-deprecation-period-extended-modified/)).

`S3UrlBuilder` signs presigned GET URLs with SigV2 only. Its main consumer, `Widen/dam`
(`collective/filemanagement/S3FileManagementImpl.java`), builds download links with it. A link it signs today fails for
any bucket that rejects SigV2. Version 3.0 makes SigV4 the default signature so those links keep working.

## Goals

- `S3UrlBuilder.toString()` presigns with SigV4 by default whenever an expiration is set, the same condition that
  triggers SigV2 signing today.
- Every feature that works with SigV2 works with SigV4: all three bucket placements, regional and custom endpoints,
  HTTPS, protocol-relative mode, fragments, session tokens, `withAttachmentFilename`, and `withContentType`.
- SigV4 output matches the AWS SDK for Java v1 presigner for the same inputs, checked by tests.
- SigV2 stays available as a deprecated opt-in for buckets and S3-compatible services that need it.

## Non-goals

- A general SigV4 signer for other AWS services or for `UrlBuilder`. S3 differs from other services (it encodes the
  path once and signs `UNSIGNED-PAYLOAD`), and nothing needs another service yet.
- Presigning anything but GET: no PUT, POST policies, or multipart uploads.
- SigV4a (multi-region access points) and header-based authorization.
- Any change to `CloudfrontUrlBuilder`. CloudFront signs its own policies with RSA, and CloudFront's requests to an S3
  origin are signed by CloudFront itself.
- Changing `Widen/dam`. The upgrade notes tell it what it must change.

## Anti-goals

- A runtime dependency on the AWS SDK. The library depends only on BouncyCastle, and the SDK stays a test dependency.
- Clamping an expiration longer than 7 days, or falling back to SigV2 when SigV4 cannot sign. Either produces a link
  that fails later, far from the code that built it.
- Defaulting the signing region. A link signed for the wrong region fails at request time with a 400.

## Constraints

- Java 8 source and target; the build also runs the tests on Java 21 (`testOnJava21`).
- SigV4 presigned URLs are valid for at most 7 days (604,800 seconds).
- The signing region must match the bucket's region exactly.
- The canonical request signs the path as S3 decodes it. Layer 9 made `S3UrlBuilder` encode keys as the AWS SDKs do
  (everything but `A-Z a-z 0-9 - . _ ~ /`, with `%2F` after the first slash of a run), so the canonical URI is the
  encoded path the builder writes, with each `%2F` signed as `/`, as the AWS SDK for Java v1 signs it.

## Approaches

1. **SigV4 inside `S3UrlBuilder` (chosen).** A second signing method beside SigV2, picked by a flag, in the same
   `toString()` flow. Smallest public API; nothing reusable outside S3.
2. **A general `AwsSigV4Signer implements UrlSigner`.** Reusable by any `UrlBuilder`, but a larger public API with
   options for S3's differences that nothing uses yet.
3. **Delegate to the AWS SDK v2 presigner.** Correct by construction, but every consumer would pull the SDK in at
   runtime.

Uriah picked approach 1: it meets the need with the least API to support, and the SDK comparison tests check it. If
another AWS service needs signing later, the signer can move behind `UrlSigner` then.

## Decided

- **SigV4 is the default.** A new deprecated method, `usingSignatureVersion2()`, restores SigV2 signing for that
  builder. No method is needed to select SigV4.
- **A signed URL requires a region.** `toString()` throws `IllegalStateException` when SigV4 signing is due and no
  region is set. Unsigned URLs and SigV2 URLs need no region.
  - `inRegion(region)` sets the signing region as well as the endpoint `s3.REGION.amazonaws.com`.
  - A new `withEndpoint(endpoint, region)` sets both for an S3-compatible service or a custom endpoint.
  - `withEndpoint(endpoint)` keeps its current meaning and clears any region, including one `inRegion` set earlier,
    so a SigV4 URL on that endpoint throws until a region is given. (My call: a region left over from `inRegion` would
    sign for a different endpoint.)
- **Expirations longer than 7 days throw** `IllegalArgumentException` from `toString()`, as does an expiration at or
  before the signing time. The check happens at `toString()` because the signature version may be chosen after the
  expiration, and `expireAt` is relative to the moment of signing. These checks apply to SigV4 only; SigV2 keeps
  accepting any expiration, as its existing tests with 2018 and far-future dates expect.
- **The signing time is the moment `toString()` runs**, read from a clock that tests can fix.

## The SigV4 URL

For a builder with key `docs/a b.txt`, bucket `my-bucket`, `inRegion("us-west-2")`, HTTPS, and `expireIn(1 hour)`,
`toString()` writes:

```text
https://my-bucket.s3.us-west-2.amazonaws.com/docs/a%20b.txt
  ?X-Amz-Algorithm=AWS4-HMAC-SHA256
  &X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20261120%2Fus-west-2%2Fs3%2Faws4_request
  &X-Amz-Date=20261120T150000Z
  &X-Amz-Expires=3600
  &X-Amz-SignedHeaders=host
  &X-Amz-Signature=...
```

(Shown on several lines for reading; the URL has none.)

- Query parameters:
  - `X-Amz-Algorithm` is `AWS4-HMAC-SHA256`.
  - `X-Amz-Credential` is `ACCESS_KEY/YYYYMMDD/REGION/s3/aws4_request`.
  - `X-Amz-Date` is the signing time in UTC, `YYYYMMDD'T'HHMMSS'Z'`.
  - `X-Amz-Expires` is the whole seconds from the signing time to the expiration.
  - `X-Amz-SignedHeaders` is `host`.
  - `X-Amz-Security-Token` is present only with session credentials.
  - `X-Amz-Signature` is the hex HMAC-SHA256 signature.
- Canonical request, lines joined with `\n`:
  1. `GET`
  2. The encoded path as the URL writes it, with each `%2F` written as `/`.
  3. The canonical query string: every query parameter except `X-Amz-Signature`, including
     `response-content-disposition` and `response-content-type`, each name and value encoded with the unreserved set
     (`A-Z a-z 0-9 - . _ ~`), sorted by encoded name.
  4. `host:` followed by the URL's host, plus `:PORT` when the URL writes a port; then an empty line.
  5. `host`
  6. `UNSIGNED-PAYLOAD`
- String to sign: `AWS4-HMAC-SHA256`, the `X-Amz-Date` value, the credential scope `YYYYMMDD/REGION/s3/aws4_request`,
  and the hex SHA-256 of the canonical request, joined with `\n`.
- Signing key: HMAC-SHA256 chained over `"AWS4" + secret`, then the date, the region, `s3`, and `aws4_request`.
- Protocol-relative URLs sign the same host; the scheme is not part of the signature.
- A fragment is written after the query and is not signed.
- The signature parameters follow the caller's response-header parameters in the written URL, as SigV2's do today. S3
  does not care about their order; the canonical query string is sorted regardless.

## Testing

- **Known-answer test** from the AWS documentation's presigned URL example ("Authenticating Requests: Using Query
  Parameters", bucket `examplebucket`, key `test.txt`, `20130524T000000Z`, `us-east-1`, 86,400 seconds). Its published
  signature checks the algorithm without the SDK.
- **SDK comparison:** extend `S3UrlBuilderAwsSdkTest` to presign with the SDK v1 SigV4 signer
  (`AWSS3V4SignerType`) for the same keys, bucket placements, session token, and response headers as the SigV2 cases.
  Compare host, encoded path, and every query parameter. The SDK client signs `us-east-1` on the global host
  `s3.amazonaws.com`, so the builder side uses `withEndpoint("s3.amazonaws.com", "us-east-1")`.
- **Behavior tests:** missing region throws for SigV4 and not for SigV2 or unsigned URLs; `withEndpoint(endpoint)`
  after `inRegion` clears the region; `withEndpoint(endpoint, region)` signs with that region; expirations at the
  signing time (`expireAt(now)` on a fixed clock, since `expireIn(Duration.ZERO)` already throws), exactly 7 days, and
  7 days plus 1 second; `toString()` twice gives fresh `X-Amz-Date` values with `expireIn`; `usingSignatureVersion2()`
  produces today's SigV2 output.
- Existing SigV2 tests switch to `usingSignatureVersion2()` and keep their expected output.

## Documentation

- JavaDoc on `S3UrlBuilder` (class, `inRegion`, both `withEndpoint` overloads, `usingSignatureVersion2`, `expireIn`,
  `expireAt`, `usingCredentials`) describes SigV4 as the default and the region and 7-day rules.
- README "Signed S3 URLs" section: SigV4 by default, the region requirement, the 7-day limit, and SigV2 as a deprecated
  opt-in.
- CHANGELOG: an upgrade note that signed URLs now use SigV4, need the bucket's region (DAM passes the app's region to
  `inRegion`, which breaks for a bucket in another region), and cannot last more than 7 days; entries under Added,
  Changed, and Deprecated.

## Left to the builder

- How the clock reaches `S3UrlBuilder` and `ExpireDateHolder`, as long as production code uses the system clock and no
  public method exposes it.
- How to pin the SDK's signing time in the comparison tests. The SDK v1 signer reads the current time; reading
  `X-Amz-Date` from its URL and fixing the builder's clock to that value is one option.
- Internal structure: method names, whether SigV4 code lives in a private nested class, and how SigV2 and SigV4 share
  the canonical path and query parameters.
- Exception messages.
