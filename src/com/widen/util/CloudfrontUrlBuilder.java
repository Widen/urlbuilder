package com.widen.util;

import java.security.PrivateKey;
import java.security.Signature;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class CloudfrontUrlBuilder
{

    private String distributionHostname;

    private String key;

    private boolean ssl;

    private final TrustedSignerCredentials trustedSignerCredentials;

    private String attachmentFilename;

    private String contentType;

    private ExpireDateHolder expireDate = new ExpireDateHolder();

    private Map<String, String> parameters = new LinkedHashMap<String, String>();

    /**
     * Construct a "canned policy" Cloudfront URL; you must set an expire date.
     */
    public CloudfrontUrlBuilder(String distributionHostname, String key, String keyPairId, PrivateKey privateKey)
    {
        this(distributionHostname, key, keyPairId, privateKey, "SunRsaSign");
    }

    /**
     * Construct a "canned policy" Cloudfront URL; you must set an expire date. Use "BC" to use Bouncy Castle as crypto provider when generating SHA1 signature.
     */
    public CloudfrontUrlBuilder(String distributionHostname, String key, String keyPairId, PrivateKey privateKey, String cryptoProvider)
    {
        this.distributionHostname = distributionHostname;
        this.key = key;
        this.trustedSignerCredentials = new TrustedSignerCredentials(keyPairId, privateKey, cryptoProvider);
    }

    public CloudfrontUrlBuilder withDistributionHostname(String hostname)
    {
        this.distributionHostname = hostname;
        return this;
    }

    public CloudfrontUrlBuilder withKey(String key)
    {
        this.key = key;
        return this;
    }

    public CloudfrontUrlBuilder withAttachmentFilename(String attachmentFilename)
    {
        this.attachmentFilename = attachmentFilename;
        return this;
    }

    public CloudfrontUrlBuilder withContentType(String contentType)
    {
        this.contentType = contentType;
        return this;
    }

    public CloudfrontUrlBuilder addParameter(String key, String value)
    {
        parameters.put(key, value);
        return this;
    }

    public CloudfrontUrlBuilder withSsl()
    {
        ssl = true;
        return this;
    }

    /**
     * Time generated link is valid for. Expire time is calculated when
     * #toString() is executed.
     *
     * @param duration
     * @param unit
     */
    public CloudfrontUrlBuilder expireIn(long duration, TimeUnit unit)
    {
        InternalUtils.checkNotNull(duration, "duration");
        InternalUtils.checkNotNull(unit, "unit");

        expireDate.duration = duration;
        expireDate.unit = unit;

        return this;
    }

    /**
     * Set absolute time URL will expire. Time is accurate to seconds.
     * @param date
     */
    public CloudfrontUrlBuilder expireAt(Date date)
    {
        expireDate.instant = date;

        return this;
    }

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

        String cannedPolicy = String.format("{\"Statement\":[{\"Resource\":\"%s\",\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":%s}}}]}", builder.toString(), expireDate.getExpiresUtcSeconds());
        String signature = trustedSignerCredentials.sign(cannedPolicy);

        builder.addParameter("Expires", expireDate.getExpiresUtcSeconds());
        builder.addParameter("Signature", signature, new NoEncodingEncoder());
        builder.addParameter("Key-Pair-Id", trustedSignerCredentials.accessKeyId);

        return builder.toString();
    }

    private class ExpireDateHolder
    {
        long duration;

        TimeUnit unit;

        Date instant;

        Date getExpireDate()
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

            return new Date(futureMillis);
        }

        long getExpiresUtcSeconds()
        {
            Calendar gmt = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            gmt.setTime(getExpireDate());
            return gmt.getTimeInMillis() / 1000;
        }

        boolean isSet()
        {
            return getExpireDate() != null;
        }
    }

    public static class TrustedSignerCredentials
    {
        private final String accessKeyId;

        private final Signature signer;

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

        public String sign(String text)
        {
            try
            {
                signer.update(text.getBytes("UTF-8"));
                byte[] bytes = signer.sign();

                String encodedBytes = Base64.encodeBytes(bytes);
                return encodedBytes.replace("+", "-").replace("=", "_").replace("/", "~");
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

}
