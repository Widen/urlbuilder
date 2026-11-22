package com.widen.urlbuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningContextTest {
    
    @Test
    void testAllFieldsPopulated() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("key1", Collections.singletonList("value1"));
        params.put("key2", Collections.singletonList("value2"));

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
            null,
            "/path",
            "",
            Collections.emptyMap(),
            null,
            "https://example.com/path",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );
        
        assertNull(context.getPort());
    }
    
    @Test
    void testEmptyQueryAndPath() {
        SigningContextImpl context = new SigningContextImpl(
            "http",
            "example.com",
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
            null,
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
        Map<String, List<String>> params = Collections.singletonMap("token", Collections.singletonList("abc123"));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
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
        assertEquals(Collections.singletonList("abc123"), context.getParameters().get("token"));
    }

    @Test
    void testParameterWithEmptyStringValue() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("flag", Collections.singletonList(""));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
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
        assertEquals(Collections.singletonList(""), context.getParameters().get("flag"));
    }

    @Test
    void testParameterWithNullValue() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("flag", Collections.singletonList(null));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
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
        assertEquals(Collections.singletonList(null), context.getParameters().get("flag"));
        assertTrue(context.getParameters().containsKey("flag"));
    }

    @Test
    void testParameterWithEmptyStringKey() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("", Collections.singletonList("value"));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
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
        assertEquals(Collections.singletonList("value"), context.getParameters().get(""));
    }

    @Test
    void testParameterWithEmptyStringKeyAndValue() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("", Collections.singletonList(""));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
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
        assertEquals(Collections.singletonList(""), context.getParameters().get(""));
    }

    @Test
    void testParametersAreUnmodifiable() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("key", Collections.singletonList("value"));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
            "/path",
            "key=value",
            params,
            null,
            "https://example.com/path?key=value",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertThrows(UnsupportedOperationException.class, () ->
            context.getParameters().put("new", Collections.emptyList())
        );
    }

    @Test
    void testEncodedQueryWithMultipleParameters() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("a", Collections.singletonList("1"));
        params.put("b", Collections.singletonList("2"));
        params.put("c", Collections.singletonList("3"));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
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
        assertEquals(Collections.singletonList("1"), context.getParameters().get("a"));
        assertEquals(Collections.singletonList("2"), context.getParameters().get("b"));
        assertEquals(Collections.singletonList("3"), context.getParameters().get("c"));
    }

    @Test
    void testEncodedQueryWithSpecialCharacters() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("query", Collections.singletonList("hello world"));
        params.put("path", Collections.singletonList("/a/b"));

        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
            "/search",
            "query=hello+world&path=%2Fa%2Fb",
            params,
            null,
            "https://example.com/search?query=hello+world&path=%2Fa%2Fb",
            true,
            UrlBuilder.GenerationMode.FULLY_QUALIFIED
        );

        assertEquals("query=hello+world&path=%2Fa%2Fb", context.getEncodedQuery());
        assertEquals(Collections.singletonList("hello world"), context.getParameters().get("query"));
        assertEquals(Collections.singletonList("/a/b"), context.getParameters().get("path"));
    }

    @Test
    void testNullParametersWithPopulatedEncodedQuery() {
        SigningContextImpl context = new SigningContextImpl(
            "https",
            "example.com",
            null,
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
            null,
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
