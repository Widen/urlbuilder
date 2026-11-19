package com.widen.urlbuilder.examples;

import com.widen.urlbuilder.UrlBuilder;
import com.widen.urlbuilder.UrlSafeBase64;
import com.widen.urlbuilder.UrlSigner;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Examples demonstrating URL signing with HMAC.
 * 
 * These examples show real-world usage patterns for signing URLs
 * with HMAC-SHA256 signatures.
 */
class HmacSigningExampleTest {
    
    private static final String SECRET_KEY = "my-secret-key-12345";
    
    @Test
    void exampleSimpleHmacSigning() {
        // Simple lambda-based HMAC signing
        UrlBuilder builder = new UrlBuilder("cdn.example.com", "/videos/movie.mp4");
        builder.addParameter("user", "john");
        builder.usingUrlSigner(context -> {
            String signature = hmacSha256(context.getStringToSign(), SECRET_KEY);
            return Collections.singletonMap("signature", signature);
        });
        
        String signedUrl = builder.toString();
        
        assertEquals("http://cdn.example.com/videos/movie.mp4?user=john&signature=trISZB-s6WYVxetzmgdIBCOrLj7GF-po2jnmQHAtQjM", signedUrl);
        assertTrue(signedUrl.contains("user=john"));
    }
    
    @Test
    void exampleReusableHmacSigner() {
        // Reusable signer class
        HmacUrlSigner signer = new HmacUrlSigner(SECRET_KEY);
        
        UrlBuilder builder1 = new UrlBuilder("cdn.example.com", "/file1.pdf");
        builder1.usingUrlSigner(signer);
        String url1 = builder1.toString();
        
        UrlBuilder builder2 = new UrlBuilder("cdn.example.com", "/file2.pdf");
        builder2.usingUrlSigner(signer);
        String url2 = builder2.toString();
        
        assertTrue(url1.contains("signature="));
        assertTrue(url2.contains("signature="));
        assertNotEquals(url1, url2); // Different signatures
    }
    
    @Test
    void exampleExpiringUrlSigner() {
        // Signer that adds expiration timestamp
        ExpiringHmacSigner signer = new ExpiringHmacSigner(SECRET_KEY, Duration.ofHours(1));
        
        UrlBuilder builder = new UrlBuilder("api.example.com", "/v1/data");
        builder.usingUrlSigner(signer);
        String signedUrl = builder.toString();
        
        assertTrue(signedUrl.contains("signature="));
        assertTrue(signedUrl.contains("expires="));
    }

    @Test
    void exampleVerifyingExpiringUrl() {
        ExpiringHmacVerifier verifier = new ExpiringHmacVerifier(SECRET_KEY);

        String signedUrl = new UrlBuilder("api.example.com", "/v1/data")
            .addParameter("user", "john")
            .usingUrlSigner(new ExpiringHmacSigner(SECRET_KEY, Duration.ofHours(1)))
            .toString();
        String expiredUrl = new UrlBuilder("api.example.com", "/v1/data")
            .addParameter("user", "john")
            .usingUrlSigner(new ExpiringHmacSigner(SECRET_KEY, Duration.ofHours(-1)))
            .toString();

        assertTrue(verifier.isValid(signedUrl));
        assertFalse(verifier.isValid(expiredUrl), "an expired URL is rejected");
        assertFalse(verifier.isValid(signedUrl.replace("user=john", "user=jane")), "a changed URL is rejected");
        assertFalse(verifier.isValid(signedUrl.replaceFirst("expires=\\d+", "expires=9999999999")), "a changed expiration is rejected");
        assertFalse(verifier.isValid("http://api.example.com/v1/data?user=john"), "an unsigned URL is rejected");
    }
    
    @Test
    void exampleSigningWithSsl() {
        UrlBuilder builder = new UrlBuilder("secure.example.com", "/secure/document.pdf");
        builder.usingSsl();
        builder.usingUrlSigner(context -> {
            // Verify we're signing HTTPS URL
            assertTrue(context.isSsl());
            assertTrue(context.getUrl().startsWith("https://"));
            
            String signature = hmacSha256(context.getStringToSign(), SECRET_KEY);
            return Collections.singletonMap("sig", signature);
        });
        
        String signedUrl = builder.toString();
        
        assertTrue(signedUrl.startsWith("https://"));
        assertTrue(signedUrl.contains("sig="));
    }
    
    // Helper method for HMAC-SHA256
    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return UrlSafeBase64.encode(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
    
    /**
     * Reusable HMAC URL signer.
     */
    private static class HmacUrlSigner implements UrlSigner {
        private final String secretKey;

        public HmacUrlSigner(String secretKey) {
            this.secretKey = secretKey;
        }
        
        @Override
        public Map<String, String> sign(SigningContext context) {
            String signature = hmacSha256(context.getStringToSign(), secretKey);
            
            return Collections.singletonMap("signature", signature);
        }
    }
    
    /**
     * HMAC signer with expiration support.
     */
    private static class ExpiringHmacSigner implements UrlSigner {
        private final String secretKey;
        private final Duration validFor;
        
        public ExpiringHmacSigner(String secretKey, Duration validFor) {
            this.secretKey = secretKey;
            this.validFor = validFor;
        }
        
        @Override
        public Map<String, ?> sign(SigningContext context) {
            // Added before signing, so the signature covers the expiration
            context.addParameter("expires", Instant.now().plus(validFor).getEpochSecond());

            String signature = hmacSha256(context.getStringToSign(), secretKey);
            return Collections.singletonMap("signature", signature);
        }
    }

    /**
     * Verifies URLs signed by {@link ExpiringHmacSigner}.
     */
    private static class ExpiringHmacVerifier {
        private final String secretKey;

        public ExpiringHmacVerifier(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isValid(String signedUrl) {
            UrlBuilder builder = new UrlBuilder(signedUrl);
            String expires = firstValue(builder, "expires");
            String signature = firstValue(builder, "signature");
            if (expires == null || signature == null) {
                return false;
            }

            // The signer signed the whole URL except the signature parameter, so rebuild exactly that.
            // Check the signature first: until it matches, the expiration is untrusted input.
            String signedText = builder.clearParameter("signature").toString();
            String expected = hmacSha256(signedText, secretKey);
            // MessageDigest.isEqual compares in constant time, so the comparison leaks nothing about the signature
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(expires));
            return Instant.now().isBefore(expiresAt);
        }

        private static String firstValue(UrlBuilder builder, String name) {
            List<String> values = builder.getQueryParameters().get(name);
            return values == null ? null : values.get(0);
        }
    }
}
