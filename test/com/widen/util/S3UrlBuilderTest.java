package com.widen.util;

import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class S3UrlBuilderTest
{

	@Test
	public void testSimpleBucketKey()
	{
		S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "foo/bar.jpg");

		assertEquals("http://bucketuno.s3.amazonaws.com/foo/bar.jpg", builder.toString());
	}

	@Test
	public void testDnsBucket()
	{
		S3UrlBuilder builder = new S3UrlBuilder("bucketuno.test.com", "foo/bar.jpg").usingBucketInHostname();

		assertEquals("http://bucketuno.test.com.s3.amazonaws.com/foo/bar.jpg", builder.toString());
	}

	@Test
	public void testVirtualHostBucket()
	{
		S3UrlBuilder builder = new S3UrlBuilder("bucketuno.test.com", "foo/bar.jpg").usingBucketVirtualHost();

		assertEquals("http://bucketuno.test.com/foo/bar.jpg", builder.toString());
	}

	@Test
	public void testHostnameAndPathStyleStringsAreTheSameSignature()
	{
		S3UrlBuilder builder = new S3UrlBuilder("uriah2.widencdn.net", "/posta-1280.jpeg").expireAt(new Date(1522540800000L)).usingCredentials("AKIAJKECYSQBZYJDUDSQ", "VpIwQFDNeL1L4EFrnTVgoF5mGWR4QetTaMMNgcuo");

		System.out.println("as dns");

		assertEquals("http://uriah2.widencdn.net.s3.amazonaws.com/posta-1280.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=8voVjBRzgLB09BWa44dkovbX9YA%3D", builder.toString());

		System.out.println("as path");

		assertEquals("http://s3.amazonaws.com/uriah2.widencdn.net/posta-1280.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=8voVjBRzgLB09BWa44dkovbX9YA%3D", builder.usingBucketInPath().toString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testExpireWithNoAccountsThrows()
	{
		S3UrlBuilder builder = new S3UrlBuilder("buckuno.test.com", "fo/bar.jpg").expireIn(1, TimeUnit.HOURS);

		builder.toString();
	}

	@Test
	public void testTemp()
	{
		S3UrlBuilder builder = new S3UrlBuilder("preview.widencdn.net", "demo/7/2/1/7218efdb-26ea-4bc6-a9e3-f42f1dac11a6.pdf").expireIn(1, TimeUnit.DAYS).usingCredentials("AKIAIYQID47OGVJ5LEWA", "nba+J9CArh8BUIaTXpGTtvKHhz5dRQQi31n35/F2");

		System.out.println(builder.toString());
	}

}
