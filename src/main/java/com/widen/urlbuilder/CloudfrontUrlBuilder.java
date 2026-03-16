package com.widen.urlbuilder;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for constructing signed Amazon CloudFront URLs using a fluent method-chaining API.
 * 
 * <p>This builder creates "canned policy" CloudFront signed URLs that allow temporary access to
 * private CloudFront distributions. The URLs include cryptographic signatures generated using
 * RSA-SHA1 with your CloudFront trusted signer credentials.
 * 
 * <p><b>Typical usage:</b>
 * <pre>{@code
 * PrivateKey privateKey = CloudfrontPrivateKeyUtils.fromPemString(pemString);
 * 
 * String signedUrl = new CloudfrontUrlBuilder("d123.cloudfront.net", "/videos/movie.mp4", "AKIAEXAMPLE", privateKey)
 *     .withSsl()
 *     .expireIn(1, TimeUnit.HOURS)
 *     .toString();
 * }</pre>
 * 
 * <p><b>With attachment filename:</b>
 * <pre>{@code
 * String signedUrl = new CloudfrontUrlBuilder("d123.cloudfront.net", "/docs/report.pdf", keyPairId, privateKey)
 *     .withSsl()
 *     .withAttachmentFilename("quarterly-report.pdf")
 *     .expireAt(expirationDate)
 *     .toString();
 * }</pre>
 * 
 * <p>The generated URL includes three signing parameters:
 * <ul>
 *   <li>{@code Expires} - Unix timestamp when the URL expires</li>
 *   <li>{@code Signature} - RSA-SHA1 signature of the canned policy, base64 encoded with CloudFront-safe characters</li>
 *   <li>{@code Key-Pair-Id} - The CloudFront key pair ID used for signing</li>
 * </ul>
 * 
 * <p>This class uses the generic {@link UrlSigner} mechanism internally for URL signing.
 * 
 * @see CloudfrontPrivateKeyUtils
 * @see UrlSigner
 * @since 1.0.0
 */
public class CloudfrontUrlBuilder
{

    private String distributionHostname;

    private String key;

    private boolean ssl;

    private final TrustedSignerCredentials trustedSignerCredentials;

    private String attachmentFilename;

    private String contentType;

    private final ExpireDateHolder expireDate = new ExpireDateHolder();

    private final Map<String, String> parameters = new LinkedHashMap<>();

    /**
     * Constructs a CloudFront URL builder for creating "canned policy" signed URLs.
     * 
     * <p>Uses the default "SunRsaSign" crypto provider for RSA-SHA1 signature generation.
     * 
     * @param distributionHostname The CloudFront distribution hostname (e.g., "d123.cloudfront.net")
     * @param key The path to the resource (e.g., "/videos/movie.mp4")
     * @param keyPairId The CloudFront key pair ID associated with your trusted signer
     * @param privateKey The RSA private key for signing
     * @see #CloudfrontUrlBuilder(String, String, String, PrivateKey, String)
     */
    public CloudfrontUrlBuilder(String distributionHostname, String key, String keyPairId, PrivateKey privateKey)
    {
        this(distributionHostname, key, keyPairId, privateKey, "SunRsaSign");
    }

    /**
     * Constructs a CloudFront URL builder for creating "canned policy" signed URLs with a custom crypto provider.
     * 
     * <p>Use "BC" to use Bouncy Castle as the crypto provider when generating SHA1 signatures.
     * This may be necessary in environments where the default SunRsaSign provider is not available.
     * 
     * @param distributionHostname The CloudFront distribution hostname (e.g., "d123.cloudfront.net")
     * @param key The path to the resource (e.g., "/videos/movie.mp4")
     * @param keyPairId The CloudFront key pair ID associated with your trusted signer
     * @param privateKey The RSA private key for signing
     * @param cryptoProvider The JCE crypto provider name (e.g., "SunRsaSign" or "BC" for Bouncy Castle)
     */
    public CloudfrontUrlBuilder(String distributionHostname, String key, String keyPairId, PrivateKey privateKey, String cryptoProvider)
    {
        this.distributionHostname = distributionHostname;
        this.key = key;
        this.trustedSignerCredentials = new TrustedSignerCredentials(keyPairId, privateKey, cryptoProvider);
    }

    /**
     * Sets the CloudFront distribution hostname.
     * 
     * @param hostname The distribution hostname (e.g., "d123.cloudfront.net")
     * @return This builder for method chaining
     */
    public CloudfrontUrlBuilder withDistributionHostname(String hostname)
    {
        this.distributionHostname = hostname;
        return this;
    }

    /**
     * Sets the resource key (path) within the CloudFront distribution.
     * 
     * @param key The path to the resource (e.g., "/videos/movie.mp4")
     * @return This builder for method chaining
     */
    public CloudfrontUrlBuilder withKey(String key)
    {
        this.key = key;
        return this;
    }

    /**
     * Sets a filename hint for Content-Disposition header.
     * 
     * <p>When set, CloudFront will include a {@code response-content-disposition} parameter
     * that instructs the browser to download the file with the specified filename rather
     * than displaying it inline.
     * 
     * @param attachmentFilename The suggested filename for downloads
     * @return This builder for method chaining
     */
    public CloudfrontUrlBuilder withAttachmentFilename(String attachmentFilename)
    {
        this.attachmentFilename = attachmentFilename;
        return this;
    }

    /**
     * Sets a content type hint for the response.
     * 
     * <p>When set, CloudFront will include a {@code response-content-type} parameter
     * that overrides the Content-Type header in the response.
     * 
     * @param contentType The MIME type for the response (e.g., "application/pdf")
     * @return This builder for method chaining
     */
    public CloudfrontUrlBuilder withContentType(String contentType)
    {
        this.contentType = contentType;
        return this;
    }

    /**
     * Adds a custom query parameter to the URL.
     * 
     * <p>Custom parameters are included in the signed URL and will be part of the
     * signature calculation.
     * 
     * @param key The parameter name
     * @param value The parameter value
     * @return This builder for method chaining
     */
    public CloudfrontUrlBuilder addParameter(String key, String value)
    {
        parameters.put(key, value);
        return this;
    }

    /**
     * Enables HTTPS for the generated URL.
     * 
     * <p>When enabled, the URL will use the "https" scheme instead of "http".
     * 
     * @return This builder for method chaining
     */
    public CloudfrontUrlBuilder withSsl()
    {
        ssl = true;
        return this;
    }

    /**
     * Sets the relative duration until the URL expires.
     * 
     * <p>The actual expiration time is calculated when {@link #toString()} is called,
     * allowing the same builder to generate URLs with fresh expiration times.
     *
     * @param duration The duration value
     * @param unit The time unit for the duration
     * @return This builder for method chaining
     * @throws NullPointerException if duration or unit is null
     */
    public CloudfrontUrlBuilder expireIn(long duration, TimeUnit unit)
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
     * @deprecated Use {@link #expireAt(Instant)} instead. This method will be removed in a future version.
     */
    @Deprecated
    public CloudfrontUrlBuilder expireAt(Date date)
    {
        expireDate.instant = date.toInstant();
        return this;
    }

    /**
     * Sets the absolute expiration time for the URL using a Java 8+ Instant.
     * 
     * <p>The expiration time is accurate to seconds (nanoseconds are truncated).
     * 
     * <p>Example usage:
     * <pre>{@code
     * builder.expireAt(Instant.now().plus(Duration.ofHours(1)));
     * }</pre>
     * 
     * @param instant The absolute instant when the URL should expire
     * @return This builder for method chaining
     * @throws IllegalArgumentException if instant is null
     * @since 3.1.0
     */
    public CloudfrontUrlBuilder expireAt(Instant instant)
    {
        InternalUtils.checkNotNull(instant, "instant");
        expireDate.instant = instant;
        return this;
    }

    /**
     * Generates the signed CloudFront URL.
     * 
     * <p>This method constructs the complete URL including:
     * <ul>
     *   <li>The distribution hostname and resource path</li>
     *   <li>Any custom parameters and content disposition/type headers</li>
     *   <li>CloudFront signing parameters (Expires, Signature, Key-Pair-Id)</li>
     * </ul>
     * 
     * <p>The URL is signed using the {@link UrlSigner} mechanism with the
     * trusted signer credentials provided at construction time.
     * 
     * @return The fully-qualified signed CloudFront URL
     * @throws NullPointerException if expiration date has not been set
     */
    @Override
    public String toString()
    {
        InternalUtils.checkNotNull(expireDate.getExpireDate(), "Expire date");

        UrlBuilder builder = new UrlBuilder();

        builder.withHostname(distributionHostname);
        builder.withPath(key);
        builder.usingSsl(ssl);
        builder.addParameters(parameters);
        builder.modeFullyQualified();

        if (StringUtilsInternal.isNotBlank(attachmentFilename))
        {
            builder.addParameter("response-content-disposition", HttpUtils.createContentDispositionHeader("attachment", attachmentFilename));
        }

        if(StringUtilsInternal.isNotBlank(contentType))
        {
            builder.addParameter("response-content-type", contentType);
        }

        builder.usingUrlSigner(this::signUrl);

        return builder.toString();
    }

    /**
     * Signs the URL for CloudFront canned policy authentication.
     * This method implements the UrlSigner functional interface.
     *
     * @param context The signing context containing the unsigned URL
     * @return Map of CloudFront signing parameters (Expires, Signature, Key-Pair-Id)
     */
    private Map<String, String> signUrl(UrlSigner.SigningContext context)
    {
        String cannedPolicy = String.format("{\"Statement\":[{\"Resource\":\"%s\",\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":%s}}}]}", context.getUrl(), expireDate.getExpiresUtcSeconds());
        String signature = trustedSignerCredentials.sign(cannedPolicy);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("Expires", String.valueOf(expireDate.getExpiresUtcSeconds()));
        params.put("Signature", signature);
        params.put("Key-Pair-Id", trustedSignerCredentials.accessKeyId);
        return params;
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

        long getExpiresUtcSeconds()
        {
            Instant date = getExpireDate();
            InternalUtils.checkNotNull(date, "expire date");
            return date.getEpochSecond();
        }
    }

    /**
     * Holds the CloudFront trusted signer credentials and provides signature generation.
     * 
     * <p>This class encapsulates the RSA-SHA1 signing process used by CloudFront for
     * authenticating canned policy URLs. The signature is:
     * <ol>
     *   <li>Generated using SHA1WithRSA algorithm</li>
     *   <li>Base64 encoded</li>
     *   <li>Modified with CloudFront-safe character replacements (+ → -, = → _, / → ~)</li>
     * </ol>
     */
    public static class TrustedSignerCredentials
    {
        private final String accessKeyId;

        private final Signature signer;

        /**
         * Creates trusted signer credentials for CloudFront URL signing.
         * 
         * @param accessKeyId The CloudFront key pair ID
         * @param privateKey The RSA private key for signing
         * @param cryptoProvider The JCE crypto provider name
         * @throws RuntimeException if the crypto provider or algorithm is not available
         */
        public TrustedSignerCredentials(String accessKeyId, PrivateKey privateKey, String cryptoProvider)
        {
            this.accessKeyId = accessKeyId;

            try
            {
                signer = Signature.getInstance("SHA1WithRSA", cryptoProvider);
                signer.initSign(privateKey);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        /**
         * Signs the given text using RSA-SHA1 and returns a CloudFront-compatible signature.
         * 
         * <p>The signature process:
         * <ol>
         *   <li>Sign the UTF-8 encoded text using RSA-SHA1</li>
         *   <li>Base64 encode the signature bytes</li>
         *   <li>Replace characters not safe for CloudFront URLs:
         *       <ul>
         *         <li>{@code +} is replaced with {@code -}</li>
         *         <li>{@code =} is replaced with {@code _}</li>
         *         <li>{@code /} is replaced with {@code ~}</li>
         *       </ul>
         *   </li>
         * </ol>
         * 
         * @param text The text to sign (typically the CloudFront canned policy JSON)
         * @return The CloudFront-safe base64-encoded signature
         * @throws RuntimeException if signing fails
         */
        public String sign(String text)
        {
            try
            {
                signer.update(text.getBytes(StandardCharsets.UTF_8));
                byte[] bytes = signer.sign();

                String encodedBytes = Base64.getEncoder().encodeToString(bytes);
                return encodedBytes.replace("+", "-").replace("=", "_").replace("/", "~");
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

}
