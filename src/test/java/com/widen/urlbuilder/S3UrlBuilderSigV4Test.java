package com.widen.urlbuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3UrlBuilderSigV4Test
{
    private static final String accessKey = "AKIAIOSFODNN7EXAMPLE";

    private static final String secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    private static final Clock may24 = Clock.fixed(Instant.parse("2013-05-24T00:00:00Z"), ZoneOffset.UTC);

    /**
     * The example in the AWS documentation, "Authenticating Requests: Using Query Parameters (AWS Signature Version
     * 4)", with its published signature.
     */
    @Test
    void signsAwsDocumentationExample()
    {
        String url = new S3UrlBuilder("examplebucket", "test.txt")
            .withEndpoint("s3.amazonaws.com", "us-east-1")
            .usingSsl()
            .usingClock(may24)
            .expireIn(Duration.ofDays(1))
            .usingCredentials(accessKey, secretKey)
            .toString();

        assertEquals("https://examplebucket.s3.amazonaws.com/test.txt"
            + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
            + "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request"
            + "&X-Amz-Date=20130524T000000Z"
            + "&X-Amz-Expires=86400"
            + "&X-Amz-SignedHeaders=host"
            + "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404", url);
    }

    @Test
    void requiresRegionForSigV4()
    {
        S3UrlBuilder noRegion = builder().expireIn(Duration.ofHours(1));
        S3UrlBuilder regionCleared = builder()
            .inRegion("us-west-2")
            .withEndpoint("minio.example.com")
            .expireIn(Duration.ofHours(1));

        assertThrows(IllegalStateException.class, noRegion::toString);
        assertThrows(IllegalStateException.class, regionCleared::toString);
    }

    @Test
    void needsNoRegionForSigV2OrUnsignedUrls()
    {
        S3UrlBuilder sigV2 = builder().usingSignatureVersion2().expireIn(Duration.ofHours(1));
        S3UrlBuilder unsigned = new S3UrlBuilder("examplebucket", "test.txt");

        assertDoesNotThrow(sigV2::toString);
        assertDoesNotThrow(unsigned::toString);
    }

    @Test
    void signsWithEndpointRegion()
    {
        String url = builder()
            .withEndpoint("minio.example.com", "eu-west-1")
            .expireIn(Duration.ofHours(1))
            .toString();

        assertTrue(url.startsWith("https://examplebucket.minio.example.com/test.txt?"), url);
        assertTrue(parameter(url, "X-Amz-Credential").contains("/eu-west-1/s3/aws4_request"), url);
    }

    @Test
    void inRegionSetsSigningRegion()
    {
        String url = builder()
            .inRegion("us-west-2")
            .expireIn(Duration.ofHours(1))
            .toString();

        assertTrue(url.startsWith("https://examplebucket.s3.us-west-2.amazonaws.com/test.txt?"), url);
        assertTrue(parameter(url, "X-Amz-Credential").contains("/us-west-2/"), url);
    }

    /**
     * HTTP clients leave a default port out of the Host header and send the host in lower case, and some send the
     * host exactly as the URL writes it, so the URL writes the host as the signature covers it.
     */
    @Test
    void writesAndSignsHostAsClientsSendIt()
    {
        S3UrlBuilder https = builder().withEndpoint("minio.example.com", "eu-west-1");
        S3UrlBuilder http = builder().usingSsl(false).withEndpoint("minio.example.com", "eu-west-1");
        String expected = https.expireIn(Duration.ofHours(1)).toString();

        assertEquals(expected, builder().withEndpoint("minio.example.com:443", "eu-west-1").expireIn(Duration.ofHours(1)).toString());
        assertEquals(expected, builder().withEndpoint("MinIO.Example.com", "eu-west-1").expireIn(Duration.ofHours(1)).toString());
        assertEquals(http.expireIn(Duration.ofHours(1)).toString(),
            builder().usingSsl(false).withEndpoint("minio.example.com:80", "eu-west-1").expireIn(Duration.ofHours(1)).toString());

        String port9000 = builder().withEndpoint("minio.example.com:9000", "eu-west-1").expireIn(Duration.ofHours(1)).toString();
        assertTrue(port9000.startsWith("https://examplebucket.minio.example.com:9000/"), port9000);
        assertNotEquals(parameter(expected, "X-Amz-Signature"), parameter(port9000, "X-Amz-Signature"));
    }

    @Test
    void concurrentToStringProducesIdenticalUrls() throws Exception
    {
        S3UrlBuilder builder = builder()
            .inRegion("us-east-1")
            .withContentType("application/pdf")
            .withAttachmentFilename("a.pdf")
            .expireIn(Duration.ofHours(1));
        String expectedUrl = builder.toString();
        Callable<String> buildUrl = builder::toString;

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try
        {
            List<Future<String>> urls = new ArrayList<>();
            for (int i = 0; i < 400; i++)
            {
                urls.add(executor.submit(buildUrl));
            }

            for (Future<String> url : urls)
            {
                assertEquals(expectedUrl, url.get());
            }
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    void withEndpointRejectsBlankRegion()
    {
        S3UrlBuilder builder = builder();

        assertThrows(IllegalArgumentException.class, () -> builder.withEndpoint("minio.example.com", null));
        assertThrows(IllegalArgumentException.class, () -> builder.withEndpoint("minio.example.com", " "));
    }

    @Test
    void withEndpointRejectsBlankEndpoint()
    {
        S3UrlBuilder builder = builder();

        assertThrows(IllegalArgumentException.class, () -> builder.withEndpoint(null));
        assertThrows(IllegalArgumentException.class, () -> builder.withEndpoint(" "));
        assertThrows(IllegalArgumentException.class, () -> builder.withEndpoint(null, "us-east-1"));
        assertThrows(IllegalArgumentException.class, () -> builder.withEndpoint("", "us-east-1"));
    }

    @Test
    void inRegionSignsRegionInLowerCaseAsTheHostWritesIt()
    {
        String url = builder().inRegion("US-West-2").expireIn(Duration.ofHours(1)).toString();

        assertEquals(builder().inRegion("us-west-2").expireIn(Duration.ofHours(1)).toString(), url);
    }

    @Test
    void inRegionUsesChinaPartitionEndpoint()
    {
        String url = new S3UrlBuilder("examplebucket", "test.txt").usingSsl().inRegion("cn-north-1").toString();

        assertEquals("https://examplebucket.s3.cn-north-1.amazonaws.com.cn/test.txt", url);
    }

    @Test
    void inRegionRejectsBlankRegion()
    {
        S3UrlBuilder builder = builder();

        assertThrows(IllegalArgumentException.class, () -> builder.inRegion(""));
        assertThrows(IllegalArgumentException.class, () -> builder.inRegion(" "));
    }

    static Stream<Arguments> expirations()
    {
        return Stream.of(
            Arguments.of("at the signing time", expiration(b -> b.expireAt(may24.instant())), null),
            Arguments.of("7 days and 1 second", expiration(b -> b.expireIn(Duration.ofDays(7).plusSeconds(1))), null),
            Arguments.of("500 milliseconds", expiration(b -> b.expireIn(Duration.ofMillis(500))), null),
            Arguments.of("800 milliseconds, signed 400 milliseconds into a second", expiration(b -> b
                .usingClock(Clock.fixed(may24.instant().plusMillis(400), ZoneOffset.UTC))
                .expireIn(Duration.ofMillis(800))), null),
            Arguments.of("1500 milliseconds, signed 600 milliseconds into a second", expiration(b -> b
                .usingClock(Clock.fixed(may24.instant().plusMillis(600), ZoneOffset.UTC))
                .expireIn(Duration.ofMillis(1500))), "1"),
            Arguments.of("7 days", expiration(b -> b.expireIn(Duration.ofDays(7))), "604800"),
            Arguments.of("1500 milliseconds", expiration(b -> b.expireIn(Duration.ofMillis(1500))), "1"),
            Arguments.of("10 seconds after the X-Amz-Date second", expiration(b -> b
                .usingClock(Clock.fixed(may24.instant().plusMillis(900), ZoneOffset.UTC))
                .expireAt(may24.instant().plusSeconds(10))), "10"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expirations")
    void rejectsExpirationOutsideSevenDays(String description, UnaryOperator<S3UrlBuilder> expiration, String expectedExpires)
    {
        S3UrlBuilder builder = expiration.apply(builder().inRegion("us-east-1"));

        if (expectedExpires == null)
        {
            assertThrows(IllegalArgumentException.class, builder::toString);
        }
        else
        {
            assertEquals(expectedExpires, parameter(builder.toString(), "X-Amz-Expires"));
        }
    }

    @Test
    void acceptsLongExpirationWithSigV2()
    {
        S3UrlBuilder builder = builder().usingSignatureVersion2().expireIn(Duration.ofDays(365));

        assertDoesNotThrow(builder::toString);
    }

    @Test
    void failedToStringLeavesNoParametersBehind()
    {
        S3UrlBuilder builder = builder()
            .withContentType("application/pdf")
            .expireIn(Duration.ofHours(1));

        assertThrows(IllegalStateException.class, builder::toString);

        builder.inRegion("us-east-1").expireIn(Duration.ofDays(8));

        assertThrows(IllegalArgumentException.class, builder::toString);

        String url = builder.expireIn(Duration.ofHours(1)).toString();

        assertEquals(1, occurrences(url, "response-content-type="), url);
    }

    @Test
    void reusedBuilderSignsAgain()
    {
        MutableClock clock = new MutableClock(may24.instant());
        S3UrlBuilder builder = builder()
            .inRegion("us-east-1")
            .usingClock(clock)
            .expireIn(Duration.ofHours(1));

        String first = builder.toString();
        clock.now = clock.now.plusSeconds(60);
        String second = builder.toString();

        assertNotEquals(parameter(first, "X-Amz-Date"), parameter(second, "X-Amz-Date"));
        assertEquals(1, occurrences(first, "X-Amz-Signature="), first);
        assertEquals(1, occurrences(second, "X-Amz-Signature="), second);
    }

    @Test
    void protocolRelativeSignsLikeHttps()
    {
        S3UrlBuilder builder = builder().inRegion("us-east-1").expireIn(Duration.ofHours(1));

        String https = builder.toString();
        String protocolRelative = builder.modeProtocolRelative().toString();

        assertTrue(protocolRelative.startsWith("//examplebucket."), protocolRelative);
        assertEquals(parameter(https, "X-Amz-Signature"), parameter(protocolRelative, "X-Amz-Signature"));
    }

    @Test
    void virtualHostSignsBucketAsHost()
    {
        String url = new S3UrlBuilder("assets.example.com", "test.txt")
            .usingBucketVirtualHost()
            .inRegion("us-east-1")
            .usingSsl()
            .usingClock(may24)
            .expireIn(Duration.ofHours(1))
            .usingCredentials(accessKey, secretKey)
            .toString();

        assertTrue(url.startsWith("https://assets.example.com/test.txt?X-Amz-Algorithm="), url);
    }

    @Test
    void fragmentIsNotSigned()
    {
        S3UrlBuilder builder = builder().inRegion("us-east-1").expireIn(Duration.ofHours(1));

        String signature = parameter(builder.toString(), "X-Amz-Signature");
        String withFragment = builder.withFragment("top").toString();

        assertTrue(withFragment.endsWith("&X-Amz-Signature=" + signature + "#top"), withFragment);
    }

    @Test
    void signsSessionToken()
    {
        String url = builder()
            .inRegion("us-east-1")
            .expireIn(Duration.ofHours(1))
            .usingCredentials(accessKey, secretKey, "session-token")
            .toString();

        assertTrue(url.contains("&X-Amz-Security-Token=session-token&X-Amz-SignedHeaders=host&"), url);
    }

    private static S3UrlBuilder builder()
    {
        return new S3UrlBuilder("examplebucket", "test.txt")
            .usingSsl()
            .usingClock(may24)
            .usingCredentials(accessKey, secretKey);
    }

    private static UnaryOperator<S3UrlBuilder> expiration(UnaryOperator<S3UrlBuilder> expiration)
    {
        return expiration;
    }

    /**
     * @return the decoded value of the parameter, ending at the next {@code &} or {@code #}
     */
    private static String parameter(String url, String name)
    {
        int start = url.indexOf(name + "=");
        assertTrue(start >= 0, name + " missing from " + url);
        start += name.length() + 1;

        int end = start;
        while (end < url.length() && url.charAt(end) != '&' && url.charAt(end) != '#')
        {
            end++;
        }
        return PercentCodec.decode(url.substring(start, end));
    }

    private static int occurrences(String text, String part)
    {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1))
        {
            count++;
        }
        return count;
    }

    private static class MutableClock extends Clock
    {
        Instant now;

        MutableClock(Instant now)
        {
            this.now = now;
        }

        @Override
        public ZoneId getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant()
        {
            return now;
        }
    }
}
