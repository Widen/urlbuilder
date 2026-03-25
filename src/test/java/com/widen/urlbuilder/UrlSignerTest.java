package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UrlSignerTest {
    
    @Test
    void testLambdaImplementation() {
        UrlSigner signer = context -> {
            Map<String, String> params = new HashMap<>();
            params.put("signature", "test-sig");
            return params;
        };
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertEquals(1, result.size());
        assertEquals("test-sig", result.get("signature"));
    }
    
    @Test
    void testClassImplementation() {
        UrlSigner signer = new TestSigner();
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertEquals(2, result.size());
        assertEquals("class-sig", result.get("signature"));
        assertEquals("12345", result.get("timestamp"));
    }
    
    @Test
    void testEmptyMapReturn() {
        UrlSigner signer = context -> Collections.emptyMap();
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testNullValueInMap() {
        UrlSigner signer = context -> {
            Map<String, String> params = new HashMap<>();
            params.put("signature", "test-sig");
            params.put("empty", null);
            return params;
        };
        
        UrlSigner.SigningContext context = createMockContext();
        Map<String, String> result = signer.sign(context);
        
        assertEquals(2, result.size());
        assertNull(result.get("empty"));
    }
    
    private UrlSigner.SigningContext createMockContext() {
        return new UrlSigner.SigningContext() {
            @Override public String getProtocol() { return "https"; }
            @Override public String getHostname() { return "example.com"; }
            @Override public int getPort() { return -1; }
            @Override public String getEncodedPath() { return "/path"; }
            @Override public String getEncodedQuery() { return "key=value"; }
            @Override public Map<String, String> getParameters() { return Collections.singletonMap("key", "value"); }
            @Override public String getFragment() { return null; }
            @Override public String getUrl() { return "https://example.com/path?key=value"; }
            @Override public boolean isSsl() { return true; }
            @Override public UrlBuilder.GenerationMode getGenerationMode() { return UrlBuilder.GenerationMode.FULLY_QUALIFIED; }
        };
    }
    
    private static class TestSigner implements UrlSigner {
        @Override
        public Map<String, String> sign(SigningContext context) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("signature", "class-sig");
            params.put("timestamp", "12345");
            return params;
        }
    }
}
