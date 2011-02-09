package com.widen.util;

import sun.misc.BASE64Encoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SignatureException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class S3UrlBuilder
{
	private String bucket;

	private List<String> key;

	private Region region = Region.US_STANDARD;

	private BucketEncoding requestedBucketEncoding = BucketEncoding.DNS;

	private ExpireDateHolder expireDate = new ExpireDateHolder();

	private String awsAccount;

	private String awsPrivateKey;

	private final UrlBuilder builder = new UrlBuilder();

	public enum Region
	{
		US_STANDARD("s3.amazonaws.com"),
		US_WEST("s3-us-west-1.amazonaws.com"),
		EU_IRELAND("s3-eu-west-1.amazonaws.com"),
		ASIA_PACIFIC_SINGAPORE("s3-ap-southeast-1.amazonaws.com");

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

	class ExpireDateHolder
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
	}

	public S3UrlBuilder(String bucket, String key)
	{
		withBucket(bucket);

		withKey(key);

		builder.modeFullyQualified();
	}

	public S3UrlBuilder expireIn(long duration, TimeUnit unit)
	{
		checkNotNull(duration, "duration");
		checkNotNull(unit, "unit");

		expireDate.duration = duration;
		expireDate.unit = unit;

		return this;
	}

	public S3UrlBuilder expireAt(Date date)
	{
		expireDate.instant = date;

		return this;
	}

	public S3UrlBuilder usingCredentials(String awsAccount, String awsPrivateKey)
	{
		checkNotNull(awsAccount, "awsAccount");
		checkNotNull(awsPrivateKey, "awsPrivateKey");

		this.awsAccount = awsAccount;
		this.awsPrivateKey = awsPrivateKey;

		return this;
	}

	public S3UrlBuilder inRegion(Region region)
	{
		checkNotNull(region, "region");

		this.region = region;

		return this;
	}

	public S3UrlBuilder withBucket(String bucket)
	{
		checkNotBlank(bucket, "bucket");

		this.bucket = cleanBucketName(bucket);

		return this;
	}

		public S3UrlBuilder withKey(String key)
	{
		checkNotBlank(key, "key");

		this.key = builder.makePathSegments(key);

		return this;
	}

	private String getPathSegments()
	{
		return UrlBuilder.StringUtilsInternal.join(key, "/");
	}

	private String cleanBucketName(String s)
	{
		return s.replace("/", "");
	}

	public S3UrlBuilder usingBucketVirtualHost()
	{
		requestedBucketEncoding = BucketEncoding.VIRTUAL_DNS;
		return S3UrlBuilder.this;
	}

	public S3UrlBuilder usingBucketInPath()
	{
		requestedBucketEncoding = BucketEncoding.PATH;
		return S3UrlBuilder.this;
	}

	public S3UrlBuilder usingBucketInHostname()
	{
		requestedBucketEncoding = BucketEncoding.DNS;
		return S3UrlBuilder.this;
	}

	public S3UrlBuilder downloadFilename(String filename)
	{
		return S3UrlBuilder.this;
	}

	public String toString()
	{
		builder.modeFullyQualified();

		String canonicalizedResource = String.format("/%s/%s", bucket, getPathSegments());

		if (!isValidDnsBucketName() || BucketEncoding.PATH.equals(requestedBucketEncoding))
		{
			builder.withHostname(region.endpoint);
			builder.withPath(canonicalizedResource);
		}
		else if (BucketEncoding.VIRTUAL_DNS.equals(requestedBucketEncoding))
		{
			builder.withHostname(bucket);
			builder.withPath(getPathSegments());
		}
		else
		{
			builder.withHostname(bucket + "." + region.endpoint);
			builder.withPath(getPathSegments());
		}

		Date expireOn = expireDate.getExpireDate();

		if (expireOn != null)
		{
			builder.clearParameters();

			if (UrlBuilder.StringUtilsInternal.isBlank(awsAccount) || UrlBuilder.StringUtilsInternal.isBlank(awsPrivateKey))
			{
				throw new IllegalArgumentException("AWS Account and Private Key must be specified when generating expiring URLs.");
			}

			builder.addParameters(signParams(expireOn, canonicalizedResource));
		}

		return builder.toString();
	}

	public String getKey()
	{
		return UrlBuilder.StringUtilsInternal.join(key, "/");
	}

	public S3UrlBuilder usingSsl()
	{
		builder.usingSsl();

		return this;
	}

	public S3UrlBuilder usingSsl(boolean useSsl)
	{
		builder.usingSsl(useSsl);

		return this;
	}

	public S3UrlBuilder addParameter(String key, Object value)
	{
		builder.addParameter(key, value);

		return this;
	}

	public S3UrlBuilder addParameters(Map<String, ? extends Object> params)
	{
		builder.addParameters(params);

		return this;
	}

	public S3UrlBuilder withFragment(String fragment)
	{
		builder.withFragment(fragment);

		return this;
	}

	public S3UrlBuilder modeProtocolRelative()
	{
		builder.modeProtocolRelative();

		return this;
	}

	public S3UrlBuilder modeFullyQualified()
	{
		builder.modeFullyQualified();

		return this;
	}

	private static void checkNotNull(Object o, String var)
	{
		if (o == null)
		{
			throw new IllegalArgumentException(var + " cannot be null.");
		}
	}

	private static void checkNotBlank(String s, String var)
	{
		if (UrlBuilder.StringUtilsInternal.isBlank(s))
		{
			throw new IllegalArgumentException(var + " cannot be null or empty.");
		}
	}

	private boolean isValidDnsBucketName()
	{
		return AmazonAWSJavaSDKInternal.isValidV2BucketName(bucket);
	}

	/**
	 * http://s3.amazonaws.com/doc/s3-developer-guide/RESTAuthentication.html
	 */
	private Map<String, String> signParams(Date expireTime, String path)
	{
		String expires = String.valueOf(expireTime.getTime() / 1000);

		StringBuilder stringToSign = new StringBuilder();

		stringToSign.append("GET\n"); //http verb
		stringToSign.append("\n"); //content md5
		stringToSign.append("\n"); //content type
		stringToSign.append(expires + "\n");
		//stringToSign.append("\n"); //canonicalizedAmzHeaders
		stringToSign.append(path); //canonicalizedResource

		System.out.println(stringToSign);

		String signature = AmazonAWSJavaSDKInternal.sign(stringToSign.toString(), awsPrivateKey);

		HashMap<String, String> params = new HashMap<String, String>();
		params.put("Signature", signature);
		params.put("Expires", expires);
		params.put("AWSAccessKeyId", awsAccount);

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
		 *
		 * <p>Uses com.sun.BASE64Encoder to avoid external dependencies.
		 */
		private static String sign(String data, String key)
		{
			try
			{
				Mac mac = Mac.getInstance("HmacSHA1");
				mac.init(new SecretKeySpec(key.getBytes(), "HmacSHA1"));
				return new BASE64Encoder().encode(mac.doFinal(data.getBytes("UTF-8")));
			}
			catch (Exception e)
			{
				throw new RuntimeException(new SignatureException("Failed to generate signature: " + e.getMessage(), e));
			}
		}

	}

}
