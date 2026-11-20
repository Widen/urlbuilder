package com.widen.urlbuilder;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares the presigned URLs S3UrlBuilder writes with those of the AWS SDK for Java v1, which presigns with the same
 * Signature Version 2. The SDK signs locally, so these tests need no network or real credentials.
 */
class S3UrlBuilderAwsSdkTest
{
    private static final String accessKey = "AKIAIOSFODNN7EXAMPLE";

    private static final String secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    private static final Instant expiration = Instant.ofEpochSecond(1522540800L);

    private static final String[] keys = {
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
