# UrlBuilder

[![Build Status](https://github.com/Widen/urlbuilder/actions/workflows/ci.yml/badge.svg)](https://github.com/Widen/urlbuilder/actions/workflows/ci.yml)
![Maven Central](https://img.shields.io/maven-central/v/com.widen/urlbuilder)

Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API. It strives simply to be more robust than manually constructing URLs by string concatenation.

Made with :heart: by Widen.

## Features

* Automatic slash management in paths. Slashes will be de-duped or added as necessary when using addPathSegment
* Automatic URL encoding for both path segments (preserving slashes) and query parameters
  * Encoder is user replaceable; two implementations are provided:
    * Default [BuiltinEncoder](/src/main/java/com/widen/urlbuilder/BuiltinEncoder.java) uses `java.net.UrlEncoder`
    * [NoEncodingEncoder](/src/main/java/com/widen/urlbuilder/NoEncodingEncoder.java) uses text as-is
    * Use `usingEncoder(Encoder encoder)` to set default; `addParameter(String key, Object value, Encoder encoder)` can be used to override Encoder for a single parameter
* Options for generation of fully-qualified, hostname relative, or protocol relative URLs
* Fluent method-chaining API
* More examples in [UrlBuilderTest](/src/test/java/com/widen/urlbuilder/UrlBuilderTest.java)

## Installation

With Gradle:

```
compile 'com.widen:urlbuilder:{version}'
```

Other dependency managers should be similar.

## Usage

```java
new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString()
```

produces `http://my.host.com/foo/bar?a=b`

```java
new UrlBuilder("my.host.com", "foo & bar").addParameter("1&2", "3&4").addParameter("a", "b&c").toString()
```

produces `http://my.host.com/foo%20%26%20bar?1%262=3%264&a=b%26c`

## [S3](https://aws.amazon.com/s3/) Flavored UrlBuilder

* [`S3UrlBuilder`](/src/main/java/com/widen/urlbuilder/S3UrlBuilder.java) provides specialized functionality building for S3 URLs
* `expireIn` and `expireAt` for time-bombing S3 links
* All bucket reference methods supported:
  * [virtual bucket](http://docs.aws.amazon.com/AmazonS3/latest/dev/VirtualHosting.html) (`http://bucket.example.com/key.txt`)
  * [bucket in path](http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingBucket.html#access-bucket-intro) (`https://s3.amazonaws.com/bucket.example.com/key.txt`)
  * hostname (`http://bucket.example.com.s3.amazonaws.com/key.txt`)
* `withAttachmentFilename(String filename)` generates required `Content-Disposition` header for browser file download prompt

## [Cloudfront](https://aws.amazon.com/cloudfront/) Flavored UrlBuilder
* [`CloudfrontUrlBuilder`](/src/main/java/com/widen/urlbuilder/CloudfrontUrlBuilder.java) provides specialized functionality for building CloudFront URLs
* `expireIn` and `expireAt` for time-bombing CloudFront links
* `withAttachmentFilename(String filename)` generates required `Content-Disposition` header for browser file download prompt

## License

Licensed under the Apache Version 2.0 license. See [the license file](LICENSE.md) for details.
