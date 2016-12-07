# UrlBuilder
Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API. It strives simply to be more robust then manually constructing URLs by string concatenation.

    new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b=c").toString()

produces `http://my.host.com/foo/bar?a=b`

    new UrlBuilder("my.host.com", "foo & bar").addParameter("1&2", "3&4").addParameter("a", "b&c").toString()
    
produces `http://my.host.com/foo%20%26%20bar?1%262=3%264&a=b%26c`


## Features
* Automatic slash management in paths. Slashes will be de-duped or added as necessary when using addPathSegment
* Automatic URL encoding for both path segments (preserving slashes) and query parameters
  * Encoder is user replaceable; two implementations are provided: [BuiltinEncoder](/src/com/widen/util/BuiltinEncoder.java) uses `java.net.UrlEncoder`, [NoEncodingEncoder](/src/com/widen/util/NoEncodingEncoder.java) uses text as-is
  * '+'s in URL encoded values are replaced with '%20's
* Options for generation of fully-qualified, hostname relative, or protocol relative URLs
* Fluent method-chaining API
* More examples in [UrlBuilderTest](/test/com/widen/util/UrlBuilderTest.java)

## [S3](https://aws.amazon.com/s3/) Flavored UrlBuilder
* `[S3UrlBuilder](/src/com/widen/util/S3UrlBuilder.java)` provides specialized functionality building S3 URLs
* `expireIn` and `expireAt` for time-bombing S3 links
* All hostname methods supported: virtual bucket (`http://bucket.example.com/key.txt`), bucket in path (`https://s3.amazonaws.com/bucket.example.com/key.txt`), or hostname (`http://bucket.example.com.s3.amazonaws.com/key.txt`) 
* `withAttachmentFilename(String filename)` generates required `Content-Disposition` header for browser file download prompt

## [Cloudfront](https://aws.amazon.com/cloudfront/) Flavored UrlBuilder
* `[CloudfrontUrlBuilder](/src/com/widen/util/CloudfrontUrlBuilder.java)` provides specialized functionality for building CloudFront URLs
* `expireIn` and `expireAt` for time-bombing CloudFront links
* `withAttachmentFilename(String filename)` generates required `Content-Disposition` header for browser file download prompt

## License
Licensed under Apache, Version 2.0. See [the license file](LICENSE.md) for details.
