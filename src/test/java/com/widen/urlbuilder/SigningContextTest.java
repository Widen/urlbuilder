package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningContextTest {
    
    @Test
    void testAllFieldsPopulated() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            8080,
            "/path/to/resource",
            "key1=value1&key2=value2",
            "section",
            "https://example.com:8080/path/to/resource?key1=value1&key2=value2",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );
        
        assertEquals("https", context.getProtocol());
        assertEquals("example.com", context.getHostname());
        assertEquals(8080, context.getPort());
        assertEquals("/path/to/resource", context.getEncodedPath());
        assertEquals("key1=value1&key2=value2", context.getEncodedQuery());
        assertEquals("section", context.getFragment());
        assertEquals("https://example.com:8080/path/to/resource?key1=value1&key2=value2", context.getUrl());
        assertTrue(context.isSsl());
        assertEquals(UrlBuilder.GenerationMode.FULLY_QUALIFIED, context.getGenerationMode());
    }
    
    @Test
    void testDefaultPort() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            null,
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );
        
        assertEquals(-1, context.getPort());
    }
    
    @Test
    void testEmptyQueryAndPath() {
        SigningContextImpl context = new SigningContextImpl(
            "http",
            "example.com",
            -1,
            "",
            "",
            null,
            "http://example.com",
            false,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );
        
        assertEquals("", context.getEncodedPath());
        assertEquals("", context.getEncodedQuery());
        assertNull(context.getFragment());
        assertFalse(context.isSsl());
    }
    
    @Test
    void testProtocolRelativeMode() {
        SigningContextImpl context = new SigningContextImpl(
            "",
            "example.com",
            -1,
            "/path",
            "",
            null,
            "//example.com/path",
            true,
            UrlBuilder.GenerationMode.PROTOCOL_RELATIVE
        );
        
        assertEquals("", context.getProtocol());
        assertEquals(UrlBuilder.GenerationMode.PROTOCOL_RELATIVE, context.getGenerationMode());
    }
}
