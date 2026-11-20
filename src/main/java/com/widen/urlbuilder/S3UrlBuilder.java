/*
 * Copyright 2010 Widen Enterprises, Inc.
 * Madison, Wisconsin USA -- www.widen.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.widen.urlbuilder;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for constructing syntactically correct Amazon S3 URLs using a fluent method-chaining API.
 * 
 * <p>This builder creates both unsigned and signed (pre-authenticated) S3 URLs. It handles
 * the complexities of S3 bucket naming, DNS-compatible hostnames, and AWS Signature Version 4 (SigV4) presigning.
 *
 * <p><b>Typical usage (signed URL):</b>
 * <pre>{@code
 * String signedUrl = new S3UrlBuilder("my-bucket", "path/to/file.pdf")
 *     .inRegion("us-west-2")
 *     .expireIn(Duration.ofHours(1))
 *     .usingCredentials(awsAccessKey, awsSecretKey)
 *     .toString();
 * // Result: http://my-bucket.s3.us-west-2.amazonaws.com/path/to/file.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&...
 * }</pre>
 *
 * <p><b>With attachment download:</b>
 * <pre>{@code
 * String downloadUrl = new S3UrlBuilder("my-bucket", "documents/report.pdf")
 *     .inRegion("us-west-2")
 *     .usingSsl()
 *     .withAttachmentFilename("quarterly-report.pdf")
 *     .expireIn(Duration.ofMinutes(30))
 *     .usingCredentials(awsAccessKey, awsSecretKey)
 *     .toString();
 * }</pre>
 * 
 * <p><b>With regional endpoint:</b>
 * <pre>{@code
 * String url = new S3UrlBuilder("my-eu-bucket", "data/file.json")
 *     .inRegion("eu-west-1")
 *     .usingSsl()
 *     .expireIn(Duration.ofHours(1))
 *     .usingCredentials(awsAccessKey, awsSecretKey)
 *     .toString();
 * }</pre>
 * 
 * <p><b>Bucket encoding modes:</b>
 * <ul>
 *   <li>{@link #usingBucketInHostname()} (default) - {@code bucket.s3.amazonaws.com/key}</li>
 *   <li>{@link #usingBucketVirtualHost()} - {@code bucket/key} (bucket as hostname)</li>
 *   <li>{@link #usingBucketInPath()} - {@code s3.amazonaws.com/bucket/key}</li>
 * </ul>
 * 
 * <p><b>AWS STS Token Support:</b>
 * <pre>{@code
 * String url = new S3UrlBuilder("my-bucket", "file.txt")
 *     .inRegion("us-west-2")
 *     .expireIn(Duration.ofHours(1))
 *     .usingCredentials(accessKey, secretKey, sessionToken)
 *     .toString();
 * }</pre>
 *
 * <p><b>Signing:</b> a URL with an expiration is signed with SigV4, which needs the bucket's region from
 * {@link #inRegion(String)} or {@link #withEndpoint(String, String)}, and lasts at most 7 days.
 * {@link #toString()} throws {@link IllegalStateException} without a region and {@link IllegalArgumentException}
 * for an expiration outside that range. {@link #usingSignatureVersion2()} signs with the deprecated Signature
 * Version 2 instead, which needs no region.
 *
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html">Authenticating
 * Requests: Using Query Parameters (AWS Signature Version 4)</a>
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/RESTAuthentication.html">S3 REST Authentication</a>
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/VirtualHosting.html">S3 Virtual Hosting</a>
 * @since 1.0.0
 */
public class S3UrlBuilder
{
    private String bucket;

    private String key;

    private String endpoint = "s3.amazonaws.com";

    private String region;

    private BucketEncoding requestedBucketEncoding = BucketEncoding.DNS;

    private final ExpireDateHolder expireDate = new ExpireDateHolder();

    private String attachmentFilename;

    private String awsKey;

    private String awsPrivateKey;

    private String awsSessionToken;

    private String contentType;

    private boolean signatureVersion2;

    private Clock clock = Clock.systemUTC();

    private static final long MAX_SIGV4_EXPIRES_SECONDS = Duration.ofDays(7).getSeconds();

    private static final DateTimeFormatter SIGV4_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter SIGV4_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT).withZone(ZoneOffset.UTC);

    // S3 reads "+" in a path as a space, so encode keys as the AWS SDKs do: everything but A-Z a-z 0-9 - . _ ~
    private final UrlBuilder builder = new UrlBuilder().usingPathEncoder(new QueryParameterEncoder());

    /**
     * Enumeration of bucket encoding strategies for S3 URLs.
     */
    private enum BucketEncoding
    {
        /** Bucket as subdomain: bucket.s3.amazonaws.com/key (default) */
        DNS,
        /** Bucket as hostname: bucket/key */
        VIRTUAL_DNS,
        /** Bucket in path: s3.amazonaws.com/bucket/key */
        PATH
    }

    /**
     * Easily, and correctly, construct URLs for S3.
     *
     * @param bucket name as String
     * @param key    name as String
     * @throws IllegalArgumentException if bucket or key is null
     */
    public S3UrlBuilder(String bucket, String key)
    {
        withBucket(bucket);

        withKey(key);

        builder.modeFullyQualified();
    }

    /**
     * Sets the relative duration until the URL expires.
     * 
     * <p>The actual expiration time is calculated when {@link #toString()} is called,
     * allowing the same builder to generate URLs with fresh expiration times.
     *
     * <p>Replaces an expiration set earlier with {@code expireAt}.
     *
     * <p>A Signature Version 4 URL must expire from 1 second to 7 days after it is signed; {@link #toString()}
     * throws {@link IllegalArgumentException} for a duration outside that range, including one under a second.
     *
     * @param duration The time from URL generation until the URL expires (must be positive)
     * @return This builder for method chaining
     * @throws IllegalArgumentException if duration is null or not positive
     * @since 3.0.0
     */
    public S3UrlBuilder expireIn(Duration duration)
    {
        expireDate.expireIn(duration);
        return this;
    }

    /**
     * Sets the relative duration until the URL expires, as a count of a {@link TimeUnit}.
     * Behaves as {@link #expireIn(Duration)}.
     *
     * @param duration The duration value (must be positive)
     * @param unit The time unit for the duration
     * @return This builder for method chaining
     * @throws IllegalArgumentException if unit is null or duration is not positive
     */
    public S3UrlBuilder expireIn(long duration, TimeUnit unit)
    {
        expireDate.expireIn(duration, unit);
        return this;
    }

    /**
     * Sets the absolute expiration time for the URL.
     * 
     * <p>The expiration time is accurate to seconds (milliseconds are truncated).
     * Behaves as {@link #expireAt(Instant)}.
     *
     * @param date The absolute date/time when the URL should expire
     * @return This builder for method chaining
     * @throws IllegalArgumentException if date is null
     * @deprecated Use {@link #expireAt(Instant)} instead for better type safety with Java 8+ time API
     */
    @Deprecated
    public S3UrlBuilder expireAt(Date date)
    {
        InternalUtils.checkNotNull(date, "date");
        return expireAt(date.toInstant());
    }

    /**
     * Sets the absolute expiration time for the URL using Java 8+ Instant.
     * 
     * <p>The expiration time is accurate to seconds (milliseconds are truncated).
     * This is the preferred method over {@link #expireAt(Date)}.
     * It replaces an expiration set earlier with {@code expireIn}.
     *
     * <p>A Signature Version 4 URL must expire from 1 second to 7 days after it is signed, which is the moment
     * {@link #toString()} runs; {@code toString()} throws {@link IllegalArgumentException} for an instant outside
     * that range.
     *
     * @param instant The absolute instant when the URL should expire
     * @return This builder for method chaining
     * @throws IllegalArgumentException if instant is null
     * @since 3.0.0
     */
    public S3UrlBuilder expireAt(Instant instant)
    {
        expireDate.expireAt(instant);
        return this;
    }

    /**
     * Sets AWS credentials for generating signed URLs.
     * 
     * <p>Credentials are required when generating pre-authenticated URLs with an expiration time.
     * A Signature Version 4 URL also needs the bucket's region: without one, {@link #toString()} throws
     * {@link IllegalStateException}.
     *
     * @param awsKey The AWS access key ID
     * @param awsPrivateKey The AWS secret access key
     * @return This builder for method chaining
     * @throws IllegalArgumentException if awsKey or awsPrivateKey is null
     */
    public S3UrlBuilder usingCredentials(String awsKey, String awsPrivateKey)
    {
        InternalUtils.checkNotNull(awsKey, "awsKey");
        InternalUtils.checkNotNull(awsPrivateKey, "awsPrivateKey");

        this.awsKey = awsKey;
        this.awsPrivateKey = awsPrivateKey;

        return this;
    }

    /**
     * Sets AWS credentials including an STS session token for generating signed URLs.
     * 
     * <p>Use this method when authenticating with temporary credentials from AWS Security
     * Token Service (STS), such as when using IAM roles or federated access.
     * Behaves as {@link #usingCredentials(String, String)} otherwise.
     *
     * @param awsKey The AWS access key ID (temporary)
     * @param awsPrivateKey The AWS secret access key (temporary)
     * @param awsSessionToken The STS session token
     * @return This builder for method chaining
     * @throws IllegalArgumentException if any parameter is null
     */
    public S3UrlBuilder usingCredentials(String awsKey, String awsPrivateKey, String awsSessionToken)
    {
        usingCredentials(awsKey, awsPrivateKey);
        InternalUtils.checkNotNull(awsSessionToken, "awsSessionToken");

        this.awsSessionToken = awsSessionToken;
        return this;
    }

    /**
     * Signs the URL with AWS Signature Version 2 (SigV2) instead of Signature Version 4.
     *
     * <p>S3 accepts SigV2 only in the eight regions launched before 2014, and only where no IAM policy denies it
     * with the {@code s3:signatureversion} condition key.
     *
     * @return This builder for method chaining
     * @deprecated AWS is retiring SigV2 for S3; sign with Signature Version 4 instead.
     * @since 3.0.0
     */
    @Deprecated
    public S3UrlBuilder usingSignatureVersion2()
    {
        signatureVersion2 = true;
        return this;
    }

    /**
     * Sets the clock that gives the signing time, from which {@code expireIn} counts.
     */
    S3UrlBuilder usingClock(Clock clock)
    {
        InternalUtils.checkNotNull(clock, "clock");
        this.clock = clock;
        return this;
    }

    /**
     * Set a custom endpoint for the S3 service.
     * <p>
     * When using Amazon services you should use #inRegion() instead of this method.
     * This method is only necessary when using S3 API compatible services like <a href="http://ceph.com/">CEPH</a>
     *
     * <p>Clears the signing region, including one {@link #inRegion(String)} set, so a Signature Version 4 URL
     * throws {@link IllegalStateException} from {@link #toString()} until a region is given. Use
     * {@link #withEndpoint(String, String)} to sign for this endpoint.
     *
     * @param endpoint fully-quantified DNS hostname of S3 service
     * @return This builder for method chaining
     */
    public S3UrlBuilder withEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
        this.region = null;
        return this;
    }

    /**
     * Set a custom endpoint for the S3 service and the region Signature Version 4 signs for.
     *
     * <p>Use this for an S3 API compatible service or a custom endpoint. The region must be the one the service
     * expects, or the service rejects the signature with a 400.
     *
     * @param endpoint fully-quantified DNS hostname of S3 service, with {@code :PORT} for a non-default port
     * @param region the signing region (e.g., "us-east-1")
     * @return This builder for method chaining
     * @throws IllegalArgumentException if region is null or blank
     * @since 3.0.0
     */
    public S3UrlBuilder withEndpoint(String endpoint, String region)
    {
        InternalUtils.checkNotBlank(region, "region");
        this.endpoint = endpoint;
        this.region = region;
        return this;
    }

    /**
     * Set endpoint based on AWS Region of bucket: {@code s3.$REGION.amazonaws.com}
     *
     * <p>Also sets the region Signature Version 4 signs for. It must be the bucket's region: a URL signed for
     * another region fails with a 400 when used.
     *
     * @param region AWS region identifier (e.g., "us-east-1", "eu-west-1")
     * @return This builder for method chaining
     * @throws IllegalArgumentException if region is null
     * @see <a href="http://docs.amazonwebservices.com/AmazonS3/latest/dev/LocationSelection.html">S3 Location Selection Docs</a>
     * @see <a href="https://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region">AWS Endpoint Docs</a>
     */
    public S3UrlBuilder inRegion(String region)
    {
        InternalUtils.checkNotNull(region, "region");
        endpoint = "s3." + region + ".amazonaws.com";
        this.region = region;
        return this;
    }

    /**
     * Reset S3 bucket to new value
     *
     * @param bucket S3 bucket name
     * @return This builder for method chaining
     * @throws IllegalArgumentException if bucket is blank
     */
    public S3UrlBuilder withBucket(String bucket)
    {
        InternalUtils.checkNotBlank(bucket, "bucket");

        this.bucket = bucket;

        return this;
    }

    /**
     * Reset S3 key to new value
     *
     * <p>The key is literal text: the builder encodes every character except letters, digits, {@code -._~},
     * and {@code /}, as the AWS SDKs do, so {@code a%20b.txt} names that object and not {@code a b.txt}, and
     * {@code a+b.txt} is not read as {@code a b.txt}. Leading slashes are dropped,
     * and every other slash is kept, so {@code folder/} and {@code a//b} name objects of their own. After the
     * first slash of a run, each slash is written as {@code %2F}, as the AWS SDKs write it: {@code a//b} becomes
     * {@code a/%2Fb}.
     *
     * @param key S3 object key
     * @return This builder for method chaining
     * @throws IllegalArgumentException if key is blank
     */
    public S3UrlBuilder withKey(String key)
    {
        InternalUtils.checkNotBlank(key, "key");

        this.key = UrlBuilder.stripLeadingSlashes(key);

        return this;
    }

    /**
     * Set URL generation to use bucket name as hostname.
     *
     * @return This builder for method chaining
     * @see <a href="http://docs.amazonwebservices.com/AmazonS3/latest/dev/VirtualHosting.html">S3 Virtual Hosting Docs</a>
     */
    public S3UrlBuilder usingBucketVirtualHost()
    {
        requestedBucketEncoding = BucketEncoding.VIRTUAL_DNS;
        return S3UrlBuilder.this;
    }

    /**
     * Set URL generation to encode bucket into path.
     * @return This builder for method chaining
     */
    public S3UrlBuilder usingBucketInPath()
    {
        requestedBucketEncoding = BucketEncoding.PATH;
        return S3UrlBuilder.this;
    }

    /**
     * Set URL generation to prefix bucket to hostname ".s3.amazonaws.com"
     * <p>
     * This is the default generation mode.
     * @return This builder for method chaining
     */
    public S3UrlBuilder usingBucketInHostname()
    {
        requestedBucketEncoding = BucketEncoding.DNS;
        return S3UrlBuilder.this;
    }

    /**
     * Generates the S3 URL (signed or unsigned depending on configuration).
     * 
     * <p>The URL generation process:
     * <ol>
     *   <li>Determines the hostname based on bucket encoding mode</li>
     *   <li>Constructs the path with the object key</li>
     *   <li>Adds any response headers (Content-Disposition, Content-Type)</li>
     *   <li>If an expiration is set, generates and appends the AWS Signature Version 4 parameters, or Signature
     *       Version 2 after {@link #usingSignatureVersion2()}</li>
     * </ol>
     * 
     * <p>The builder can be reused - calling {@code toString()} multiple times will
     * generate fresh URLs with updated expiration times (if using relative expiration).
     *
     * @return The complete S3 URL as a String
     * @throws IllegalStateException if credentials are required but not set, or a Signature Version 4 URL has no region
     * @throws IllegalArgumentException if a Signature Version 4 URL expires less than 1 second or more than 7 days
     *                                  after this call
     */
    public String toString()
    {
        List<String> keySegments = keySegments(key);

        // AWS signature V2 signs the path exactly as the request sends it, encoded
        String canonicalResource;

        if (!isValidDnsBucketName() || BucketEncoding.PATH.equals(requestedBucketEncoding))
        {
            List<String> pathSegments = new ArrayList<>();
            pathSegments.add(bucket);
            pathSegments.addAll(keySegments);

            builder.withHostname(endpoint);
            canonicalResource = builder.withExactPathSegments(pathSegments).buildPath();
        }
        else
        {
            builder.withHostname(BucketEncoding.VIRTUAL_DNS.equals(requestedBucketEncoding) ? bucket : bucket + "." + endpoint);
            canonicalResource = "/" + bucket + builder.withExactPathSegments(keySegments).buildPath();
        }

        // AWS signature V4 signs the path of the request alone, without the bucket the hostname names
        String requestPath = builder.buildPath();

        try
        {
            if (StringUtilsInternal.isNotBlank(attachmentFilename))
            {
                canSign();

                builder.addParameter("response-content-disposition", HttpUtils.createContentDispositionHeader("attachment", attachmentFilename));
            }

            if(StringUtilsInternal.isNotBlank(contentType))
            {
                builder.addParameter("response-content-type", contentType);
            }

            if (expireDate.isSet())
            {
                canSign();
                Instant signingTime = clock.instant();
                Instant expireInstant = expireDate.getExpireDate(signingTime);
                InternalUtils.checkNotNull(expireInstant, "expire instant");
                Map<String, String> params = signatureVersion2
                    ? signParams(expireInstant, canonicalResource, builder)
                    : signV4Params(signingTime, expireInstant, requestPath, builder);
                builder.addParameters(params);
            }

            return builder.toString();
        }
        finally
        {
            builder.clearParameters(); //clean for any subsequent calls to toString()
        }
    }

    /**
     * Splits the key into path segments the way the AWS SDKs write it: the first slash of a run separates segments,
     * and the rest belong to the next segment, which encodes them as %2F, so {@code a///b} becomes
     * {@code a/%2F%2Fb}. HTTP clients and proxies may merge {@code //}, which would change the key.
     */
    private static List<String> keySegments(String key)
    {
        List<String> segments = new ArrayList<>();
        if (key.isEmpty())
        {
            return segments;
        }

        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < key.length(); i++)
        {
            char c = key.charAt(i);
            if (c != '/')
            {
                segment.append(c);
                continue;
            }

            segments.add(segment.toString());
            segment.setLength(0);
            while (i + 1 < key.length() && key.charAt(i + 1) == '/')
            {
                segment.append('/');
                i++;
            }
        }
        segments.add(segment.toString());

        return segments;
    }

    private void canSign()
    {
        if (!expireDate.isSet())
        {
            throw new IllegalStateException("Expire date must be set when generating signed URLs.");
        }

        if (StringUtilsInternal.isBlank(awsKey) || StringUtilsInternal.isBlank(awsPrivateKey))
        {
            throw new IllegalStateException("AWS Account and AWS Private Key must be specified when generating signed URLs.");
        }
    }

    /**
     * Get current key value.
     *
     * @return the key as given, unencoded and without leading slashes
     */
    public String getKey()
    {
        return key;
    }

    /**
     * Set generated URL to use the "https" scheme.
     * @return This builder for method chaining
     */
    public S3UrlBuilder usingSsl()
    {
        builder.usingSsl();

        return this;
    }

    /**
     * Set generated URL to use "https" scheme.
     *
     * @param useSsl true to use "https" or false to use "http"
     * @return This builder for method chaining
     */
    public S3UrlBuilder usingSsl(boolean useSsl)
    {
        builder.usingSsl(useSsl);

        return this;
    }

    /**
     * Add 'hash' fragment to generated URL. Value does not modify S3 signature.
     *
     * @param fragment URL fragment identifier
     * @return This builder for method chaining
     */
    public S3UrlBuilder withFragment(String fragment)
    {
        builder.withFragment(fragment);

        return this;
    }

    /**
     * Set generation mode to Protocol Relative; e.g. <code>"//my.host.com/foo/bar.html"</code>
     * @return This builder for method chaining
     */
    public S3UrlBuilder modeProtocolRelative()
    {
        builder.modeProtocolRelative();

        return this;
    }

    /**
     * Set generation mode to Fully Qualified. This is the default mode; e.g. {@code "http://my.host.com/foo/bar.html"}
     *
     * <p>Default mode.
     * @return This builder for method chaining
     */
    public S3UrlBuilder modeFullyQualified()
    {
        builder.modeFullyQualified();

        return this;
    }

    /**
     * Sets the Content-Type response header hint.
     * 
     * <p>When set, S3 will include a {@code response-content-type} parameter that
     * instructs S3 to return the specified Content-Type header in the response,
     * overriding the object's stored content type.
     * 
     * @param contentType The MIME type for the response (e.g., "application/pdf")
     * @return This builder for method chaining
     */
    public S3UrlBuilder withContentType(String contentType)
    {
        this.contentType = contentType;
        return this;
    }

    /**
     * Hint for "attachment" filename. Informs S3 to add "Content-Disposition; attachment" HTTP header.
     *
     * @param filename the filename for the attachment; most browsers will raise a "Save File As..." dialog, or immediately save file
     * @return This builder for method chaining
     */
    public S3UrlBuilder withAttachmentFilename(String filename)
    {
        if (StringUtilsInternal.isBlank(filename))
        {
            attachmentFilename = null;
        }
        else
        {
            attachmentFilename = filename;
        }

        return this;
    }

    private boolean isValidDnsBucketName()
    {
        return AmazonAWSJavaSDKInternal.isValidV2BucketName(bucket);
    }

    /**
     * Generates AWS V2 signature parameters for the URL.
     * 
     * <p>The signature is computed according to the AWS V2 Signature specification:
     * <ol>
     *   <li>Construct the string to sign (HTTP verb, headers, expiration, canonical resource)</li>
     *   <li>Compute HMAC-SHA1 using the secret key</li>
     *   <li>Base64 encode the result</li>
     * </ol>
     * 
     * @param expireTime The expiration time for the signature
     * @param canonicalResource The canonical S3 resource path
     * @param builder The URL builder with query parameters to include in signature
     * @return Map containing Signature, Expires, and AWSAccessKeyId parameters
     * @see <a href="http://s3.amazonaws.com/doc/s3-developer-guide/RESTAuthentication.html">S3 REST Authentication</a>
     */
    private Map<String, String> signParams(Instant expireTime, String canonicalResource, UrlBuilder builder)
    {
        String expires = String.valueOf(expireTime.getEpochSecond());

        StringBuilder stringToSign = new StringBuilder();

        stringToSign.append("GET\n"); //http verb
        stringToSign.append("\n"); //content md5
        stringToSign.append("\n"); //content type
        stringToSign.append(expires).append("\n");
        if (awsSessionToken != null)
        {
            stringToSign.append("x-amz-security-token:").append(awsSessionToken).append("\n");
        }
        stringToSign.append(canonicalResource);

        String separator = "?";
        for (UrlBuilder.QueryParam queryParam : builder.queryParams)
        {
            stringToSign.append(separator).append(queryParam.key).append("=").append(queryParam.value);
            separator = "&";
        }

        //System.err.println("sign text for " + canonicalResource + "\n" + stringToSign);

        String signature = AmazonAWSJavaSDKInternal.sign(stringToSign.toString(), awsPrivateKey);

        HashMap<String, String> params = new HashMap<>();
        params.put("Signature", signature);
        params.put("Expires", expires);
        params.put("AWSAccessKeyId", awsKey);
        if (awsSessionToken != null)
        {
            params.put("x-amz-security-token", awsSessionToken);
        }

        return params;
    }

    /**
     * Generates AWS Signature Version 4 query parameters for a presigned GET, in the order the URL writes them.
     *
     * @param signingTime the moment the URL is signed, written as {@code X-Amz-Date}
     * @param expireTime the expiration, from which {@code X-Amz-Expires} counts whole seconds after signingTime
     * @param requestPath the encoded path as the URL writes it
     * @param builder the URL builder whose hostname and query parameters the signature covers
     * @throws IllegalStateException if no region is set
     * @throws IllegalArgumentException if the URL expires less than 1 second or more than 7 days after signingTime
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html">Authenticating
     * Requests: Using Query Parameters (AWS Signature Version 4)</a>
     */
    private Map<String, String> signV4Params(Instant signingTime, Instant expireTime, String requestPath, UrlBuilder builder)
    {
        if (region == null)
        {
            throw new IllegalStateException("A region must be set to sign with Signature Version 4: "
                + "call inRegion(region) or withEndpoint(endpoint, region).");
        }

        long expiresSeconds = Duration.between(signingTime, expireTime).getSeconds();
        if (expiresSeconds < 1 || expiresSeconds > MAX_SIGV4_EXPIRES_SECONDS)
        {
            throw new IllegalArgumentException("A Signature Version 4 URL must expire from 1 second to 7 days after it is "
                + "signed, not " + expiresSeconds + " seconds.");
        }

        String timestamp = SIGV4_TIMESTAMP.format(signingTime);
        String date = SIGV4_DATE.format(signingTime);
        String scope = date + "/" + region + "/s3/aws4_request";

        Map<String, String> params = new LinkedHashMap<>();
        params.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        params.put("X-Amz-Credential", awsKey + "/" + scope);
        params.put("X-Amz-Date", timestamp);
        params.put("X-Amz-Expires", String.valueOf(expiresSeconds));
        if (awsSessionToken != null)
        {
            params.put("X-Amz-Security-Token", awsSessionToken);
        }
        params.put("X-Amz-SignedHeaders", "host");

        // S3 decodes the path before it signs it, so the %2F that keeps a run of slashes apart signs as a slash
        String canonicalUri = requestPath.replace("%2F", "/");

        String canonicalRequest = "GET\n"
            + canonicalUri + "\n"
            + canonicalQueryString(builder.queryParams, params) + "\n"
            + "host:" + builder.getHostname() + "\n"
            + "\n"
            + "host\n"
            + "UNSIGNED-PAYLOAD";

        String stringToSign = "AWS4-HMAC-SHA256\n"
            + timestamp + "\n"
            + scope + "\n"
            + hex(sha256(canonicalRequest));

        byte[] signingKey = hmacSha256(("AWS4" + awsPrivateKey).getBytes(StandardCharsets.UTF_8), date);
        signingKey = hmacSha256(signingKey, region);
        signingKey = hmacSha256(signingKey, "s3");
        signingKey = hmacSha256(signingKey, "aws4_request");

        params.put("X-Amz-Signature", hex(hmacSha256(signingKey, stringToSign)));

        return params;
    }

    /**
     * Encodes every name and value with the unreserved set and sorts by encoded name, which SigV4 requires.
     */
    private static String canonicalQueryString(List<UrlBuilder.QueryParam> queryParams, Map<String, String> signatureParams)
    {
        Encoder encoder = new QueryParameterEncoder();
        Map<String, String> sorted = new TreeMap<>();
        for (UrlBuilder.QueryParam queryParam : queryParams)
        {
            sorted.put(encoder.encode(queryParam.key), encoder.encode(queryParam.value));
        }
        for (Map.Entry<String, String> param : signatureParams.entrySet())
        {
            sorted.put(encoder.encode(param.getKey()), encoder.encode(param.getValue()));
        }

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> param : sorted.entrySet())
        {
            if (query.length() > 0)
            {
                query.append('&');
            }
            query.append(param.getKey()).append('=').append(param.getValue());
        }
        return query.toString();
    }

    private static byte[] hmacSha256(byte[] key, String data)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException("Failed to generate signature: " + e.getMessage(), e);
        }
    }

    private static byte[] sha256(String data)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException("Failed to hash canonical request: " + e.getMessage(), e);
        }
    }

    private static String hex(byte[] bytes)
    {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }


    /*
     * Copyright 2010-2011 Amazon.com, Inc. or its affiliates. All Rights Reserved.
     *
     * Licensed under the Apache License, Version 2.0 (the "License").
     * You may not use this file except in compliance with the License.
     * A copy of the License is located at
     *
     *  http://aws.amazon.com/apache2.0
     *
     * or in the "license" file accompanying this file. This file is distributed
     * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
     * express or implied. See the License for the specific language governing
     * permissions and limitations under the License.
     */

    /**
     * Internal utilities for working with Amazon S3 bucket names and signatures.
     * 
     * <p>Provides bucket name validation according to S3 naming guidelines and
     * HMAC-SHA1 signature generation for AWS V2 authentication.
     * 
     * <p>Code derived from Amazon AWS Java SDK.
     * 
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/BucketRestrictions.html">S3 Bucket Restrictions</a>
     */
    private static class AmazonAWSJavaSDKInternal
    {

        /**
         * Validates that the specified bucket name is valid for Amazon S3 V2 naming
         * (i.e. DNS addressable in virtual host style). Throws an
         * IllegalArgumentException if the bucket name is not valid.
         * <p/>
         * S3 bucket naming guidelines are specified in <a href="http://docs.amazonwebservices.com/AmazonS3/latest/dev/index.html?BucketRestrictions.html"
         * > http://docs.amazonwebservices.com/AmazonS3/latest/dev/index.html?
         * BucketRestrictions.html</a>
         *
         * @param bucketName The bucket name to validate.
         * @throws IllegalArgumentException If the specified bucket name doesn't follow Amazon S3's
         *                                  guidelines.
         */
        private static void validateBucketName(String bucketName) throws IllegalArgumentException
        {
            /*
             * From the Amazon S3 bucket naming guidelines in the Amazon S3 Developer Guide
             *
             * To conform with DNS requirements:
             *  - Bucket names should not contain underscores (_)
             *  - Bucket names should be between 3 and 63 characters long
             *  - Bucket names should not end with a dash or a period
             *  - Bucket names cannot contain two, adjacent periods
             *  - Bucket names cannot contain dashes next to periods
             *     - (e.g., "my-.bucket.com" and "my.-bucket" are invalid)
             */

            if (bucketName == null)
            {
                throw new IllegalArgumentException("Bucket name cannot be null");
            }

            if (!bucketName.toLowerCase().equals(bucketName))
            {
                throw new IllegalArgumentException("Bucket name should not contain uppercase characters");
            }

            if (bucketName.contains("_"))
            {
                throw new IllegalArgumentException("Bucket name should not contain '_'");
            }

            if (bucketName.contains("!") || bucketName.contains("@") || bucketName.contains("#"))
            {
                throw new IllegalArgumentException("Bucket name contains illegal characters");
            }

            if (bucketName.length() < 3 || bucketName.length() > 63)
            {
                throw new IllegalArgumentException("Bucket name should be between 3 and 63 characters long");
            }

            if (bucketName.endsWith("-") || bucketName.endsWith("."))
            {
                throw new IllegalArgumentException("Bucket name should not end with '-' or '.'");
            }

            if (bucketName.contains(".."))
            {
                throw new IllegalArgumentException("Bucket name should not contain two adjacent periods");
            }

            if (bucketName.contains("-.") ||
                bucketName.contains(".-"))
            {
                throw new IllegalArgumentException("Bucket name should not contain dashes next to periods");
            }
        }

        /**
         * Returns true if the specified bucket name can be addressed using V2,
         * virtual host style, addressing. Otherwise, returns false indicating that
         * the bucket must be addressed using V1, path style, addressing.
         *
         * @param bucketName The name of the bucket to check.
         * @return True if the specified bucket name can be addressed in V2, virtual
         * host style, addressing otherwise false if V1, path style,
         * addressing is required.
         */
        public static boolean isValidV2BucketName(String bucketName)
        {
            if (bucketName == null)
            {
                return false;
            }

            try
            {
                validateBucketName(bucketName);
                return true;
            }
            catch (IllegalArgumentException e)
            {
                return false;
            }
        }

        /**
         * Computes RFC 2104-compliant HMAC signature.
         */
        private static String sign(String data, String key)
        {
            try
            {
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
                return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            }
            catch (Exception e)
            {
                throw new RuntimeException(new SignatureException("Failed to generate signature: " + e.getMessage(), e));
            }
        }

    }

}
