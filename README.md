# UrlBuilder

[![Build Status](https://github.com/Widen/urlbuilder/actions/workflows/ci.yml/badge.svg)](https://github.com/Widen/urlbuilder/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.widen/urlbuilder)](https://central.sonatype.com/artifact/com.widen/urlbuilder)

UrlBuilder builds URLs from their parts. Give it a host, path segments, and query parameters, and it places the slashes,
adds the `?` and `&` separators, and percent-encodes each value. It is a small Java library with a fluent, chainable
API.

Made with :heart: by Widen.

## Why not concatenate strings?

Building a URL by hand looks simple. It breaks as soon as a value differs from what you expected:

```java
String host = "files.example.com";
String folder = "/reports/2024/";
String fileName = "Q3 summary #2.pdf";
String search = "cats & dogs";
int page = 2;

String url = "https://" + host + "/" + folder + "/" + fileName + "?q=" + search + "&page=" + page;
// https://files.example.com//reports/2024//Q3 summary #2.pdf?q=cats & dogs&page=2
```

That URL has four bugs:

* The slashes double up, because `folder` already starts and ends with one.
* The spaces stay unencoded.
* The `#` starts a fragment, so the server never receives `2.pdf` or the query string.
* The `&` in the search text ends the `q` parameter early.

UrlBuilder takes the same values and gets all four right:

```java
String url = new UrlBuilder(host, folder)
    .usingSsl()
    .addPathSegment(fileName)
    .addParameter("q", search)
    .addParameter("page", page)
    .toString();
// https://files.example.com/reports/2024/Q3%20summary%20%232.pdf?q=cats%20%26%20dogs&page=2
```

### What about `java.net.URI` and `URL`?

The JDK classes parse and validate a URL, but they do little to build one. The obvious attempt still gets this URL
wrong:

```java
new URI("https", host, "/" + folder + "/" + fileName, "q=" + search + "&page=" + page, null).toString()
// https://files.example.com//reports/2024//Q3%20summary%20%232.pdf?q=cats%20&%20dogs&page=2
```

The path is encoded now, but the slashes still double up, and the `&` in the search text still ends the `q` parameter. A
correct version that uses only the JDK looks like this:

```java
// Nothing in the JDK joins path segments, so fix the slashes yourself
String path = ("/" + folder + "/" + fileName).replaceAll("/+", "/");

// URLEncoder writes a space as "+", which is correct only for form data
String query = "q=" + URLEncoder.encode(search, "UTF-8").replace("+", "%20") + "&page=" + page;

// The URI constructor would encode the "%" signs in the query again, so add the query afterward
String url = new URI("https", host, path, null).toASCIIString() + "?" + query;
// https://files.example.com/reports/2024/Q3%20summary%20%232.pdf?q=cats%20%26%20dogs&page=2
```

The example works around the first five of these problems:

* **No JDK class joins path segments.** You place and trim the slashes yourself.
* **The multi-argument `URI` constructors leave `&`, `=`, and `+` in the query unencoded.** Those characters are legal
  in a query string, and the constructor cannot tell a separator from part of a value. You encode every name and value
  yourself.
* **Those constructors always encode `%`.** They encode the `%` in a value you already encoded, so `%26` becomes
  `%2526`. That is why the example adds the query afterward.
* **`URLEncoder` implements HTML form encoding, not URL encoding.** It writes a space as `+`, and a path reads `+` as a
  literal plus sign.
* **`URI.toString()` leaves characters outside ASCII unencoded.** `toASCIIString()` encodes them.
* **`URL` encodes nothing and validates little.** `new URL("https://files.example.com/a b")` succeeds and keeps the
  space. Its `equals` and `hashCode` look up the host name on the network. Its constructors have been deprecated since
  Java 20.
* **Neither class can change a URL.** Both are immutable and hold the query as one string, so adding or removing a
  parameter means taking that string apart yourself.
* **The constructors and `URLEncoder` throw checked exceptions.** `new URI(...)`, `new URL(...)`, and
  `URLEncoder.encode(String, String)` each declare one.

UrlBuilder does that work for you and returns a `URI` or `URL` when an API needs one. See
[Getting a `URI` or `URL`](#getting-a-uri-or-url).

## Installation

UrlBuilder requires Java 8 or later. If you are moving from version 2, read the upgrade notes in the
[changelog](CHANGELOG.md) first.

### Gradle

```kotlin
implementation("com.widen:urlbuilder:{version}")
```

### Maven

```xml
<dependency>
    <groupId>com.widen</groupId>
    <artifactId>urlbuilder</artifactId>
    <version>{version}</version>
</dependency>
```

## Building a URL

### Host, path, and parameters

Start with a host and a path, then add to them. The methods that add to the URL return the builder, so the calls chain.
`toString()` writes the URL.

```java
new UrlBuilder("my.host.com", "foo")
    .addPathSegment("bar")
    .addParameter("a", "b")
    .toString()
// http://my.host.com/foo/bar?a=b
```

### Slashes

You never add or trim a slash. The builder joins segments with exactly one slash, whatever the input contains.

```java
new UrlBuilder("my.host.com", "/reports/")
    .addPathSegment("/2024/")
    .addPathSegment("summary.pdf")
    .toString()
// http://my.host.com/reports/2024/summary.pdf
```

A value that contains slashes adds several segments. `addPrefixedPathSegment` adds to the front of the path, and
`includeTrailingSlash()` ends the path with a slash.

```java
new UrlBuilder("my.host.com", "users/42")
    .addPrefixedPathSegment("api/v2")
    .includeTrailingSlash()
    .toString()
// http://my.host.com/api/v2/users/42/
```

### Encoding

Pass plain, unencoded text. The builder encodes each path segment and each parameter name and value when it writes the
URL, including characters outside ASCII.

```java
new UrlBuilder("my.host.com", "foo & bar")
    .addParameter("1&2", "3&4")
    .addParameter("a", "b&c")
    .toString()
// http://my.host.com/foo%20&%20bar?1%262=3%264&a=b%26c

new UrlBuilder("my.host.com", "café")
    .addParameter("q", "naïve 😀")
    .toString()
// http://my.host.com/caf%C3%A9?q=na%C3%AFve%20%F0%9F%98%80
```

The path and the query string follow different rules. An `&` is legal in a path, so it stays as it is. In a parameter it
would end the value, so it becomes `%26`. [Controlling encoding](#controlling-encoding) describes the rules and how to
change them.

### Query parameters

`addParameter` accepts any object and uses its `toString()`. You can add several parameters from a map, and you can
repeat a name.

```java
Map<String, Object> filters = new LinkedHashMap<>();
filters.put("status", "open");
filters.put("limit", 25);

new UrlBuilder("my.host.com", "issues")
    .addParameters(filters)
    .addParameter("tag", "bug")
    .addParameter("tag", "ui")
    .toString()
// http://my.host.com/issues?status=open&limit=25&tag=bug&tag=ui
```

A null value adds the name alone, and a blank name adds nothing.

```java
new UrlBuilder("my.host.com", "issues")
    .addParameter("archived", null)
    .toString()
// http://my.host.com/issues?archived
```

`clearParameter("name")` removes a parameter, and `clearParameters()` removes them all.

### HTTPS and ports

```java
new UrlBuilder("my.host.com", "secure/path").usingSsl().toString()
// https://my.host.com/secure/path

new UrlBuilder("localhost", 8080, "api/health").toString()
// http://localhost:8080/api/health

new UrlBuilder("https://localhost:8443/app").toString()
// https://localhost:8443/app
```

The builder leaves the default port of the scheme out of the URL: 80 for `http` and 443 for `https`. Port 443 turns
HTTPS on and port 80 turns it off, so the port wins when it conflicts with the scheme.

```java
new UrlBuilder("https://localhost:80/app").toString()
// http://localhost/app

new UrlBuilder("http://localhost:443/app").toString()
// https://localhost/app
```

A later `usingSsl(boolean)` call changes the scheme and keeps the port.

```java
new UrlBuilder("localhost", 80, "app").usingSsl().toString()
// https://localhost:80/app
```

### Fragments

```java
new UrlBuilder("my.host.com", "docs/guide").withFragment("install").toString()
// http://my.host.com/docs/guide#install
```

The builder writes the fragment as given, without encoding it.

## Changing an existing URL

Pass a complete URL to the constructor to take it apart. Then change it and write it back out.

```java
UrlBuilder builder = new UrlBuilder("https://example.com/path/to/resource?existing=param&page=1");

builder.addParameter("new", "value").clearParameter("page").toString()
// https://example.com/path/to/resource?existing=param&new=value
```

The builder also returns the parts.

```java
builder.getHostname()         // example.com
builder.getPath()             // /path/to/resource
builder.getPathSegments()     // [path, to, resource]
builder.getQueryParameters()  // {existing=[param], new=[value]}
builder.isSslEnabled()        // true
```

The constructor decodes percent-encoded text in the path and the query string, so the getters return plain text. It
throws `UrlBuilder.NonParsableUrl` for a string that `java.net.URL` cannot parse.

## Relative URLs

The builder writes a fully qualified URL by default. It can also leave out the scheme, or both the scheme and the host.

```java
UrlBuilder builder = new UrlBuilder("my.host.com", "foo");

builder.modeFullyQualified().toString()
// http://my.host.com/foo

builder.modeProtocolRelative().toString()
// //my.host.com/foo

builder.modeHostnameRelative().toString()
// /foo
```

A builder made with the no-argument constructor starts in hostname-relative mode.

```java
new UrlBuilder().withPath("search").addParameter("q", "shoes").toString()
// /search?q=shoes
```

## Getting a `URI` or `URL`

```java
UrlBuilder builder = new UrlBuilder("my.host.com", "path");

URI uri = builder.toURI();
URL url = builder.toURL();
```

## Controlling encoding

The default encoders follow [RFC 3986](https://www.rfc-editor.org/rfc/rfc3986):

* [`PathSegmentEncoder`](/src/main/java/com/widen/urlbuilder/PathSegmentEncoder.java) encodes path segments. It leaves
  `@`, `:`, and the sub-delimiters `!$&'()*+,;=` as they are.
* [`QueryParameterEncoder`](/src/main/java/com/widen/urlbuilder/QueryParameterEncoder.java) encodes parameter names and
  values. It encodes everything except letters, digits, and `-._~`.

You can change the encoding in three ways:

* `usingPathEncoder(Encoder)` and `usingQueryEncoder(Encoder)` set an encoder of your own.
* `addParameter(key, value, encoder)` sets the encoder for one parameter.
  [`NoEncodingEncoder`](/src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java) passes text through unchanged, which
  suits a value that is already encoded.
* `usingLegacyEncoding()` restores the encoding of version 2.x through
  [`LegacyEncoder`](/src/main/java/com/widen/urlbuilder/LegacyEncoder.java).

```java
new UrlBuilder("my.host.com", "login")
    .addParameter("next", "%2Fhome%3Ftab%3D1", NoEncodingEncoder.INSTANCE)
    .toString()
// http://my.host.com/login?next=%2Fhome%3Ftab%3D1
```

You can set the path and query encoders at any point before `toString()`. They apply to the whole URL, including a URL
the builder parsed. A parameter added with its own encoder keeps it.

## Signed S3 URLs

[`S3UrlBuilder`](/src/main/java/com/widen/urlbuilder/S3UrlBuilder.java) builds [S3](https://aws.amazon.com/s3/) URLs and
signs them, so you can share a private object through a link that expires.

```java
// Unsigned URL of a public object
new S3UrlBuilder("my-bucket", "path/to/file.txt")
    .inRegion("us-west-2")
    .toString()
// http://my-bucket.s3.us-west-2.amazonaws.com/path/to/file.txt

// Signed URL that works for one hour
new S3UrlBuilder("my-bucket", "private/file.txt")
    .inRegion("us-east-1")
    .usingCredentials(accessKey, secretKey)
    .expireIn(Duration.ofHours(1))
    .usingSsl()
    .toString()
// https://my-bucket.s3.us-east-1.amazonaws.com/private/file.txt?Signature=...&AWSAccessKeyId=...&Expires=...

// Signed URL that downloads the object under another file name
new S3UrlBuilder("my-bucket", "documents/report-2024-q1.pdf")
    .inRegion("us-east-1")
    .usingCredentials(accessKey, secretKey)
    .expireIn(Duration.ofMinutes(30))
    .withAttachmentFilename("Quarterly Report.pdf")
    .toString()
```

The details:

* **Key:** the builder takes the key as literal text and encodes every character except letters, digits, `-._~`, and
  `/`, as the AWS SDKs do. So `a%20b.txt` names that object and not `a b.txt`, and S3 does not read the `+` in
  `a+b.txt` as a space. The builder drops leading slashes and keeps every other slash, because `folder/` and `a//b` are
  keys of their own. After the first slash of a run it writes `%2F`, as the AWS SDKs do, so `a//b` becomes `a/%2Fb`
  and no HTTP client or proxy can merge the slashes. `CloudfrontUrlBuilder` encodes its key the same way, for an S3
  origin, and keeps the slashes as given.
* **Expiration:** `expireIn(Duration)` counts from the moment the URL is generated, and `expireAt(Instant)` sets a fixed
  time. The later call replaces the earlier one.
* **Credentials:** a signed URL needs an expiration and `usingCredentials`, which also accepts an STS session token. The
  builder signs with AWS Signature Version 2.
* **Response headers:** `withAttachmentFilename` sets the `Content-Disposition` header that makes a browser download the
  file, and it requires a signed URL. `withContentType` sets the `Content-Type` header.
* **Endpoint:** `inRegion("us-west-2")` selects a regional endpoint, and `withEndpoint` names any S3-compatible service.
* **Bucket placement:** the bucket goes in the hostname by default. The table lists all three placements.

| Method | Result |
| --- | --- |
| `usingBucketInHostname()` (default) | `http://my-bucket.s3.amazonaws.com/key.txt` |
| `usingBucketVirtualHost()`, for a [bucket served from its own domain](https://docs.aws.amazon.com/AmazonS3/latest/userguide/VirtualHosting.html#VirtualHostingCustomURLs) | `http://assets.example.com/key.txt` |
| `usingBucketInPath()`, for a [path-style request](https://docs.aws.amazon.com/AmazonS3/latest/userguide/VirtualHosting.html#path-style-access) | `http://s3.amazonaws.com/my-bucket/key.txt` |

## Signed CloudFront URLs

[`CloudfrontUrlBuilder`](/src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java) builds a
[CloudFront](https://aws.amazon.com/cloudfront/) signed URL with a canned policy, which gives temporary access to a
private distribution. It needs the ID of a CloudFront key pair and the matching RSA private key.

```java
PrivateKey privateKey = CloudfrontPrivateKeyUtils.fromPemString(pemString);

// Signed URL that works for two hours
new CloudfrontUrlBuilder("d1234.cloudfront.net", "videos/movie.mp4", "APKAEIBAERJR2EXAMPLE", privateKey)
    .expireIn(Duration.ofHours(2))
    .toString()

// Signed URL that downloads the file under another name
new CloudfrontUrlBuilder("d1234.cloudfront.net", "files/document.pdf", "APKAEIBAERJR2EXAMPLE", privateKey)
    .expireAt(Instant.now().plus(Duration.ofDays(1)))
    .withAttachmentFilename("download.pdf")
    .toString()

// Signed URL that expires at an exact time in a time zone
ZonedDateTime endOfSale = LocalDateTime.of(2030, 6, 30, 17, 0).atZone(ZoneId.of("America/Chicago"));
new CloudfrontUrlBuilder("d1234.cloudfront.net", "files/document.pdf", "APKAEIBAERJR2EXAMPLE", privateKey)
    .expireAt(endOfSale.toInstant())
    .toString()
```

The details:

* **Expiration:** a CloudFront URL always needs one, and `toString()` throws without it. `expireIn` and `expireAt` work
  as they do for S3.
* **Private key:** `fromPemString` reads a PKCS#1 or PKCS#8 key in PEM form, and `fromDerBinary` reads a PKCS#8 key in
  DER form from an `InputStream`.
* **Other settings:** `withSsl()` selects HTTPS, and `addParameter` adds a query parameter that the signature covers.

## Signing URLs yourself

[`UrlSigner`](/src/main/java/com/widen/urlbuilder/UrlSigner.java) adds your own signature scheme, such as an HMAC over
the URL, to any `UrlBuilder`. When the builder writes the URL, it calls the signer with a `SigningContext`. The signer
signs `context.getStringToSign()`, which is the complete URL with every query parameter, and returns the signature as a
map of parameter names to values. The builder appends each entry to the URL. A value can be any object: the builder
writes its `toString()` and encodes it like any other parameter.

### A signer in one lambda

```java
String SECRET_KEY = "my-secret-key";

UrlBuilder builder = new UrlBuilder("cdn.example.com", "/videos/movie.mp4");
builder.addParameter("user", "john");
builder.usingUrlSigner(context -> {
    // NOTE: use a strong hash like HmacSHA256 in production
    String signature = Integer.toHexString((context.getStringToSign() + SECRET_KEY).hashCode());
    return Collections.singletonMap("signature", signature);
});
String signedUrl = builder.toString();
// http://cdn.example.com/videos/movie.mp4?user=john&signature=abc123...
```

### A reusable signer

A class lets several builders share one signer. In these examples, `hmacSha256` stands for a helper of your own that
returns the HMAC as text.
[`HmacSigningExampleTest`](/src/test/java/com/widen/urlbuilder/examples/HmacSigningExampleTest.java) has a working
version of that helper.

```java
public class HmacUrlSigner implements UrlSigner {
    private final String secretKey;

    public HmacUrlSigner(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public Map<String, String> sign(SigningContext context) {
        String signature = hmacSha256(context.getStringToSign(), secretKey);
        return Collections.singletonMap("signature", signature);
    }
}

HmacUrlSigner signer = new HmacUrlSigner("my-secret");
UrlBuilder builder = new UrlBuilder("cdn.example.com", "/media/video.mp4");
builder.usingUrlSigner(signer);
String signedUrl = builder.toString();
```

### Choices to make

A signing scheme is yours to design. These choices matter most.

* **What the signature covers.** Sign the complete URL, including every query parameter. That is the convention, and it
  stops anyone from changing any part of the link. Sign less only for a reason, such as omitting the host so that one
  link works on several domains. A narrower scope leaves more of the link open to change: a signature over the path
  alone lets anyone change the parameters. The context returns each scope in one call.

  | Scope | Text to sign | Example |
  | --- | --- | --- |
  | Whole URL | `context.getStringToSign()` | `https://cdn.example.com/a/b.mp4?user=john&expires=1795127806` |
  | Path and parameters | `context.getEncodedPathAndQuery()` | `/a/b.mp4?user=john&expires=1795127806` |
  | Path only | `context.getEncodedPath()` | `/a/b.mp4` |

  The whole URL is the URL as the builder writes it. A protocol-relative URL has no scheme to sign, and a
  hostname-relative URL has no scheme, host, or port, so its signature stays valid on any host. Use a fully qualified
  URL when the signature must cover the host.
* **Where extra values go.** An expiration, a key ID, or a user ID must be in the URL, so the verifier can read it, and
  covered by the signature, so nobody can change it. Add each one with `context.addParameter` before you read the text
  to sign; once you have read it, `addParameter` throws `IllegalStateException`. The signature does not cover a
  parameter that `sign` returns, because the builder appends it after signing. Return only the signature.
* **Whether the link expires.** A link with no expiration works for as long as the key does, for anyone who obtains it.
  Add an expiration unless the link must be permanent.
* **A shared secret or a key pair.** An HMAC uses one secret on both sides, so every service that can verify a link can
  also create one. A signature made with a private key, such as RSA or ECDSA, is verified with the public key, so a
  verifier cannot create links. CloudFront signed URLs work that way.
* **How to replace a key.** Plan for it before a key leaks. Add a signed key ID parameter that tells the verifier which
  key to use, so the old key and the new key both work during the change.
* **What the verifier rebuilds.** The verifier must reproduce the signed text byte for byte. The simplest rule is "the
  URL as it arrived, without the signature parameter". Anything between the signer and the verifier that reorders or
  re-encodes query parameters breaks that rule, so check what your proxies and CDN do to a URL.

### Expiring URLs

To make a signed URL expire, add the expiration to the URL before you sign it. The signature then covers the expiration,
and the verifier can read it.

```java
public class ExpiringHmacSigner implements UrlSigner {
    private final String secretKey;
    private final Duration validFor;

    public ExpiringHmacSigner(String secretKey, Duration validFor) {
        this.secretKey = secretKey;
        this.validFor = validFor;
    }

    @Override
    public Map<String, ?> sign(SigningContext context) {
        // Added before signing, so the signature covers the expiration
        context.addParameter("expires", Instant.now().plus(validFor).getEpochSecond());

        String signature = hmacSha256(context.getStringToSign(), secretKey);
        return Collections.singletonMap("signature", signature);
    }
}

// A URL that expires in one hour
ExpiringHmacSigner signer = new ExpiringHmacSigner("my-secret", Duration.ofHours(1));
UrlBuilder builder = new UrlBuilder("api.example.com", "/v1/data");
builder.usingUrlSigner(signer);
String signedUrl = builder.toString();
// http://api.example.com/v1/data?expires=1795127806&signature=...
```

### Verifying an expiring URL

The service that receives a signed URL finds the text the signer signed, checks the signature, and then checks the
expiration. Because the signer signed the whole URL, the signed text is the URL without its `signature` parameter.

The verifier takes that text from the URL as it arrived. Parsing the URL and writing it again can change it, for example
by dropping an empty path segment, and the verifier would then accept a URL that differs from the signed one. The signer
writes its `expires` parameter and then its `signature` parameter last, so the verifier accepts only a URL that ends
with both. It reads the expiration from that place, so an `expires` parameter that the caller put earlier in the URL
changes nothing.

```java
public class ExpiringHmacVerifier {
    // The signer writes its expiration and then its signature last, so a URL it signed ends with both
    private static final Pattern SIGNED_URL =
        Pattern.compile("(?<signedText>.*[?&]expires=(?<expires>\\d+))&signature=(?<signature>[^&#]*)");

    private final String secretKey;

    public ExpiringHmacVerifier(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isValid(String signedUrl) {
        Matcher url = SIGNED_URL.matcher(signedUrl);
        if (!url.matches()) {
            return false;
        }

        // The signer signed everything before the signature parameter. Take that text from the URL as it
        // arrived: parsing the URL and writing it again can change it.
        // Check the signature first: until it matches, the expiration is untrusted input.
        String expected = hmacSha256(url.group("signedText"), secretKey);
        // MessageDigest.isEqual compares in constant time, so the comparison leaks nothing about the signature
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                url.group("signature").getBytes(StandardCharsets.UTF_8))) {
            return false;
        }

        Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(url.group("expires")));
        return Instant.now().isBefore(expiresAt);
    }
}

ExpiringHmacVerifier verifier = new ExpiringHmacVerifier("my-secret");
boolean valid = verifier.isValid(signedUrl);
```

The verifier compares the signature as it appears in the URL, so `hmacSha256` must return text that the builder writes
without encoding, such as hexadecimal or URL-safe Base64.

### What the signer receives

The `SigningContext` describes the URL before signing:

* `getStringToSign()` returns the text to sign: the complete unsigned URL, including the parameters you add through the
  context.
* `addParameter(name, value)` adds a query parameter to the URL before signing, so the signature covers it.
* `addParameters(map)` adds several at once, in the map's order.
* `getUrl()` returns the same URL as `getStringToSign()`, for a signer that needs the URL for another purpose, such as
  the resource in a CloudFront policy.
* `getProtocol()` returns `http` or `https`, or an empty string for a relative URL.
* `getHostname()` returns the host.
* `getPort()` returns the port, or `null` when the port is unset or is the default for the scheme: 80 for `http` and 443
  for `https`.
* `getEncodedPath()` returns the encoded path.
* `getEncodedQuery()` returns the encoded query string.
* `getEncodedPathAndQuery()` returns the encoded path and query string together, without the scheme or host.
* `getParameters()` returns the unencoded query parameters.
* `getFragment()` returns the fragment, which is not part of the text to sign.
* `isSsl()` returns whether the URL uses HTTPS.
* `getGenerationMode()` returns the generation mode: fully qualified, protocol relative, or hostname relative.

### Guidelines for signers

1. **Keep signers stateless.** A signer shared between builders must be safe to call from several threads.
2. **Leave the fragment out.** A browser never sends it to the server.
3. **Pass unencoded values.** The builder encodes the parameters a signer adds or returns, so it encodes a
   percent-encoded value twice.

## More examples

The [tests](/src/test/java/com/widen/urlbuilder/) show each feature in use:

* [UrlBuilderTest](/src/test/java/com/widen/urlbuilder/UrlBuilderTest.java) covers general usage.
* [UrlBuilderPathTest](/src/test/java/com/widen/urlbuilder/UrlBuilderPathTest.java) covers path handling.
* [UrlBuilderQueryParamsTest](/src/test/java/com/widen/urlbuilder/UrlBuilderQueryParamsTest.java) covers query
  parameters.
* [UrlBuilderParsingTest](/src/test/java/com/widen/urlbuilder/UrlBuilderParsingTest.java) covers parsing a URL.
* [UrlBuilderPortSslTest](/src/test/java/com/widen/urlbuilder/UrlBuilderPortSslTest.java) covers ports and HTTPS.
* [UrlBuilderModesTest](/src/test/java/com/widen/urlbuilder/UrlBuilderModesTest.java) covers relative URLs.
* [UrlBuilderSigningTest](/src/test/java/com/widen/urlbuilder/UrlBuilderSigningTest.java) covers signing.

## License

Licensed under the Apache Version 2.0 license. See [the license file](LICENSE.md) for details.
