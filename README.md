# UrlBuilder

[![Build Status](https://github.com/Widen/urlbuilder/actions/workflows/ci.yml/badge.svg)](https://github.com/Widen/urlbuilder/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.widen/urlbuilder)](https://search.maven.org/artifact/com.widen/urlbuilder)

Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API. It strives simply to be more robust than manually constructing URLs by string concatenation.

Made with :heart: by Widen.

## Requirements

- Java 8 or higher

## Features

* Automatic slash management in paths. Slashes will be de-duped or added as necessary when using addPathSegment
* Automatic URL encoding for both path segments (preserving slashes) and query parameters
  * RFC 3986 compliant encoders:
    * [PathSegmentEncoder](/src/main/java/com/widen/urlbuilder/PathSegmentEncoder.java) for path segments (allows `@`, `:`, sub-delimiters unencoded)
    * [QueryParameterEncoder](/src/main/java/com/widen/urlbuilder/QueryParameterEncoder.java) for query parameters (stricter encoding)
  * Backward compatibility:
    * [LegacyEncoder](/src/main/java/com/widen/urlbuilder/LegacyEncoder.java) for v2.x-compatible encoding
    * Use `usingLegacyEncoding()` to enable v2.x behavior for both path and query encoding
  * Custom encoders supported via `usingPathEncoder(Encoder)` and `usingQueryEncoder(Encoder)`
  * [NoEncodingEncoder](/src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java) passes text through unchanged
* Options for generation of fully-qualified, hostname relative, or protocol relative URLs
* Fluent method-chaining API
* More examples in the [test suite](/src/test/java/com/widen/urlbuilder/):
  * [UrlBuilderTest](/src/test/java/com/widen/urlbuilder/UrlBuilderTest.java) - general usage
  * [UrlBuilderParsingTest](/src/test/java/com/widen/urlbuilder/UrlBuilderParsingTest.java) - URL parsing
  * [UrlBuilderPathTest](/src/test/java/com/widen/urlbuilder/UrlBuilderPathTest.java) - path handling
  * [UrlBuilderQueryParamsTest](/src/test/java/com/widen/urlbuilder/UrlBuilderQueryParamsTest.java) - query parameters
  * [UrlBuilderPortSslTest](/src/test/java/com/widen/urlbuilder/UrlBuilderPortSslTest.java) - port and SSL
  * [UrlBuilderModesTest](/src/test/java/com/widen/urlbuilder/UrlBuilderModesTest.java) - output modes
  * [UrlBuilderSigningTest](/src/test/java/com/widen/urlbuilder/UrlBuilderSigningTest.java) - URL signing

## Installation

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

## Usage

### Basic URL Construction

```java
new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString()
// produces: http://my.host.com/foo/bar?a=b
```

### Automatic URL Encoding

```java
new UrlBuilder("my.host.com", "foo & bar").addParameter("1&2", "3&4").addParameter("a", "b&c").toString()
// produces: http://my.host.com/foo%20%26%20bar?1%262=3%264&a=b%26c
```

### Parsing Existing URLs

```java
new UrlBuilder("https://example.com/path/to/resource?existing=param")
    .addParameter("new", "value")
    .toString()
// produces: https://example.com/path/to/resource?existing=param&new=value
```

### SSL/HTTPS

```java
new UrlBuilder("my.host.com", "secure/path").usingSsl().toString()
// produces: https://my.host.com/secure/path
```

### Output Modes

```java
UrlBuilder builder = new UrlBuilder("my.host.com", "foo");

// Fully qualified (default)
builder.modeFullyQualified().toString()
// produces: http://my.host.com/foo

// Protocol relative
builder.modeProtocolRelative().toString()
// produces: //my.host.com/foo

// Hostname relative
builder.modeHostnameRelative().toString()
// produces: /foo
```

### Converting to URI/URL

```java
UrlBuilder builder = new UrlBuilder("my.host.com", "path");

URI uri = builder.toURI();
URL url = builder.toURL();
```

## [S3](https://aws.amazon.com/s3/) Flavored UrlBuilder

[`S3UrlBuilder`](/src/main/java/com/widen/urlbuilder/S3UrlBuilder.java) provides specialized functionality for building S3 URLs.

### Features

* `expireIn` and `expireAt` for time-bombing S3 links
* All bucket reference methods supported:
  * [virtual bucket](http://docs.aws.amazon.com/AmazonS3/latest/dev/VirtualHosting.html) (`http://bucket.example.com/key.txt`)
  * [bucket in path](http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingBucket.html#access-bucket-intro) (`https://s3.amazonaws.com/bucket.example.com/key.txt`)
  * hostname (`http://bucket.example.com.s3.amazonaws.com/key.txt`)
* `withAttachmentFilename(String filename)` generates required `Content-Disposition` header for browser file download prompt

### Examples

```java
// Basic S3 URL with bucket in path
S3UrlBuilder.create("my-bucket", "path/to/file.txt")
    .inRegion("us-west-2")
    .usingBucketInPath()
    .toString()
// produces: http://s3.us-west-2.amazonaws.com/my-bucket/path/to/file.txt

// Signed S3 URL with expiration
S3UrlBuilder.create("my-bucket", "private/file.txt")
    .inRegion("us-east-1")
    .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
    .expireIn(1, TimeUnit.HOURS)
    .usingSsl()
    .toString()

// Virtual bucket hosting
S3UrlBuilder.create("my-bucket", "file.txt")
    .usingBucketVirtualHost()
    .toString()
// produces: http://my-bucket/file.txt

// Force download with custom filename
S3UrlBuilder.create("my-bucket", "documents/report-2024-q1.pdf")
    .inRegion("us-east-1")
    .withAttachmentFilename("Quarterly Report.pdf")
    .toString()
```

## [CloudFront](https://aws.amazon.com/cloudfront/) Flavored UrlBuilder

[`CloudfrontUrlBuilder`](/src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java) provides specialized functionality for building CloudFront URLs.

### Features

* `expireIn` and `expireAt` for time-bombing CloudFront links
* Signed URL support with private key authentication

### Examples

```java
PrivateKey privateKey = CloudfrontPrivateKeyUtils.loadPrivateKey(pemFileInputStream);

// Signed CloudFront URL with expiration
new CloudfrontUrlBuilder("d1234.cloudfront.net", "videos/movie.mp4", "APKAEIBAERJR2EXAMPLE", privateKey)
    .expireIn(2, TimeUnit.HOURS)
    .toString()

// CloudFront URL with attachment filename
new CloudfrontUrlBuilder("d1234.cloudfront.net", "files/document.pdf", "APKAEIBAERJR2EXAMPLE", privateKey)
    .expireAt(new Date(System.currentTimeMillis() + 86400000))
    .withAttachmentFilename("download.pdf")
    .toString()
```

## URL Signing

UrlBuilder supports signing URLs during generation using the `UrlSigner` interface. This is useful for implementing HMAC signatures, RSA signing (like CloudFront), or other URL signing schemes.

### Basic Usage

Sign URLs using a lambda function:

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collections;

String SECRET_KEY = "my-secret-key";

UrlBuilder builder = new UrlBuilder("cdn.example.com", "/videos/movie.mp4");
builder.addParameter("user", "john");
builder.usingUrlSigner(context -> {
    // Sign the complete unsigned URL
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256"));
    byte[] hash = mac.doFinal(context.getUrl().getBytes());
    String signature = Base64.getEncoder().encodeToString(hash);
    
    return Collections.singletonMap("signature", signature);
});
String signedUrl = builder.toString();

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
}

// Usage
HmacUrlSigner signer = new HmacUrlSigner("my-secret");
UrlBuilder builder = new UrlBuilder("cdn.example.com", "/media/video.mp4");
builder.usingUrlSigner(signer);
String signedUrl = builder.toString();
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
UrlBuilder builder = new UrlBuilder("api.example.com", "/v1/data");
builder.usingUrlSigner(signer);
String signedUrl = builder.toString();
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

### Best Practices

1. **Keep signers stateless** - Signers should be thread-safe
2. **Sign the complete URL** - Use `context.getUrl()` in most cases
3. **Don't include fragments** - Fragments are client-side only
4. **Use base64/hex encoding** - Signature values are not URL-encoded by default
5. **Add expiration** - Prevent signature reuse with expiration timestamps

See [`HmacSigningExampleTest`](/src/test/java/com/widen/urlbuilder/examples/HmacSigningExampleTest.java) for more examples.

## License

Licensed under the Apache Version 2.0 license. See [the license file](LICENSE.md) for details.
