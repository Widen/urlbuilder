package com.widen.urlbuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assumeTrue(bucketUsEast2 != null && bucketUsEast1 != null && System.getenv("AWS_ACCESS_KEY_ID") != null,
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

    private static void upload(String bucket, String region)
    {
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion(region)
            .build();
        for (String key : S3UrlBuilderAwsSdkTest.keys)
        {
            s3.putObject(bucket, key, key);
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
