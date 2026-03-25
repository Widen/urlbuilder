package com.widen.urlbuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningContextTest {
    
    @Test
    void testAllFieldsPopulated() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key1", "value1");
        params.put("key2", "value2");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            8080,
            "/path/to/resource",
            "key1=value1&key2=value2",
            params,
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
        assertEquals(params, context.getParameters());
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
            Collections.emptyMap(),
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
            null,
            "http://example.com",
            false,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );
        
        assertEquals("", context.getEncodedPath());
        assertEquals("", context.getEncodedQuery());
        assertEquals(Collections.emptyMap(), context.getParameters());
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
            Collections.emptyMap(),
            null,
            "//example.com/path",
            true,
            UrlBuilder.GenerationMode.PROTOCOL_RELATIVE
        );
        
        assertEquals("", context.getProtocol());
        assertEquals(UrlBuilder.GenerationMode.PROTOCOL_RELATIVE, context.getGenerationMode());
    }

    @Test
    void testNullProtocol() {
        SigningContextImpl context = new SigningContextImpl(
            null,
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "example.com/path",
            false,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNull(context.getProtocol());
    }

    @Test
    void testEmptyProtocol() {
        SigningContextImpl context = new SigningContextImpl(
            "",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "//example.com/path",
            false,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("", context.getProtocol());
    }

    @Test
    void testNullHostname() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            null,
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "https:///path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNull(context.getHostname());
    }

    @Test
    void testEmptyHostname() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "https:///path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("", context.getHostname());
    }

    @Test
    void testNullEncodedPath() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            null,
            "",
            Collections.emptyMap(),
            null,
            "https://example.com",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNull(context.getEncodedPath());
    }

    @Test
    void testEmptyEncodedPath() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "",
            "",
            Collections.emptyMap(),
            null,
            "https://example.com",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("", context.getEncodedPath());
    }

    @Test
    void testNullEncodedQuery() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            null,
            Collections.emptyMap(),
            null,
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNull(context.getEncodedQuery());
    }

    @Test
    void testEmptyEncodedQuery() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("", context.getEncodedQuery());
    }

    @Test
    void testNullParameters() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            null,
            null,
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNotNull(context.getParameters());
        assertEquals(Collections.emptyMap(), context.getParameters());
    }

    @Test
    void testEmptyParameters() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNotNull(context.getParameters());
        assertTrue(context.getParameters().isEmpty());
    }

    @Test
    void testNullFragment() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNull(context.getFragment());
    }

    @Test
    void testEmptyFragment() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            "",
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("", context.getFragment());
    }

    @Test
    void testNullUrl() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            null,
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertNull(context.getUrl());
    }

    @Test
    void testEmptyUrl() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("", context.getUrl());
    }

    @Test
    void testNullGenerationMode() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "https://example.com/path",
            true,
            null
        );

        assertNull(context.getGenerationMode());
    }

    @Test
    void testSingleParameter() {
        Map<String, String> params = Collections.singletonMap("token", "abc123");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "token=abc123",
            params,
            null,
            "https://example.com/path?token=abc123",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("token=abc123", context.getEncodedQuery());
        assertEquals(1, context.getParameters().size());
        assertEquals("abc123", context.getParameters().get("token"));
    }

    @Test
    void testParameterWithEmptyStringValue() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("flag", "");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "flag=",
            params,
            null,
            "https://example.com/path?flag=",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("flag=", context.getEncodedQuery());
        assertEquals(1, context.getParameters().size());
        assertEquals("", context.getParameters().get("flag"));
    }

    @Test
    void testParameterWithNullValue() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("flag", null);

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "flag",
            params,
            null,
            "https://example.com/path?flag",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("flag", context.getEncodedQuery());
        assertEquals(1, context.getParameters().size());
        assertNull(context.getParameters().get("flag"));
        assertTrue(context.getParameters().containsKey("flag"));
    }

    @Test
    void testParameterWithEmptyStringKey() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("", "value");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "=value",
            params,
            null,
            "https://example.com/path?=value",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("=value", context.getEncodedQuery());
        assertEquals(1, context.getParameters().size());
        assertEquals("value", context.getParameters().get(""));
    }

    @Test
    void testParameterWithEmptyStringKeyAndValue() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("", "");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "=",
            params,
            null,
            "https://example.com/path?=",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("=", context.getEncodedQuery());
        assertEquals(1, context.getParameters().size());
        assertEquals("", context.getParameters().get(""));
    }

    @Test
    void testParametersAreUnmodifiable() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", "value");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "key=value",
            params,
            null,
            "https://example.com/path?key=value",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertThrows(UnsupportedOperationException.class, () ->
            context.getParameters().put("new", "entry")
        );
    }

    @Test
    void testEncodedQueryWithMultipleParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "1");
        params.put("b", "2");
        params.put("c", "3");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "a=1&b=2&c=3",
            params,
            null,
            "https://example.com/path?a=1&b=2&c=3",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("a=1&b=2&c=3", context.getEncodedQuery());
        assertEquals(3, context.getParameters().size());
        assertEquals("1", context.getParameters().get("a"));
        assertEquals("2", context.getParameters().get("b"));
        assertEquals("3", context.getParameters().get("c"));
    }

    @Test
    void testEncodedQueryWithSpecialCharacters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", "hello world");
        params.put("path", "/a/b");

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/search",
            "query=hello+world&path=%2Fa%2Fb",
            params,
            null,
            "https://example.com/search?query=hello+world&path=%2Fa%2Fb",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("query=hello+world&path=%2Fa%2Fb", context.getEncodedQuery());
        assertEquals("hello world", context.getParameters().get("query"));
        assertEquals("/a/b", context.getParameters().get("path"));
    }

    @Test
    void testNullParametersWithPopulatedEncodedQuery() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "key=value",
            null,
            null,
            "https://example.com/path?key=value",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("key=value", context.getEncodedQuery());
        assertNotNull(context.getParameters());
        assertTrue(context.getParameters().isEmpty());
    }

    @Test
    void testEmptyParametersWithPopulatedEncodedQuery() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            -1,
            "/path",
            "key=value",
            Collections.emptyMap(),
            null,
            "https://example.com/path?key=value",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("key=value", context.getEncodedQuery());
        assertNotNull(context.getParameters());
        assertTrue(context.getParameters().isEmpty());
    }
}
