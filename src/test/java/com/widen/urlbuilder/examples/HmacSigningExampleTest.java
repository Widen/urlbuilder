package com.widen.urlbuilder.examples;

import com.widen.urlbuilder.UrlBuilder;
import com.widen.urlbuilder.UrlSigner;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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
            String signature = hmacSha256(context.getUrl(), SECRET_KEY);
            return Collections.singletonMap("signature", signature);
        });
        
        String signedUrl = builder.toString();
        
        assertTrue(signedUrl.contains("signature="));
        assertTrue(signedUrl.contains("user=john"));
    }
    
    @Test
    void exampleReusableHmacSigner() {
        // Reusable signer class
        HmacUrlSigner signer = new HmacUrlSigner(SECRET_KEY, "HmacSHA256");
        
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
        ExpiringHmacSigner signer = new ExpiringHmacSigner(SECRET_KEY, 3600);
        
        UrlBuilder builder = new UrlBuilder("api.example.com", "/v1/data");
        builder.usingUrlSigner(signer);
        String signedUrl = builder.toString();
        
        assertTrue(signedUrl.contains("signature="));
        assertTrue(signedUrl.contains("expires="));
    }
    
    @Test
    void exampleSigningWithSsl() {
        UrlBuilder builder = new UrlBuilder("secure.example.com", "/secure/document.pdf");
        builder.usingSsl();
        builder.usingUrlSigner(context -> {
            // Verify we're signing HTTPS URL
            assertTrue(context.isSsl());
            assertTrue(context.getUrl().startsWith("https://"));
            
            String signature = hmacSha256(context.getUrl(), SECRET_KEY);
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
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
    
    /**
     * Reusable HMAC URL signer.
     */
    private static class HmacUrlSigner implements UrlSigner {
        private final String secretKey;
        private final String algorithm;
        
        public HmacUrlSigner(String secretKey, String algorithm) {
            this.secretKey = secretKey;
            this.algorithm = algorithm;
        }
        
        @Override
        public Map<String, String> sign(SigningContext context) {
            String signature = hmacSha256(context.getUrl(), secretKey);
            
            Map<String, String> params = new HashMap<>();
            params.put("signature", signature);
            params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            return params;
        }
    }
    
    /**
     * HMAC signer with expiration support.
     */
    private static class ExpiringHmacSigner implements UrlSigner {
        private final String secretKey;
        private final long expirationSeconds;
        
        public ExpiringHmacSigner(String secretKey, long expirationSeconds) {
            this.secretKey = secretKey;
            this.expirationSeconds = expirationSeconds;
        }
        
        @Override
        public Map<String, String> sign(SigningContext context) {
            long expiresAt = System.currentTimeMillis() / 1000 + expirationSeconds;
            
            // Sign URL + expiration timestamp
            String toSign = context.getUrl() + expiresAt;
            String signature = hmacSha256(toSign, secretKey);
            
            Map<String, String> params = new LinkedHashMap<>();
            params.put("expires", String.valueOf(expiresAt));
            params.put("signature", signature);
            return params;
        }
    }
}
