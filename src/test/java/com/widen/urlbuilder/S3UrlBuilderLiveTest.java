package com.widen.urlbuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sends the presigned URLs S3UrlBuilder writes to real S3 buckets and checks that S3 serves the right object or
 * rejects the URL.
 *
 * <p>The tests use two buckets in the {@code dev} account: {@code widen-urlbuilder-live-test-us-east-2} in
 * {@code us-east-2}, which accepts only Signature Version 4, and {@code widen-urlbuilder-live-test-us-east-1} in
 * {@code us-east-1}, which also accepts Signature Version 2. They were created once with:
 *
 * <pre>
 * aws-vault exec dev -- aws s3api create-bucket --bucket widen-urlbuilder-live-test-us-east-2 --region us-east-2 \
 *   --create-bucket-configuration LocationConstraint=us-east-2
 * aws-vault exec dev -- aws s3api create-bucket --bucket widen-urlbuilder-live-test-us-east-1 --region us-east-1
 * </pre>
 *
 * <p>Run them with {@code aws-vault exec dev -- mise run live-test}. The {@code test} task never runs them, and they
 * are skipped without AWS credentials and the {@code URLBUILDER_LIVE_BUCKET_US_EAST_2} and
 * {@code URLBUILDER_LIVE_BUCKET_US_EAST_1} variables, which the mise task sets. Each run uploads the fixture objects
 * again, overwriting the last run's.
 *
 * <p>If every case fails, check the machine's clock first: S3 rejects a SigV4 signature more than 15 minutes off.
 */
@Tag("live")
class S3UrlBuilderLiveTest
{
    private static String bucketUsEast2;

    private static String bucketUsEast1;

    @BeforeAll
    static void uploadFixtures()
    {
        bucketUsEast2 = System.getenv("URLBUILDER_LIVE_BUCKET_US_EAST_2");
        bucketUsEast1 = System.getenv("URLBUILDER_LIVE_BUCKET_US_EAST_1");
        assumeTrue(bucketUsEast2 != null && bucketUsEast1 != null && System.getenv("AWS_ACCESS_KEY_ID") != null
                && System.getenv("AWS_SECRET_ACCESS_KEY") != null,
            "set AWS credentials and the bucket variables; run: aws-vault exec dev -- mise run live-test");

        upload(bucketUsEast2, "us-east-2");
        upload(bucketUsEast1, "us-east-1");
    }

    static Stream<String> keys()
    {
        return Arrays.stream(S3UrlBuilderAwsSdkTest.keys);
    }

    @ParameterizedTest
    @MethodSource("keys")
    void signsV4VirtualHost(String key) throws IOException
    {
        assertServes(key, signed(bucketUsEast2, key).inRegion("us-east-2").toString());
    }

    @ParameterizedTest
    @MethodSource("keys")
    void signsV4PathStyle(String key) throws IOException
    {
        assertServes(key, signed(bucketUsEast2, key).inRegion("us-east-2").usingBucketInPath().toString());
    }

    @ParameterizedTest
    @MethodSource("keys")
    void signsV4GlobalEndpoint(String key) throws IOException
    {
        assertServes(key, signed(bucketUsEast1, key).withEndpoint("s3.amazonaws.com", "us-east-1").toString());
    }

    @Test
    void signsV4OverHttp() throws IOException
    {
        String key = "test-key.txt";
        assertServes(key, signed(bucketUsEast2, key).inRegion("us-east-2").usingSsl(false).toString());
    }

    @ParameterizedTest
    @MethodSource("keys")
    void signsV2(String key) throws IOException
    {
        assertServes(key, signed(bucketUsEast1, key).usingSignatureVersion2().toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overridesResponseHeaders(boolean signatureVersion2) throws IOException
    {
        String filename = "Quarterly Report é+1.pdf";
        S3UrlBuilder builder = signatureVersion2
            ? signed(bucketUsEast1, "test-key.txt").usingSignatureVersion2()
            : signed(bucketUsEast2, "test-key.txt").inRegion("us-east-2");
        String url = builder.withAttachmentFilename(filename).withContentType("application/pdf").toString();

        Response response = get(url);

        assertEquals(200, response.status, withoutQuery(url) + " -> " + response);
        assertEquals("application/pdf", response.contentType, withoutQuery(url));
        assertEquals(HttpUtils.createContentDispositionHeader("attachment", filename), response.contentDisposition,
            withoutQuery(url));
    }

    @Test
    void rejectsExpiredUrl() throws IOException
    {
        String url = signed(bucketUsEast2, "test-key.txt")
            .inRegion("us-east-2")
            .usingClock(Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC))
            .expireIn(Duration.ofHours(1))
            .toString();

        assertRejects(403, "AccessDenied", url);
    }

    @Test
    void rejectsTamperedSignature() throws IOException
    {
        String url = signed(bucketUsEast2, "test-key.txt").inRegion("us-east-2").toString();
        Matcher signature = Pattern.compile("X-Amz-Signature=[0-9a-f]+").matcher(url);
        assertTrue(signature.find(), withoutQuery(url) + " has no X-Amz-Signature");
        char last = url.charAt(signature.end() - 1);
        String tampered = url.substring(0, signature.end() - 1) + (last == '0' ? '1' : '0')
            + url.substring(signature.end());

        assertRejects(403, "SignatureDoesNotMatch", tampered);
    }

    @Test
    void rejectsWrongRegion() throws IOException
    {
        String url = signed(bucketUsEast2, "test-key.txt")
            .withEndpoint("s3.us-east-2.amazonaws.com", "us-east-1")
            .toString();

        assertRejects(400, "AuthorizationQueryParametersError", url);
    }

    private static void upload(String bucket, String region)
    {
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion(region)
            .build();
        try
        {
            for (String key : S3UrlBuilderAwsSdkTest.keys)
            {
                s3.putObject(bucket, key, key);
            }
        }
        finally
        {
            s3.shutdown();
        }
    }

    private static S3UrlBuilder signed(String bucket, String key)
    {
        S3UrlBuilder builder = new S3UrlBuilder(bucket, key)
            .usingSsl()
            .expireIn(Duration.ofMinutes(15));
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String sessionToken = System.getenv("AWS_SESSION_TOKEN");
        return sessionToken == null
            ? builder.usingCredentials(accessKey, secretKey)
            : builder.usingCredentials(accessKey, secretKey, sessionToken);
    }

    private static void assertServes(String key, String url) throws IOException
    {
        Response response = get(url);
        assertEquals(200, response.status, withoutQuery(url) + " -> " + response);
        assertEquals(key, response.body, withoutQuery(url));
    }

    private static void assertRejects(int status, String code, String url) throws IOException
    {
        Response response = get(url);
        assertEquals(status, response.status, withoutQuery(url) + " -> " + response);
        assertTrue(response.body.contains("<Code>" + code + "</Code>"), withoutQuery(url) + " -> " + response);
    }

    /**
     * Drops the query, which holds the credential, session token, and signature, so failure messages can show the URL.
     */
    private static String withoutQuery(String url)
    {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    private static Response get(String url) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        // A redirect means S3 refused the URL as written; following it would hide that.
        connection.setInstanceFollowRedirects(false);
        try
        {
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : IOUtils.toString(stream, StandardCharsets.UTF_8);
            return new Response(status, body, connection.getHeaderField("Content-Type"),
                connection.getHeaderField("Content-Disposition"));
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static final class Response
    {
        final int status;

        final String body;

        final String contentType;

        final String contentDisposition;

        Response(int status, String body, String contentType, String contentDisposition)
        {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
            this.contentDisposition = contentDisposition;
        }

        /**
         * Gives the status and S3's error code and message, leaving out the rest of an error body: a
         * SignatureDoesNotMatch body echoes the canonical request, session token included.
         */
        @Override
        public String toString()
        {
            if (status < 400)
            {
                return String.valueOf(status);
            }
            return status + " " + element("Code") + ": " + element("Message");
        }

        private String element(String name)
        {
            int start = body.indexOf("<" + name + ">");
            int end = body.indexOf("</" + name + ">");
            return start < 0 || end < start ? "" : body.substring(start + name.length() + 2, end);
        }
    }
}
