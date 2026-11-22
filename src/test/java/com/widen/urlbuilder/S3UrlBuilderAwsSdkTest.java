package com.widen.urlbuilder;

import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares the presigned URLs S3UrlBuilder writes with those of the AWS SDK for Java v1, which presigns with the same
 * Signature Version 2 and Signature Version 4. The SDK signs locally, so these tests need no network or real
 * credentials.
 */
class S3UrlBuilderAwsSdkTest
{
    private static final String accessKey = "AKIAIOSFODNN7EXAMPLE";

    private static final String secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    private static final Instant expiration = Instant.ofEpochSecond(1522540800L);

    private static final DateTimeFormatter amzDate =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    static final String[] keys = {
        "test-key.txt",
        "docs/a+b é.txt",
        "a%20b.txt",
        "a b@c:d(1)*~!$&'=,;.txt",
        "folder/",
        "a//b",
        "a///b//",
        "ünïcødé/😀.png"
    };

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void signsLikeAwsSdk(boolean pathStyle)
    {
        AmazonS3 sdk = sdkClient(new BasicAWSCredentials(accessKey, secretKey), pathStyle);

        for (String key : keys)
        {
            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", key)
                .usingSsl()
                .expireAt(expiration)
                .usingSignatureVersion2()
                .usingCredentials(accessKey, secretKey);
            if (pathStyle)
            {
                builder.usingBucketInPath();
            }

            assertSameUrl(sdk.generatePresignedUrl("test-bucket", key, Date.from(expiration)), builder.toString(), key);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-key.txt", "docs/a+b é.txt"})
    void signsSessionTokenLikeAwsSdk(String key)
    {
        AmazonS3 sdk = sdkClient(new BasicSessionCredentials(accessKey, secretKey, "session/token+="), false);

        String url = new S3UrlBuilder("test-bucket", key)
            .usingSsl()
            .expireAt(expiration)
            .usingSignatureVersion2()
            .usingCredentials(accessKey, secretKey, "session/token+=")
            .toString();

        assertSameUrl(sdk.generatePresignedUrl("test-bucket", key, Date.from(expiration)), url, key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"report.pdf", "Quarterly Report é+1.pdf"})
    void signsResponseHeadersLikeAwsSdk(String filename)
    {
        AmazonS3 sdk = sdkClient(new BasicAWSCredentials(accessKey, secretKey), false);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest("test-bucket", "docs/report.pdf")
            .withExpiration(Date.from(expiration))
            .withResponseHeaders(new ResponseHeaderOverrides()
                .withContentDisposition(HttpUtils.createContentDispositionHeader("attachment", filename))
                .withContentType("application/pdf"));

        String url = new S3UrlBuilder("test-bucket", "docs/report.pdf")
            .usingSsl()
            .withAttachmentFilename(filename)
            .withContentType("application/pdf")
            .expireAt(expiration)
            .usingSignatureVersion2()
            .usingCredentials(accessKey, secretKey)
            .toString();

        assertSameUrl(sdk.generatePresignedUrl(request), url, filename);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void signsV4LikeAwsSdk(boolean pathStyle)
    {
        AmazonS3 sdk = sdkV4Client(new BasicAWSCredentials(accessKey, secretKey), pathStyle);

        for (String key : keys)
        {
            URL expected = sdk.generatePresignedUrl("test-bucket", key, oneHourFromNow());

            S3UrlBuilder builder = new S3UrlBuilder("test-bucket", key)
                .withEndpoint("s3.amazonaws.com", "us-east-1")
                .usingSsl()
                .usingCredentials(accessKey, secretKey);
            if (pathStyle)
            {
                builder.usingBucketInPath();
            }

            assertSameUrl(expected, signedAsSdkSigned(builder, expected).toString(), key);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-key.txt", "docs/a+b é.txt"})
    void signsV4SessionTokenLikeAwsSdk(String key)
    {
        AmazonS3 sdk = sdkV4Client(new BasicSessionCredentials(accessKey, secretKey, "session/token+="), false);

        URL expected = sdk.generatePresignedUrl("test-bucket", key, oneHourFromNow());

        S3UrlBuilder builder = new S3UrlBuilder("test-bucket", key)
            .withEndpoint("s3.amazonaws.com", "us-east-1")
            .usingSsl()
            .usingCredentials(accessKey, secretKey, "session/token+=");

        assertSameUrl(expected, signedAsSdkSigned(builder, expected).toString(), key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"report.pdf", "Quarterly Report é+1.pdf"})
    void signsV4ResponseHeadersLikeAwsSdk(String filename)
    {
        AmazonS3 sdk = sdkV4Client(new BasicAWSCredentials(accessKey, secretKey), false);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest("test-bucket", "docs/report.pdf")
            .withExpiration(oneHourFromNow())
            .withResponseHeaders(new ResponseHeaderOverrides()
                .withContentDisposition(HttpUtils.createContentDispositionHeader("attachment", filename))
                .withContentType("application/pdf"));
        URL expected = sdk.generatePresignedUrl(request);

        S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "docs/report.pdf")
            .withEndpoint("s3.amazonaws.com", "us-east-1")
            .usingSsl()
            .withAttachmentFilename(filename)
            .withContentType("application/pdf")
            .usingCredentials(accessKey, secretKey);

        assertSameUrl(expected, signedAsSdkSigned(builder, expected).toString(), filename);
    }

    @Test
    void signsV4CustomEndpointWithPortLikeAwsSdk()
    {
        AmazonS3 sdk = AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://localhost:9000", "us-east-1"))
            .withPathStyleAccessEnabled(true)
            .withClientConfiguration(new ClientConfiguration().withSignerOverride("AWSS3V4SignerType"))
            .build();

        URL expected = sdk.generatePresignedUrl("test-bucket", "docs/a+b é.txt", oneHourFromNow());

        S3UrlBuilder builder = new S3UrlBuilder("test-bucket", "docs/a+b é.txt")
            .withEndpoint("localhost:9000", "us-east-1")
            .usingSsl(false)
            .usingBucketInPath()
            .usingCredentials(accessKey, secretKey);

        String url = signedAsSdkSigned(builder, expected).toString();

        assertEquals("http://localhost:9000/test-bucket/", url.substring(0, "http://localhost:9000/test-bucket/".length()));
        assertSameUrl(expected, url, "localhost:9000");
    }

    private static Date oneHourFromNow()
    {
        return Date.from(Instant.now().plus(Duration.ofHours(1)));
    }

    /**
     * The SDK signs at the current time, so the builder signs at the SDK's X-Amz-Date for the SDK's X-Amz-Expires.
     */
    private static S3UrlBuilder signedAsSdkSigned(S3UrlBuilder builder, URL sdkUrl)
    {
        Map<String, String> sdkParameters = queryParameters(URI.create(sdkUrl.toString()));
        Instant signingTime = amzDate.parse(sdkParameters.get("X-Amz-Date"), Instant::from);

        return builder
            .usingClock(Clock.fixed(signingTime, ZoneOffset.UTC))
            .expireIn(Duration.ofSeconds(Long.parseLong(sdkParameters.get("X-Amz-Expires"))));
    }

    private static AmazonS3 sdkV4Client(AWSCredentials credentials, boolean pathStyle)
    {
        return AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion("us-east-1")
            .withPathStyleAccessEnabled(pathStyle)
            .withClientConfiguration(new ClientConfiguration().withSignerOverride("AWSS3V4SignerType"))
            .build();
    }

    private static AmazonS3 sdkClient(AWSCredentials credentials, boolean pathStyle)
    {
        return AmazonS3ClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion("us-east-1")
            .withPathStyleAccessEnabled(pathStyle)
            .withClientConfiguration(new ClientConfiguration().withSignerOverride("S3SignerType"))
            .build();
    }

    private static void assertSameUrl(URL expected, String actual, String message)
    {
        URI expectedUri = URI.create(expected.toString());
        URI actualUri = URI.create(actual);

        assertEquals(expectedUri.getHost(), actualUri.getHost(), message);
        assertEquals(expectedUri.getPort(), actualUri.getPort(), message);
        assertEquals(expectedUri.getRawPath(), actualUri.getRawPath(), message);
        assertEquals(queryParameters(expectedUri), queryParameters(actualUri), message);
    }

    private static Map<String, String> queryParameters(URI uri)
    {
        Map<String, String> parameters = new TreeMap<>();
        for (String pair : uri.getRawQuery().split("&"))
        {
            String[] keyValue = pair.split("=", 2);
            parameters.put(PercentCodec.decode(keyValue[0]), PercentCodec.decode(keyValue[1]));
        }
        return parameters;
    }
}
