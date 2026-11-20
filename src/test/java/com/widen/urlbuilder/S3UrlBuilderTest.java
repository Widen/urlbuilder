package com.widen.urlbuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3UrlBuilderTest
{
    private static final Instant farFuture = Instant.ofEpochSecond(1522540800L);

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
        S3UrlBuilder builder = new S3UrlBuilder("bucket.test.com", "foo.txt").expireIn(Duration.ofHours(1));

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

    @Test
    void testSignatureCoversAllResponseParameters() throws Exception
    {
        String secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

        S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
            .withAttachmentFilename("a.pdf")
            .withContentType("application/pdf")
            .expireAt(Instant.ofEpochSecond(1522540800L))
            .usingCredentials("AKIAIOSFODNN7EXAMPLE", secretKey);

        String stringToSign = "GET\n\n\n1522540800\n/test-bucket/test-key.txt"
            + "?response-content-disposition=attachment; filename=\"a.pdf\""
            + "&response-content-type=application/pdf";

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String expectedSignature = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

        String signature = URLDecoder.decode(extractParameter(builder.toString(), "Signature"), "UTF-8");

        assertEquals(expectedSignature, signature);
    }

    @Test
    void testFailedToStringLeavesNoParametersBehind()
    {
        S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
            .withContentType("application/pdf")
            .expireAt(Instant.ofEpochSecond(1522540800L));

        assertThrows(IllegalStateException.class, builder::toString);

        String url = builder.usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY").toString();

        assertEquals(url.indexOf("response-content-type="), url.lastIndexOf("response-content-type="),
            "response-content-type should appear once");
    }

    @Nested
    class ExpireAtDateTests
    {
        @Test
        void testExpireAtDateSetsCorrectExpiration()
        {
            Date fixedDate = new Date(1522540800000L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireAt(fixedDate)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.contains("Expires=1522540800"), "URL should contain correct Expires value from Date");
        }

        @Test
        void testExpireAtDateReturnsBuilderForChaining()
        {
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt");
            
            S3UrlBuilder result = builder.expireAt(new Date(1522540800000L));
            
            assertEquals(builder, result, "expireAt(Date) should return the same builder instance for chaining");
        }

        @Test
        void testExpireAtDateProducesConsistentUrls()
        {
            Date fixedDate = new Date(1522540800000L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireAt(fixedDate)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url1 = builder.toString();
            String url2 = builder.toString();
            
            assertEquals(url1, url2, "Multiple toString() calls with expireAt(Date) should produce identical URLs");
        }

        @Test
        void testExpireAtDateWithSsl()
        {
            Date fixedDate = new Date(1522540800000L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .usingSsl()
                .expireAt(fixedDate)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.startsWith("https://"), "URL should use HTTPS");
            assertTrue(url.contains("Expires=1522540800"), "URL should contain correct Expires value");
        }

        @Test
        void testExpireAtDateWithRegion()
        {
            Date fixedDate = new Date(1522540800000L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .inRegion("eu-west-1")
                .expireAt(fixedDate)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.contains("s3.eu-west-1.amazonaws.com"), "URL should contain regional endpoint");
            assertTrue(url.contains("Expires=1522540800"), "URL should contain correct Expires value");
        }
    }

    @Nested
    class ExpireAtInstantTests
    {
        @Test
        void testExpireAtInstantSetsCorrectExpiration()
        {
            Instant fixedInstant = Instant.ofEpochSecond(1522540800L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireAt(fixedInstant)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.contains("Expires=1522540800"), "URL should contain correct Expires value from Instant");
        }

        @Test
        void testExpireAtInstantReturnsBuilderForChaining()
        {
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt");
            
            S3UrlBuilder result = builder.expireAt(Instant.ofEpochSecond(1522540800L));
            
            assertEquals(builder, result, "expireAt(Instant) should return the same builder instance for chaining");
        }

        @Test
        void testExpireAtInstantProducesConsistentUrls()
        {
            Instant fixedInstant = Instant.ofEpochSecond(1522540800L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireAt(fixedInstant)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url1 = builder.toString();
            String url2 = builder.toString();
            
            assertEquals(url1, url2, "Multiple toString() calls with expireAt(Instant) should produce identical URLs");
        }

        @Test
        void testExpireAtInstantWithNullThrows()
        {
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt");
            
            assertThrows(IllegalArgumentException.class, () -> builder.expireAt((Instant) null),
                "expireAt(Instant) should throw IllegalArgumentException for null input");
        }

        @Test
        void testExpireAtInstantWithSsl()
        {
            Instant fixedInstant = Instant.ofEpochSecond(1522540800L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .usingSsl()
                .expireAt(fixedInstant)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.startsWith("https://"), "URL should use HTTPS");
            assertTrue(url.contains("Expires=1522540800"), "URL should contain correct Expires value");
        }

        @Test
        void testExpireAtInstantWithRegion()
        {
            Instant fixedInstant = Instant.ofEpochSecond(1522540800L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .inRegion("us-west-2")
                .expireAt(fixedInstant)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.contains("s3.us-west-2.amazonaws.com"), "URL should contain regional endpoint");
            assertTrue(url.contains("Expires=1522540800"), "URL should contain correct Expires value");
        }

        @Test
        void testExpireAtInstantWithFutureTime()
        {
            Instant futureInstant = Instant.now().plus(1, ChronoUnit.HOURS);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireAt(futureInstant)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.contains("Expires=" + futureInstant.getEpochSecond()), 
                "URL should contain the future Instant's epoch seconds");
        }
    }

    @Nested
    class ExpireAtDateAndInstantEquivalenceTests
    {
        @Test
        void testDateAndInstantProduceSameExpiration()
        {
            long epochMillis = 1522540800000L;
            Date date = new Date(epochMillis);
            Instant instant = Instant.ofEpochMilli(epochMillis);
            
            S3UrlBuilder builderWithDate = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireAt(date)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            S3UrlBuilder builderWithInstant = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireAt(instant)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String urlWithDate = builderWithDate.toString();
            String urlWithInstant = builderWithInstant.toString();
            
            // URLs should be identical since signature depends on expires
            assertEquals(urlWithDate, urlWithInstant,
                "Date and Instant with same epoch time should produce identical URLs");
        }

        @Test
        void testInstantReplacesEarlierExpireIn()
        {
            Instant fixedInstant = Instant.ofEpochSecond(1522540800L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireIn(Duration.ofHours(1))
                .expireAt(fixedInstant)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.contains("Expires=1522540800"), 
                "the later expireAt(Instant) should replace expireIn");
        }

        @Test
        void testDateReplacesEarlierExpireIn()
        {
            Date fixedDate = new Date(1522540800000L);
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireIn(Duration.ofHours(1))
                .expireAt(fixedDate)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            assertTrue(url.contains("Expires=1522540800"),
                "the later expireAt(Date) should replace expireIn");
        }
    }

    @Nested
    class ExpireInTests
    {
        @Test
        void testExpireInWithHours()
        {
            long beforeTime = Instant.now().getEpochSecond();
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireIn(Duration.ofHours(1))
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            long afterTime = Instant.now().getEpochSecond();
            
            String expiresParam = extractParameter(url, "Expires");
            long expiresValue = Long.parseLong(expiresParam);
            
            long expectedMinExpires = beforeTime + 3600;
            long expectedMaxExpires = afterTime + 3600;
            
            assertTrue(expiresValue >= expectedMinExpires && expiresValue <= expectedMaxExpires,
                "Expires should be approximately 1 hour from now");
        }

        @Test
        void testExpireInWithTimeUnit()
        {
            long beforeTime = Instant.now().getEpochSecond();
            
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireIn(30, TimeUnit.MINUTES)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            
            String url = builder.toString();
            
            long afterTime = Instant.now().getEpochSecond();
            
            String expiresParam = extractParameter(url, "Expires");
            long expiresValue = Long.parseLong(expiresParam);
            
            long expectedMinExpires = beforeTime + 1800;
            long expectedMaxExpires = afterTime + 1800;
            
            assertTrue(expiresValue >= expectedMinExpires && expiresValue <= expectedMaxExpires,
                "Expires should be approximately 30 minutes from now");
        }

        @Test
        void testExpireInWithTimeUnitKeepsDurationsLongerThanLongNanoseconds()
        {
            long fourHundredYears = Duration.ofDays(400L * 365).getSeconds();
            long beforeTime = Instant.now().getEpochSecond();

            String url = new S3UrlBuilder("test-bucket", "test-key.txt")
                .expireIn(400L * 365, TimeUnit.DAYS)
                .usingCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                .toString();

            long afterTime = Instant.now().getEpochSecond();
            long expiresValue = Long.parseLong(extractParameter(url, "Expires"));

            assertTrue(expiresValue >= beforeTime + fourHundredYears && expiresValue <= afterTime + fourHundredYears,
                "Expires should be approximately 400 years from now");
        }

        @Test
        void testExpireInReturnsBuilderForChaining()
        {
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt");
            
            S3UrlBuilder result = builder.expireIn(Duration.ofHours(1));
            
            assertEquals(builder, result, "expireIn should return the same builder instance for chaining");
        }

        @Test
        void testExpireInRejectsNonPositiveDuration()
        {
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "test-key.txt");

            assertThrows(IllegalArgumentException.class, () -> builder.expireIn(Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> builder.expireIn(Duration.ofHours(-1)));
            assertThrows(IllegalArgumentException.class, () -> builder.expireIn(0, TimeUnit.HOURS));
            assertThrows(IllegalArgumentException.class, () -> builder.expireIn(-1, TimeUnit.HOURS));
        }
    }

    /**
     * Helper method to extract a parameter value from a URL.
     */
    private static String extractParameter(String url, String paramName)
    {
        int startIndex = url.indexOf(paramName + "=");
        if (startIndex == -1)
        {
            return null;
        }
        startIndex += paramName.length() + 1;
        int endIndex = url.indexOf("&", startIndex);
        if (endIndex == -1)
        {
            endIndex = url.length();
        }
        return url.substring(startIndex, endIndex);
    }

}
