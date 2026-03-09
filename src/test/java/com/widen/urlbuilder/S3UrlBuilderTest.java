package com.widen.urlbuilder;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3UrlBuilderTest
{
    private static final Date farFuture = new Date(1522540800000L);

    private static final String awsAccount = "AKIAJKECYSQBZYJDUDSQ";

    private static final String awsPrivateKey = System.getProperty("awsPrivateKey");

    @Test
    void testSimpleBucketKey()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "foo/bar.jpg");

        assertEquals("http://bucketuno.s3.amazonaws.com/foo/bar.jpg", builder.toString());
    }

    @Test
    void testSimpleSlashPrefixedBucketKey()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "/foo/bar.jpg");

        assertEquals("http://bucketuno.s3.amazonaws.com/foo/bar.jpg", builder.toString());
    }

    @Test
    void testDnsBucket()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno.test.com", "foo/bar.jpg").usingBucketInHostname();

        assertEquals("http://bucketuno.test.com.s3.amazonaws.com/foo/bar.jpg", builder.toString());
    }

    @Test
    void testVirtualHostBucket()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno.test.com", "foo/bar.jpg").usingBucketVirtualHost();

        assertEquals("http://bucketuno.test.com/foo/bar.jpg", builder.toString());
    }

    @Test
    void testRegionEndointSetting()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "foo/bar.jpg").inRegion("eu-west-1");

        assertEquals("http://bucketuno.s3.eu-west-1.amazonaws.com/foo/bar.jpg", builder.toString());
    }

    @Test
    void testEndointSetting()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucketuno", "foo/bar.jpg").withEndpoint("s3clone.example.com").usingBucketInPath();

        assertEquals("http://s3clone.example.com/bucketuno/foo/bar.jpg", builder.toString());
    }

    @Test
    @Disabled("Dependency on a private key")
    void testHostnameAndPathStyleStringsAreTheSameSignature()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat.jpeg").expireAt(farFuture).usingCredentials(awsAccount, awsPrivateKey);

        String dns = builder.toString();

        System.out.println(dns);
        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=fHj68yJqZ1ImRrsgogBHZdb4Ceo%3D", dns);

        String path = builder.usingBucketInPath().toString();

        assertEquals("http://s3.amazonaws.com/urlbuildertests.widen.com/cat.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=fHj68yJqZ1ImRrsgogBHZdb4Ceo%3D", path);
    }

    @Test
    void testExpireWithNoAccountsThrows()
    {
        S3UrlBuilder builder = new S3UrlBuilder("bucket.test.com", "foo.txt").expireIn(1, TimeUnit.HOURS);

        assertThrows(IllegalStateException.class, builder::toString);
    }

    @Test
    @Disabled("Dependency on a private key")
    void testExpiringAttachmentFilename()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3-public.jpeg").withAttachmentFilename("kitty-cat.jpg").expireAt(farFuture).usingCredentials(awsAccount, awsPrivateKey);

        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3-public.jpeg?response-content-disposition=attachment%3B%20filename%3D%22kitty-cat.jpg%22&Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=jH%2BRr3TEjvu2Wk7cq9ER7ybdErg%3D", builder.toString());
    }

    @Test
    void testPublicAttachmentFilenameThrows()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3-public.jpeg").withAttachmentFilename("kitty-cat.jpg");

        assertThrows(IllegalStateException.class, builder::toString);
    }

    @Test
    @Disabled("Dependency on a private key")
    void testHashDoesNotChangeSignature()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3-public.jpeg").usingCredentials(awsAccount, awsPrivateKey).expireAt(farFuture);

        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3-public.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=C39yfdpfO072isjVyekpC4t1GjQ%3D", builder.toString());

        builder.withFragment("scrollmarker");

        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3-public.jpeg?Expires=1522540800&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Signature=C39yfdpfO072isjVyekpC4t1GjQ%3D#scrollmarker", builder.toString());
    }

    @Test
    void testEncodedCharsInKey()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "cat3 % public.jpeg");

        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/cat3%20%25%20public.jpeg", builder.toString());
    }

    @Test
    @Disabled("Dependency on a private key")
    void testNonAsciiCharsInAttachment()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "foo.jpeg").withAttachmentFilename("+ƒoo.jpeg").expireAt(farFuture).usingCredentials(awsAccount, awsPrivateKey);

        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/foo.jpeg?response-content-disposition=attachment%3B%20filename%3D%22%2Boo.jpeg%22%3B%20filename*%3DUTF-8%27%27%252B%25C6%2592oo.jpeg&Signature=eBNNBRl7DlXOjhIb5YSlpR9BPOg%3D&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Expires=1522540800", builder.toString());
    }

    @Test
    @Disabled("Dependency on a private key")
    void testOnlyNonAsciiCharsInAttachment()
    {
        S3UrlBuilder builder = new S3UrlBuilder("urlbuildertests.widen.com", "foo.jpeg").withAttachmentFilename("ƒƒƒƒƒ").expireAt(farFuture).usingCredentials(awsAccount, awsPrivateKey);

        assertEquals("http://urlbuildertests.widen.com.s3.amazonaws.com/foo.jpeg?Signature=E2ee6O2hY968RWZKkYTE29ZNCDk%3D&AWSAccessKeyId=AKIAJKECYSQBZYJDUDSQ&Expires=1522540800", builder.toString());
    }

}
