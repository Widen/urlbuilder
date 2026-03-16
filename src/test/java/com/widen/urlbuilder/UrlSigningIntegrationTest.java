package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for URL signing feature.
 * 
 * Tests complete workflows from builder creation to signed URL generation.
 */
class UrlSigningIntegrationTest {
    
    private static final String SECRET_KEY = "integration-test-key";
    
    @Test
    void testCompleteHmacSigningWorkflow() {
        // Build a complex URL with signing
        UrlBuilder builder = new UrlBuilder("cdn.example.com", "/videos/2024/movie.mp4");
        builder.usingSsl();
        builder.setPort(8443);
        builder.addParameter("user", "john_doe");
        builder.addParameter("quality", "1080p");
        builder.addParameter("start", "60");
        builder.withFragment("chapter2");
        builder.usingUrlSigner(context -> {
            // Verify context has all expected data
            assertEquals("https", context.getProtocol());
            assertEquals("cdn.example.com", context.getHostname());
            assertEquals(8443, context.getPort());
            assertTrue(context.isSsl());
            assertTrue(context.getEncodedPath().contains("videos"));
            assertTrue(context.getEncodedQuery().contains("user=john_doe"));
            assertEquals("chapter2", context.getFragment());
            
            // Generate signature
            String signature = hmacSha256(context.getUrl(), SECRET_KEY);
            long expires = System.currentTimeMillis() / 1000 + 3600;
            
            Map<String, String> params = new LinkedHashMap<>();
            params.put("expires", String.valueOf(expires));
            params.put("signature", signature);
            return params;
        });
        
        String signedUrl = builder.toString();
        
        // Verify final URL structure
        assertTrue(signedUrl.startsWith("https://cdn.example.com:8443/"));
        assertTrue(signedUrl.contains("videos/2024/movie.mp4"));
        assertTrue(signedUrl.contains("user=john_doe"));
        assertTrue(signedUrl.contains("quality=1080p"));
        assertTrue(signedUrl.contains("start=60"));
        assertTrue(signedUrl.contains("expires="));
        assertTrue(signedUrl.contains("signature="));
        assertTrue(signedUrl.endsWith("#chapter2"));
    }
    
    @Test
    void testSigningWithCustomEncoders() {
        UrlBuilder builder = new UrlBuilder("example.com", "/file with spaces.pdf");
        builder.addParameter("name", "value with spaces");
        builder.usingUrlSigner(context -> {
            // Verify encoding is applied before signing
            assertTrue(context.getEncodedPath().contains("%20") || 
                      context.getEncodedPath().contains("+")); 
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSigningInteractionWithExistingFeatures() {
        // Test that signing works with all existing UrlBuilder features
        UrlBuilder builder = new UrlBuilder("api.example.com", "/v1/users/123");
        builder.usingSsl();
        builder.addParameter("format", "json");
        builder.addParameter("fields", "id,name,email");
        builder.usingUrlSigner(context -> {
            String signature = hmacSha256(context.getUrl(), SECRET_KEY);
            return Collections.singletonMap("sig", signature);
        });
        builder.withFragment("profile");
        
        String url = builder.toString();
        
        assertTrue(url.startsWith("https://api.example.com/v1/users/123"));
        assertTrue(url.contains("format=json"));
        assertTrue(url.contains("sig="));
        assertTrue(url.endsWith("#profile"));
    }
    
    @Test
    void testReusableSignerAcrossMultipleBuilders() {
        UrlSigner reusableSigner = context -> {
            String signature = hmacSha256(context.getUrl(), SECRET_KEY);
            return Collections.singletonMap("sig", signature);
        };
        
        UrlBuilder builder1 = new UrlBuilder("cdn1.example.com", "/file1.pdf");
        builder1.usingUrlSigner(reusableSigner);
        String url1 = builder1.toString();
        
        UrlBuilder builder2 = new UrlBuilder("cdn2.example.com", "/file2.pdf");
        builder2.usingUrlSigner(reusableSigner);
        String url2 = builder2.toString();
        
        assertTrue(url1.contains("sig="));
        assertTrue(url2.contains("sig="));
        assertNotEquals(url1, url2);
    }
    
    @Test
    void testSigningDoesNotAffectOriginalBuilder() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test");
        builder.addParameter("key", "value");
        
        // Add signer
        builder.usingUrlSigner(context -> Collections.singletonMap("sig", "test1"));
        String url1 = builder.toString();
        
        // Change signer
        builder.usingUrlSigner(context -> Collections.singletonMap("sig", "test2"));
        String url2 = builder.toString();
        
        // URLs should have different signatures
        assertTrue(url1.contains("sig=test1"));
        assertTrue(url2.contains("sig=test2"));
    }
    
    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }
}
