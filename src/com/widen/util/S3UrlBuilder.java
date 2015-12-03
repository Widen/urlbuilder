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
package com.widen.util;

import java.security.SignatureException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for constructing syntactically correct S3 URLs using a fluent method-chaining API.
 * It strives simply to be more robust then manually constructing URLs by string concatenation.
 *
 * <p><b>Typical usage:</b>
 * <code>new S3UrlBuilder("urlbuildertests.widen.com", "cat.jpeg").expireIn(1, TimeUnit.HOURS).usingCredentials(awsKey, awsPrivateKey).toString()</code>
 * <b>produces</b> <code>http://urlbuildertests.widen.com.s3.amazonaws.com/cat.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=fHj68yJqZ1ImRrsgogBHZdb4Ceo%3D</code>
 *
 * <p>The methods {@link #usingBucketVirtualHost}, {@link #usingBucketInPath}, {@link #usingBucketInHostname},
 * control where the bucket name is encoded into the URL.
 *
 * @version 0.9.3
 */
public class S3UrlBuilder
{
	private String bucket;

	private List<String> key;

    private String endpoint = Region.US_STANDARD.endpoint;

	private BucketEncoding requestedBucketEncoding = BucketEncoding.DNS;

	private ExpireDateHolder expireDate = new ExpireDateHolder();

	private String attachmentFilename;

	private String awsKey;

	private String awsPrivateKey;

	private String awsSessionToken;

	private final UrlBuilder builder = new UrlBuilder();

	/**
	 * Available S3 Regions.
	 *
	 * When <code>PATH</code> encoding is used the
	 * the region must be correctly set with the location of the bucket.
	 */
	public enum Region
	{
		US_STANDARD("s3.amazonaws.com"),
		US_WEST_NORTHERN_CALIFORNIA("s3-us-west-1.amazonaws.com"),
		US_WEST_OREGON("s3-us-west-2.amazonaws.com"),
		EU_IRELAND("s3-eu-west-1.amazonaws.com"),
		ASIA_PACIFIC_SINGAPORE("s3-ap-southeast-1.amazonaws.com"),
		ASIA_PACIFIC_SYDNEY("s3-ap-southeast-2.amazonaws.com"),
		ASIA_PACIFIC_TOKYO("s3-ap-northeast-1.amazonaws.com"),
        SOUTH_AMERICA_SAO_PAULO("s3-sa-east-1.amazonaws.com");

		String endpoint;

		Region(String endpoint)
		{
			this.endpoint = endpoint;
		}
	}

	private enum BucketEncoding
	{
		DNS,
		VIRTUAL_DNS,
		PATH
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

		boolean isSet()
		{
			return getExpireDate() != null;
		}
	}

	/**
	 * Easily, and correctly, construct URLs for S3.
	 *
	 * @param bucket
	 *      name as String
	 * @param key
	 *      name as String
	 *
	 * @throws IllegalArgumentException if bucket or key is null
	 */
	public S3UrlBuilder(String bucket, String key)
	{
		withBucket(bucket);

		withKey(key);

		builder.modeFullyQualified();
	}

	/**
	 * Time generated link is valid for. Expire time is calculated when
	 * #toString() is executed.
	 *
	 * @param duration
	 * @param unit
	 */
	public S3UrlBuilder expireIn(long duration, TimeUnit unit)
	{
		InternalUtils.checkNotNull(duration, "duration");
		InternalUtils.checkNotNull(unit, "unit");

		expireDate.duration = duration;
		expireDate.unit = unit;

		return this;
	}

	/**
	 * Set absolute time URL will expire. Time is accurate to seconds.
	 *
	 * @param date
	 */
	public S3UrlBuilder expireAt(Date date)
	{
		expireDate.instant = date;

		return this;
	}

	/**
	 * Set AWS account and private key.
	 * Required when a signed URL is generated.
	 *
	 * @param awsKey
	 * @param awsPrivateKey
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
	 * Set AWS account, private key and STS (Security Token Service) token
	 * Required when a signed URL is generated.
	 *
	 * @param awsKey
	 * @param awsPrivateKey
	 * @throws IllegalArgumentException if awsKey or awsPrivateKey is null
	 */
	public S3UrlBuilder usingCredentials(String awsKey, String awsPrivateKey, String awsSessionToken)
	{
		usingCredentials(awsKey, awsPrivateKey);
		InternalUtils.checkNotNull(awsSessionToken, "awsSessionToken");

		this.awsSessionToken = awsSessionToken;
		return this;
	}

    /**
     * Set a custom endpoint for the S3 service.<br />
     *
     * When using Amazon services you should use #inRegion() instead of this method.
     * This method is only necessary when using S3 API compatible services like <a href="http://ceph.com/">CEPH</a>
     *
     * @param endpoint fully-quantified DNS hostname of S3 service
     */
    public S3UrlBuilder withEndpoint(String endpoint)
    {
        this.endpoint = endpoint;

        return this;
    }

	/**
	 * Set Region of bucket. Default Region is US_STANDARD.
	 *
	 * @param region
	 * @throws IllegalArgumentException if region is null
	 *
	 * @see <a href="http://docs.amazonwebservices.com/AmazonS3/latest/dev/LocationSelection.html">S3 Location Selection Docs</a>
	 */
	public S3UrlBuilder inRegion(Region region)
	{
		InternalUtils.checkNotNull(region, "region");

		endpoint = region.endpoint;

		return this;
	}

	/**
	 * Reset S3 bucket to new value
	 *
	 * @param bucket
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
	 * @param key
	 * @throws IllegalArgumentException if key is blank
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
	 */
	public S3UrlBuilder usingBucketVirtualHost()
	{
		requestedBucketEncoding = BucketEncoding.VIRTUAL_DNS;
		return S3UrlBuilder.this;
	}

	/**
	 * Set URL generation to encode bucket into path.
	 */
	public S3UrlBuilder usingBucketInPath()
	{
		requestedBucketEncoding = BucketEncoding.PATH;
		return S3UrlBuilder.this;
	}

	/**
	 * Set URL generation to prefix bucket to hostname ".s3.amazonaws.com"
	 *
	 * This is the default generation mode.
	 */
	public S3UrlBuilder usingBucketInHostname()
	{
		requestedBucketEncoding = BucketEncoding.DNS;
		return S3UrlBuilder.this;
	}

	/**
	 * Construct URL using specific S3 conventions.
	 *
	 * @return
	 *      Generated URL as String
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

			builder.addParameter("response-content-disposition", String.format("attachment; filename=\"%s\"", attachmentFilename));
		}

		if (expireDate.isSet())
		{
			canSign();

			Map<String, String> params = signParams(expireDate.getExpireDate(), canonicalResource, builder);

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
	 * @return
	 *     path segments separated by '/'
	 */
	public String getKey()
	{
		return StringUtilsInternal.join(key, "/");
	}

	/**
	 * Set generated URL to use the "https" scheme.
	 */
	public S3UrlBuilder usingSsl()
	{
		builder.usingSsl();

		return this;
	}

	/**
	 * Set generated URL to use "https" scheme.
	 *
	 * @param useSsl
	 *      true to use "https" or false to use "http"
	 */
	public S3UrlBuilder usingSsl(boolean useSsl)
	{
		builder.usingSsl(useSsl);

		return this;
	}

	/**
	 * Add 'hash' fragment to generated URL. Value does not modify S3 signature.
	 *
	 * @param fragment
	 */
	public S3UrlBuilder withFragment(String fragment)
	{
		builder.withFragment(fragment);

		return this;
	}

	/**
	 * Set generation mode to Protocol Relative; e.g. <code>"//my.host.com/foo/bar.html"</code>
	 */
	public S3UrlBuilder modeProtocolRelative()
	{
		builder.modeProtocolRelative();

		return this;
	}

	/**
	 * Set generation mode to Fully Qualified. This is the default mode; e.g. <code>"http://my.host.com/foo/bar.html"</code>
	 *
	 * <p>Default mode.
	 */
	public S3UrlBuilder modeFullyQualified()
	{
		builder.modeFullyQualified();

		return this;
	}

	/**
	 * Hint for "attachment" filename. Informs S3 to add "Content-Disposition; attachment" HTTP header.
	 *
	 * @param filename
	 *      the filename for the attachment; most browsers will raise a "Save File As..." dialog, or immediately save file
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
	 * http://s3.amazonaws.com/doc/s3-developer-guide/RESTAuthentication.html
	 */
	private Map<String, String> signParams(Date expireTime, String canonicalResource, UrlBuilder builder)
	{
		String expires = String.valueOf(expireTime.getTime() / 1000);

		StringBuilder stringToSign = new StringBuilder();

		stringToSign.append("GET\n"); //http verb
		stringToSign.append("\n"); //content md5
		stringToSign.append("\n"); //content type
		stringToSign.append(expires + "\n");
		stringToSign.append(canonicalResource);

		if (!builder.queryParams.isEmpty())
		{
			stringToSign.append("?");
		}

		for (UrlBuilder.QueryParam queryParam : builder.queryParams)
		{
			stringToSign.append(String.format("%s=%s", queryParam.key, queryParam.value));
		}

		if (awsSessionToken != null)
		{
			stringToSign.append("x-amz-security-token=").append(awsSessionToken);
		}

		//System.err.println("sign text for " + canonicalResource + "\n" + stringToSign);

		String signature = AmazonAWSJavaSDKInternal.sign(stringToSign.toString(), awsPrivateKey);

		HashMap<String, String> params = new HashMap<String, String>();
		params.put("Signature", signature);
		params.put("Expires", expires);
		params.put("AWSAccessKeyId", awsKey);

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
	 * Utilities for working with Amazon S3 bucket names, such as validation and
	 * checked to see if they are compatible with DNS addressing.
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
				throw new IllegalArgumentException("Bucket name cannot be null");

			if (!bucketName.toLowerCase().equals(bucketName))
				throw new IllegalArgumentException("Bucket name should not contain uppercase characters");

			if (bucketName.contains("_"))
				throw new IllegalArgumentException("Bucket name should not contain '_'");

			if (bucketName.contains("!") || bucketName.contains("@") || bucketName.contains("#"))
				throw new IllegalArgumentException("Bucket name contains illegal characters");

			if (bucketName.length() < 3 || bucketName.length() > 63)
				throw new IllegalArgumentException("Bucket name should be between 3 and 63 characters long");

			if (bucketName.endsWith("-") || bucketName.endsWith("."))
				throw new IllegalArgumentException("Bucket name should not end with '-' or '.'");

			if (bucketName.contains(".."))
				throw new IllegalArgumentException("Bucket name should not contain two adjacent periods");

			if (bucketName.contains("-.") ||
					bucketName.contains(".-"))
				throw new IllegalArgumentException("Bucket name should not contain dashes next to periods");
		}

		/**
		 * Returns true if the specified bucket name can be addressed using V2,
		 * virtual host style, addressing. Otherwise, returns false indicating that
		 * the bucket must be addressed using V1, path style, addressing.
		 *
		 * @param bucketName The name of the bucket to check.
		 * @return True if the specified bucket name can be addressed in V2, virtual
		 *         host style, addressing otherwise false if V1, path style,
		 *         addressing is required.
		 */
		public static boolean isValidV2BucketName(String bucketName)
		{
			if (bucketName == null) return false;

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
				return Base64.encodeBytes(mac.doFinal(data.getBytes("UTF-8")));
			}
			catch (Exception e)
			{
				throw new RuntimeException(new SignatureException("Failed to generate signature: " + e.getMessage(), e));
			}
		}

	}

}
