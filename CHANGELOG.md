# Changelog

This file lists the changes in each release that affect code using the library. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Releases before 3.0.0 are listed in the
[Git tags](https://github.com/Widen/urlbuilder/tags).

## 3.0.0 - Unreleased

Version 3 encodes URLs by the rules of RFC 3986, adds a way to sign URLs, signs S3 URLs with Signature Version 4, and
fixes several bugs in the S3 and CloudFront builders. Some URLs differ from the ones version 2 produced, so read the
upgrade notes first.

### Upgrading from 2.x

1. **Pass the bucket's region to every signed S3 URL.** `S3UrlBuilder` now signs with AWS Signature Version 4 (SigV4) by
   default, because AWS is retiring Signature Version 2 (SigV2) for S3. A signed URL needs the bucket's region, from
   `inRegion(region)` or the new `withEndpoint(endpoint, region)`; without one, `toString()` throws
   `IllegalStateException`. `withEndpoint(endpoint)` clears any region that `inRegion` set. The region must be the
   bucket's own: code that passes the application's region to `inRegion` signs for the wrong region when the bucket
   lives in another one, and S3 answers the request with a 400. A SigV4 URL lasts from 1 second to 7 days, and
   `toString()` throws `IllegalArgumentException` for an expiration outside that range. The URL carries
   `X-Amz-Algorithm`, `X-Amz-Credential`, `X-Amz-Date`, `X-Amz-Expires`, `X-Amz-SignedHeaders`, `X-Amz-Signature`, and
   with a session token `X-Amz-Security-Token`, in place of `Signature`, `Expires`, `AWSAccessKeyId`, and
   `x-amz-security-token`. To keep SigV2 for a bucket or S3-compatible service that still accepts it, call the
   deprecated `usingSignatureVersion2()`.
2. **Check the URLs you generate.** The default encoding changed. A path segment now keeps `@`, `:`, and `!$&'()*+,;=`,
   and a query parameter now encodes `*` and keeps `~`.

   ```java
   new UrlBuilder("my.host.com", "a b+c@d").addParameter("q", "x y*z~").toString()
   // 2.x: http://my.host.com/a%20b%2Bc%40d?q=x%20y*z%7E
   // 3.0: http://my.host.com/a%20b+c@d?q=x%20y%2Az~
   ```

   Both forms decode to the same text. To get the 2.x output back, call `usingLegacyEncoding()` on the builder.

   ```java
   new UrlBuilder("my.host.com", "a b+c@d")
       .usingLegacyEncoding()
       .addParameter("q", "x y*z~")
       .toString()
   // http://my.host.com/a%20b%2Bc%40d?q=x%20y*z%7E
   ```

   To switch back only the path or only the query string, pass a `LegacyEncoder` to `usingPathEncoder` or
   `usingQueryEncoder`.

   ```java
   builder.usingPathEncoder(new LegacyEncoder());   // 2.x path, 3.0 query string
   builder.usingQueryEncoder(new LegacyEncoder());  // 3.0 path, 2.x query string
   ```

   The switch applies to the whole URL whenever you call it, including a URL that `new UrlBuilder(String)` parsed.

   ```java
   new UrlBuilder("http://my.host.com/a%20b%2Bc%40d?q=x%20y*z%7E").usingLegacyEncoding().toString()
   // http://my.host.com/a%20b%2Bc%40d?q=x%20y*z%7E
   ```

   It has two limits:

   - A parameter added with its own encoder, through `addParameter(key, value, encoder)`, keeps that encoder.
   - `S3UrlBuilder` and `CloudfrontUrlBuilder` always use the 3.0 encoding. Their keys encode every character except
     letters, digits, `-._~`, and `/`, as the AWS SDKs do, so they differ from 2.x only in writing `*` as `%2A` and
     keeping `~`.
3. **Replace `usingEncoder(Encoder)`.** Call `usingPathEncoder(Encoder)`, `usingQueryEncoder(Encoder)`, or both.
   `LegacyEncoder` replaces `BuiltinEncoder`. Check code that calls it after adding parameters: `usingEncoder` encoded
   only the parameters added after the call, and `usingQueryEncoder` encodes every parameter, including the ones added
   before it. To encode one parameter differently, pass its encoder to `addParameter(key, value, encoder)`.
4. **Expect unencoded text from `getPath()`, `getPathSegments()`, and `S3UrlBuilder.getKey()`.** They returned
   percent-encoded text in 2.x. `getKey()` now returns the key as given, without leading slashes.
5. **Check code that parses a URL and writes it back.** `new UrlBuilder(String)` now decodes the path, so `/a%20b` stays
   `/a%20b`. In 2.x it became `/a%2520b`. A trailing slash now stays as well, so `/foo/` no longer becomes `/foo`.
   Check S3 keys that end with a slash or contain `//` too: `S3UrlBuilder` now keeps those slashes.
6. **Check code that adds a parameter whose value is only whitespace.** `addParameter("q", " ")` now writes `q=%20`. In
   2.x it wrote `q` alone, as a null or empty value still does.
7. **Check code that calls both `expireIn` and `expireAt`.** The later call now wins. In 2.x `expireAt` always won.
8. **Check code that passes zero, a negative number, or null as an expiration.** `expireIn` now throws
   `IllegalArgumentException` for a zero or negative duration, and `expireAt` throws it for null. In 2.x
   `S3UrlBuilder.expireIn(0, unit)` produced an unsigned URL, and `expireAt(null)` cleared the expiration.
9. **Move from `expireAt(Date)` to `expireAt(Instant)`.** The `Date` overload still works and is deprecated.
10. **Check code that pairs a scheme with port 80 or 443, or reads `getPort()`.** The port now wins over the scheme:
    `https://host:80/` parses as `http://host/`, and `setPort(80)` turns HTTPS off. A `usingSsl(boolean)` call after
    the port keeps the port, so `new UrlBuilder("host", 80, "a").usingSsl()` writes `https://host:80/a` where 2.x
    wrote `https://host/a`. `new UrlBuilder(hostname, path)` sets no port, so its `getPort()` returns 0 where 2.x
    returned 80.
11. **Update any rule that names the BouncyCastle artifacts.** The library now depends on `bcprov-jdk18on` and
    `bcpkix-jdk18on` 1.87, where 2.x depended on `bcprov-jdk15to18` and `bcpkix-jdk15to18` 1.80.

### Added

- `UrlSigner` and `UrlBuilder.usingUrlSigner(UrlSigner)` add a signature of your own to any URL. The signer receives a
  `SigningContext`, adds any parameter the signature must cover with `addParameter`, signs `getStringToSign()`, and
  returns the query parameters to append.
- `PathSegmentEncoder` and `QueryParameterEncoder` encode by RFC 3986, and `usingPathEncoder(Encoder)` and
  `usingQueryEncoder(Encoder)` set them separately.
- `LegacyEncoder` and `usingLegacyEncoding()` produce the encoding of 2.x.
- `NoEncodingEncoder.INSTANCE` is a shared instance of that encoder.
- `S3UrlBuilder` and `CloudfrontUrlBuilder` gain `expireAt(Instant)` and `expireIn(Duration)`.
- `S3UrlBuilder.withEndpoint(endpoint, region)` sets a custom endpoint and the region Signature Version 4 signs for.
- `CloudfrontPrivateKeyUtils.fromPemString` reads PKCS#8 keys (`BEGIN PRIVATE KEY`) as well as PKCS#1 keys.
- The jar declares the module name `com.widen.urlbuilder` through `Automatic-Module-Name`.

### Changed

- `S3UrlBuilder` signs with AWS Signature Version 4 by default. A signed URL needs the bucket's region and lasts at most
  7 days. `inRegion` sets the signing region, and `withEndpoint(endpoint)` clears it.
- Path segments and query parameters are encoded by RFC 3986, with separate rules for each.
- The builder encodes the path when it writes the URL, so `getPath()` and `getPathSegments()` return unencoded text,
  and `S3UrlBuilder.getKey()` returns the key as given.
- `new UrlBuilder(String)` decodes the path of the URL it parses.
- A parameter value of only whitespace is written, as in `q=%20`. A null or empty value still writes the name alone.
- Port 80 turns HTTPS off, as port 443 turns it on, and `new UrlBuilder(hostname, path)` sets no port.
- `expireIn` and `expireAt` replace each other, so the later call sets the expiration.
- `expireIn` rejects a zero or negative duration, and `expireAt` rejects null.
- `toURL()` wraps a `MalformedURLException` in a `RuntimeException`.
- `NoEncodingEncoder` is final.
- The BouncyCastle dependency is `bcprov-jdk18on` and `bcpkix-jdk18on` 1.87.

### Deprecated

- `S3UrlBuilder.expireAt(Date)` and `CloudfrontUrlBuilder.expireAt(Date)`. Use `expireAt(Instant)`.
- `LegacyEncoder` and `usingLegacyEncoding()`. They exist to ease the move from 2.x.
- `S3UrlBuilder.usingSignatureVersion2()`, which signs with Signature Version 2 for buckets and S3-compatible services
  that still accept it. Sign with Signature Version 4 instead.

### Removed

- `UrlBuilder.usingEncoder(Encoder)`. Use `usingPathEncoder(Encoder)` and `usingQueryEncoder(Encoder)`.
- `BuiltinEncoder`. Use `LegacyEncoder` for the same output.

### Fixed

- `S3UrlBuilder` signed a URL that S3 rejected when the URL set both an attachment file name and a content type.
- `S3UrlBuilder.toString()` left parameters behind when it threw, and the next call wrote and signed them twice.
- `CloudfrontUrlBuilder` could sign one expiration and write another when the clock passed a second boundary during
  signing, and CloudFront rejected that URL.
- `CloudfrontUrlBuilder` produced wrong signatures when several threads called `toString()` on one builder.
- `CloudfrontUrlBuilder.addParameter` and `UrlBuilder.addParameters` threw `NullPointerException` for a null value. A
  null value now adds the name alone.
- `new UrlBuilder(String)` mishandled a query value that contains `=`. It removed trailing `=` characters, so
  `?sig=abc==` parsed as `abc`, and it dropped the whole parameter for a value such as `?a=b=c`.
- `new UrlBuilder(String)` encoded an already encoded path a second time.
- `new UrlBuilder(String)` dropped the trailing slash of a path, so `/foo/` became `/foo`.
- `S3UrlBuilder` and `CloudfrontUrlBuilder` dropped a trailing slash and empty segments from a key, so `folder/` and
  `a//b` named the objects `folder` and `a/b`.
- `UrlBuilder` left port 80 out of an `https` URL and port 443 out of an `http` URL, so the URL named the wrong port.
  `SigningContext.getPort()` returned null for those ports as well.

### Security

- On Java 9 and later, the file name in a `Content-Disposition` header kept `\`, `"`, and `%`. A file name containing
  `"` could add a `filename*` parameter and change the name a browser saves the download under. `withAttachmentFilename`
  now removes those characters on every Java version.
- BouncyCastle 1.87 includes the fixes for CVE-2025-14813, CVE-2026-5588, CVE-2026-8763, and CVE-2026-13506.
