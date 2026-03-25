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
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for constructing syntactically correct Amazon S3 URLs using a fluent method-chaining API.
 * 
 * <p>This builder creates both unsigned and signed (pre-authenticated) S3 URLs. It handles
 * the complexities of S3 bucket naming, DNS-compatible hostnames, and AWS V2 signature generation.
 * 
 * <p><b>Typical usage (signed URL):</b>
 * <pre>{@code
 * String signedUrl = new S3UrlBuilder("my-bucket", "path/to/file.pdf")
 *     .expireIn(1, TimeUnit.HOURS)
 *     .usingCredentials(awsAccessKey, awsSecretKey)
 *     .toString();
 * // Result: http://my-bucket.s3.amazonaws.com/path/to/file.pdf?Expires=...&AWSAccessKeyId=...&Signature=...
 * }</pre>
 * 
 * <p><b>With attachment download:</b>
 * <pre>{@code
 * String downloadUrl = new S3UrlBuilder("my-bucket", "documents/report.pdf")
 *     .usingSsl()
 *     .withAttachmentFilename("quarterly-report.pdf")
 *     .expireIn(30, TimeUnit.MINUTES)
 *     .usingCredentials(awsAccessKey, awsSecretKey)
 *     .toString();
 * }</pre>
 * 
 * <p><b>With regional endpoint:</b>
 * <pre>{@code
 * String url = new S3UrlBuilder("my-eu-bucket", "data/file.json")
 *     .inRegion("eu-west-1")
 *     .usingSsl()
 *     .expireIn(1, TimeUnit.HOURS)
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
 *     .expireIn(1, TimeUnit.HOURS)
 *     .usingCredentials(accessKey, secretKey, sessionToken)
 *     .toString();
 * }</pre>
 * 
 * <p>The signature generation uses AWS V2 Signature format with HMAC-SHA1.
 * 
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/RESTAuthentication.html">S3 REST Authentication</a>
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/VirtualHosting.html">S3 Virtual Hosting</a>
 * @version 0.9.3
 * @since 1.0.0
 */
public class S3UrlBuilder
{
    private String bucket;

    private List<String> key;

    private String endpoint = "s3.amazonaws.com";

    private BucketEncoding requestedBucketEncoding = BucketEncoding.DNS;

    private final ExpireDateHolder expireDate = new ExpireDateHolder();

    private String attachmentFilename;

    private String awsKey;

    private String awsPrivateKey;

    private String awsSessionToken;

    private String contentType;

    private final UrlBuilder builder = new UrlBuilder();

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
     * Internal class to hold expiration date configuration.
     * 
     * <p>Supports both relative (duration + unit) and absolute (Instant) expiration times.
     */
    private static class ExpireDateHolder
    {
        long duration;

        TimeUnit unit;

        Instant instant;

        Instant getExpireDate()
        {
            if (instant != null)
            {
                return instant;
            }

            if (duration == 0)
            {
                return null;
            }

            long futureMillis = unit.toMillis(duration) + System.currentTimeMillis();

            return Instant.ofEpochMilli(futureMillis);
        }

        boolean isSet()
        {
            return getExpireDate() != null;
        }
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
     * @param duration The duration value (must be positive)
     * @param unit The time unit for the duration
     * @return This builder for method chaining
     * @throws NullPointerException if duration or unit is null
     */
    public S3UrlBuilder expireIn(long duration, TimeUnit unit)
    {
        InternalUtils.checkNotNull(unit, "unit");
        expireDate.duration = duration;
        expireDate.unit = unit;
        return this;
    }

    /**
     * Sets the absolute expiration time for the URL.
     * 
     * <p>The expiration time is accurate to seconds (milliseconds are truncated).
     *
     * @param date The absolute date/time when the URL should expire
     * @return This builder for method chaining
     * @deprecated Use {@link #expireAt(Instant)} instead for better type safety with Java 8+ time API
     */
    @Deprecated
    public S3UrlBuilder expireAt(Date date)
    {
        InternalUtils.checkNotNull(date, "date");
        expireDate.instant = date.toInstant();
        return this;
    }

    /**
     * Sets the absolute expiration time for the URL using Java 8+ Instant.
     * 
     * <p>The expiration time is accurate to seconds (milliseconds are truncated).
     * This is the preferred method over {@link #expireAt(Date)}.
     *
     * @param instant The absolute instant when the URL should expire
     * @return This builder for method chaining
     * @throws IllegalArgumentException if instant is null
     * @since 3.0.0
     */
    public S3UrlBuilder expireAt(Instant instant)
    {
        InternalUtils.checkNotNull(instant, "instant");
        expireDate.instant = instant;
        return this;
    }

    /**
     * Sets AWS credentials for generating signed URLs.
     * 
     * <p>Credentials are required when generating pre-authenticated URLs with an expiration time.
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
     * Set a custom endpoint for the S3 service.
     * <p>
     * When using Amazon services you should use #inRegion() instead of this method.
     * This method is only necessary when using S3 API compatible services like <a href="http://ceph.com/">CEPH</a>
     *
     * @param endpoint fully-quantified DNS hostname of S3 service
     * @return This builder for method chaining
     */
    public S3UrlBuilder withEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Set endpoint based on AWS Region of bucket: `$REGION.s3.s3.amazonaws.com`
     *
     * @param region AWS region identifier (e.g., "us-east-1", "eu-west-1")
     * @throws IllegalArgumentException if region is null
     * @see <a href="http://docs.amazonwebservices.com/AmazonS3/latest/dev/LocationSelection.html">S3 Location Selection Docs</a>
     * @see <a href="https://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region">AWS Endpoint Docs</a>
     * @return This builder for method chaining
     */
    public S3UrlBuilder inRegion(String region)
    {
        InternalUtils.checkNotNull(region, "region");
        endpoint = "s3." + region + ".amazonaws.com";
        return this;
    }

    /**
     * Reset S3 bucket to new value
     *
     * @param bucket S3 bucket name
     * @throws IllegalArgumentException if bucket is blank
     * @return This builder for method chaining
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
     * @param key S3 object key
     * @throws IllegalArgumentException if key is blank
     * @return This builder for method chaining
     */
    public S3UrlBuilder withKey(String key)
    {
        InternalUtils.checkNotBlank(key, "key");

        this.key = builder.makePathSegments(key, true);

        return this;
    }

    /**
     * Set URL generation to use bucket name as hostname.
     *
     * @see <a href="http://docs.amazonwebservices.com/AmazonS3/latest/dev/VirtualHosting.html">S3 Virtual Hosting Docs</a>
     * @return This builder for method chaining
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
     *   <li>If an expiration is set, generates and appends the AWS V2 signature</li>
     * </ol>
     * 
     * <p>The builder can be reused - calling {@code toString()} multiple times will
     * generate fresh URLs with updated expiration times (if using relative expiration).
     *
     * @return The complete S3 URL as a String
     * @throws IllegalStateException if credentials are required but not set
     */
    public String toString()
    {
        String pathSegments = getKey();

        String canonicalResource = String.format("/%s/%s", bucket, pathSegments);

        if (!isValidDnsBucketName() || BucketEncoding.PATH.equals(requestedBucketEncoding))
        {
            builder.withHostname(endpoint);
            builder.withPathEncoded(canonicalResource);
        }
        else if (BucketEncoding.VIRTUAL_DNS.equals(requestedBucketEncoding))
        {
            builder.withHostname(bucket);
            builder.withPathEncoded(pathSegments);
        }
        else
        {
            builder.withHostname(bucket + "." + endpoint);
            builder.withPathEncoded(pathSegments);
        }

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
            Instant expireInstant = expireDate.getExpireDate();
            InternalUtils.checkNotNull(expireInstant, "expire instant");
            Map<String, String> params = signParams(expireInstant, canonicalResource, builder);
            builder.addParameters(params);
        }

        String result = builder.toString();
        builder.clearParameters(); //clean for any subsequent calls to toString()
        return result;
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
     * @return path segments separated by '/'
     */
    public String getKey()
    {
        return StringUtilsInternal.join(key, "/");
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

        if (!builder.queryParams.isEmpty())
        {
            stringToSign.append("?");

            for (UrlBuilder.QueryParam queryParam : builder.queryParams)
            {
                stringToSign.append(String.format("%s=%s", queryParam.key, queryParam.value));
            }
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
                mac.init(new SecretKeySpec(key.getBytes(), "HmacSHA1"));
                return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            }
            catch (Exception e)
            {
                throw new RuntimeException(new SignatureException("Failed to generate signature: " + e.getMessage(), e));
            }
        }

    }

}
