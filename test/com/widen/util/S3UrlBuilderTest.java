package com.widen.util;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class S3UrlBuilderTest
{
	private static final Date farFuture = new Date(1522540800000L);

	private static final String awsAccount = "AKIAJKECYSQBZYJDUDSQ";

	private static final String awsPrivateKey = System.getProperty("awsPrivateKey");

    static
    {
        if (awsPrivateKey == null)
        {
            System.err.println("Set system property -DawsPrivateKey to have signature tests pass!");
        }
    }

	@Test
	public void testSimpleBucketKey()
	{
		S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "foo/bar.jpg");

		assertEquals("http://bucketuno.s3.amazonaws.com/foo/bar.jpg", builder.toString());
	}

	@Test
	public void testSimpleSlashPrefixedBucketKey()
	{
		S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "/foo/bar.jpg");

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
    public void testRegionEndointSetting()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "foo/bar.jpg").inRegion(S3UrlBuilder.Region.EU_IRELAND);

        assertEquals("http://bucketuno.s3-eu-west-1.amazonaws.com/foo/bar.jpg", builder.toString());
    }

    @Test
    public void testEndointSetting()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "foo/bar.jpg").withEndpoint("s3clone.example.com").usingBucketInPath();

        assertEquals("http://s3clone.example.com/bucketuno/foo/bar.jpg", builder.toString());
    }

	@Test
	public void testHostnameAndPathStyleStringsAreTheSameSignature()
	{
		S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat.jpeg").expireAt(farFuture).usingCredentials(awsAccount, awsPrivateKey);

		String dns = builder.toString();

		System.out.println(dns);
		assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=fHj68yJqZ1ImRrsgogBHZdb4Ceo%3D", dns);

		String path = builder.usingBucketInPath().toString();

		assertEquals("http://s3.amazonaws.com/urlbuildertests.widen.com/cat.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=fHj68yJqZ1ImRrsgogBHZdb4Ceo%3D", path);
	}

	@Test(expected = IllegalStateException.class)
	public void testExpireWithNoAccountsThrows()
	{
		S3UrlBuilder builder = new S3UrlBuilder("bucket.test.com", "foo.txt").expireIn(1, TimeUnit.HOURS);

		builder.toString();
	}

	@Test
	public void testExpiringAttachmentFilename()
	{
		S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3-public.jpeg").withAttachmentFilename("kitty-cat.jpg").expireAt(farFuture).usingCredentials(awsAccount, awsPrivateKey);

		assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3-public.jpeg?response-content-disposition=attachment%3B%20filename%3D%22kitty-cat.jpg%22&Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=jH%2BRr3TEjvu2Wk7cq9ER7ybdErg%3D", builder.toString());
	}

	@Test(expected = IllegalStateException.class)
	public void testPublicAttachmentFilenameThrows()
	{
		S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3-public.jpeg").withAttachmentFilename("kitty-cat.jpg");

		builder.toString();
	}

	@Test
	public void testHashDoesNotChangeSignature()
	{
		S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3-public.jpeg").usingCredentials(awsAccount, awsPrivateKey).expireAt(farFuture);

		assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3-public.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=C39yfdpfO072isjVyekpC4t1GjQ%3D", builder.toString());

		builder.withFragment("scrollmarker");

		assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3-public.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=C39yfdpfO072isjVyekpC4t1GjQ%3D#scrollmarker", builder.toString());
	}

    @Test
    public void testEncodedCharsInKey()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3 % public.jpeg");

        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3%20%25%20public.jpeg", builder.toString());
    }

	@Test
	public void testNonAsciiCharsInAttachment()
	{
		S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "foo.jpeg").withAttachmentFilename("+ƒoo.jpeg").expireAt(farFuture).usingCredentials(awsAccount, awsPrivateKey);

		assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/foo.jpeg?response-content-disposition=attachment%3B%20filename%3D%22%2Boo.jpeg%22&Signature=eBNNBRl7DlXOjhIb5YSlpR9BPOg%3D&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Expires=1522540800", builder.toString());
	}

}
