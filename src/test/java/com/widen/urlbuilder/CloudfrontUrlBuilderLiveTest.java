package com.widen.urlbuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static com.widen.urlbuilder.S3UrlBuilderLiveTest.assertServes;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sends the signed URLs CloudfrontUrlBuilder writes to a real CloudFront distribution and checks that it serves the
 * right object from its S3 origin.
 *
 * <p>The distribution, {@code d1qolazeseslhb.cloudfront.net} ({@code E1UPP53OSWOMJ6}) in the {@code dev} account,
 * accepts only URLs signed by key group {@code widen-urlbuilder-live-test}, whose public key is {@code KQZPIJHINMSLE},
 * and reads through an origin access control from {@code widen-urlbuilder-live-test-us-east-1}, which
 * the tests fill with {@link S3UrlBuilderLiveTest}'s fixtures before they run. Caching is off, so every request reaches S3. The private key lives outside the
 * repository, at {@code ~/.config/urlbuilder-live-test/cloudfront-private-key.pem}; to replace a lost key, generate a
 * new RSA key pair and add its public key to the key group.
 *
 * <p>Run them with {@code aws-vault exec dev -- mise run live-test}, which sets the
 * {@code URLBUILDER_LIVE_CLOUDFRONT_*} variables. Without them or AWS credentials, the tests are skipped.
 */
@Tag("live")
class CloudfrontUrlBuilderLiveTest
{
    private static String hostname;

    private static String keyPairId;

    private static PrivateKey privateKey;

    @BeforeAll
    static void uploadFixtures() throws IOException
    {
        hostname = System.getenv("URLBUILDER_LIVE_CLOUDFRONT_HOSTNAME");
        keyPairId = System.getenv("URLBUILDER_LIVE_CLOUDFRONT_KEY_PAIR_ID");
        String privateKeyFile = System.getenv("URLBUILDER_LIVE_CLOUDFRONT_PRIVATE_KEY_FILE");
        String bucket = System.getenv("URLBUILDER_LIVE_BUCKET_US_EAST_1");
        assumeTrue(hostname != null && keyPairId != null && privateKeyFile != null && bucket != null
                && System.getenv("AWS_ACCESS_KEY_ID") != null && System.getenv("AWS_SECRET_ACCESS_KEY") != null,
            "set AWS credentials and the CloudFront and bucket variables; run: aws-vault exec dev -- mise run live-test");

        privateKey = CloudfrontPrivateKeyUtils.fromPemString(
            new String(Files.readAllBytes(Paths.get(privateKeyFile)), StandardCharsets.UTF_8));
        S3UrlBuilderLiveTest.upload(bucket, "us-east-1");
    }

    static Stream<String> keys()
    {
        return Arrays.stream(S3UrlBuilderAwsSdkTest.keys);
    }

    @ParameterizedTest
    @MethodSource("keys")
    void servesBuilderUrl(String key) throws IOException
    {
        String url = new CloudfrontUrlBuilder(hostname, key, keyPairId, privateKey)
            .withSsl()
            .expireIn(Duration.ofMinutes(15))
            .toString();

        assertServes(key, url);
    }
}
