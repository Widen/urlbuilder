/*
 * Copyright 2010 Widen Enterprises, Inc. 
 * Madison, Wisconsin USA -- www.widen.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlBuilderTest
{

    @Test
    void testTypicalUsage()
    {
        String url = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString();
        assertEquals("http://my.host.com/foo/bar?a=b", url);
    }

    @Test
    void testFQSpec()
    {
        String url = new UrlBuilder("http://my.host.com:8080/bar?a=b#foo").toString();
        assertEquals("http://my.host.com:8080/bar?a=b#foo", url);
    }

    @Test
    void testFQPortOnDefaultPort()
    {
        String url = new UrlBuilder("https://my.host.com/bar?a=b#foo").toString();
        assertEquals("https://my.host.com/bar?a=b#foo", url);
    }

    @Test
    void testFQPortOnDefaultPortSecure()
    {
        String url = new UrlBuilder("https://my.host.com:443/bar?a=b#foo").toString();
        assertEquals("https://my.host.com/bar?a=b#foo", url);
    }

    @Test
    void testFQSpecMultipleQueryParams()
    {
        String url = new UrlBuilder("https://my.host.com:8080/bar?a=b&c=d").toString();
        assertEquals("https://my.host.com:8080/bar?a=b&c=d", url);
    }

    @Test
    void testFQSpecEncodedQueryParams()
    {
        String url = new UrlBuilder("https://my.host.com:8080/bar?a%20b=c%20d").toString();
        assertEquals("https://my.host.com:8080/bar?a%20b=c%20d", url);
    }

    @Test
    void testInvalidFQSpec()
    {
        assertThrows(UrlBuilder.NonParsableUrl.class, () ->
            new UrlBuilder("htt").toString());
    }

    @Test
    void testNoParams()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testNoParamsPort()
    {
        String url = new UrlBuilder("my.host.com", 8080, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com:8080/foo/bar/baz.html", url);
    }

    @Test
    void testAutoSSLOn443()
    {
        String url = new UrlBuilder("my.host.com", 443, "foo/bar/baz.html").toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testSSLOnCustomPort()
    {
        String url = new UrlBuilder("my.host.com", 4433, "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com:4433/foo/bar/baz.html", url);
    }

    @Test
    void testNegativeInvalidPort()
    {
        String url = new UrlBuilder("my.host.com", -99, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testPort80NotIncluded()
    {
        String url = new UrlBuilder("my.host.com", 80, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testPort80AsSSL()
    {
        String url = new UrlBuilder("my.host.com", 80, "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testSsl()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testPathLeadingPrefix()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testSingleParam()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value", url);
    }

    @Test
    void testParamEncoding()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key has spaces", "value has spaces").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key%20has%20spaces=value%20has%20spaces", url);
    }

    @Test
    void testMultipleParams()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").addParameter("key2", "value2").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value&key2=value2", url);

        String url2 = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").addParameter("key2", "value2").addParameter("key3", "value3").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value&key2=value2&key3=value3", url2);
    }

    @Test
    void testFullyQualifiedThrowsExceptionWhenHostnameNull()
    {
        assertThrows(IllegalArgumentException.class, () ->
            new UrlBuilder(null, "foo/bar/baz.html").modeFullyQualified().toString());
    }

    @Test
    void testFullyQualifiedThrowsExceptionWhenHostnameBlank()
    {
        assertThrows(IllegalArgumentException.class, () ->
            new UrlBuilder("", "foo/bar/baz.html").modeFullyQualified().toString());
    }

    @Test
    void testReplaceHostname()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").withHostname("new.host.com").toString();
        assertEquals("http://new.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testHostnameRelative()
    {
        String url = new UrlBuilder(null, "foo/bar/baz.html").modeHostnameRelative().toString();
        assertEquals("/foo/bar/baz.html", url);
    }

    @Test
    void testHostnameRelativeWithPort()
    {
        String url = new UrlBuilder(null, 400, "foo/bar/baz.html").modeHostnameRelative().toString();
        assertEquals("/foo/bar/baz.html", url);
    }

    @Test
    void testProtocolRelative()
    {
        String url = new UrlBuilder("another.host.com", "foo/bar/baz.html").modeProtocolRelative().toString();
        assertEquals("//another.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testAmpersand()
    {
        String url = new UrlBuilder("my.host.com", "foo & bar").addParameter("1&2", "3&4").addParameter("a", "b&c").toString();
        assertEquals("http://my.host.com/foo%20%26%20bar?1%262=3%264&a=b%26c", url);
    }

    @Test
    void testProtocolRelativeWithPort()
    {
        String url = new UrlBuilder("another.host.com", 400, "foo/bar/baz.html").modeProtocolRelative().toString();
        assertEquals("//another.host.com:400/foo/bar/baz.html", url);
    }

    @Test
    void testHostnameTrimming()
    {
        String url = new UrlBuilder(" my.host.com  ", "/foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void testFragment()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").withFragment("chapter/1").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html#chapter/1", url);
    }

    @Test
    void testPlusEncoding()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").addParameter("thekey", "+plus").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?thekey=%2Bplus", url);
    }

    @Test
    void testPathWithSpaces()
    {
        String url = new UrlBuilder("my.host.com", "foo bar baz.html").toString();
        assertEquals("http://my.host.com/foo%20bar%20baz.html", url);
    }

    @Test
    void testNullPath()
    {
        String url = new UrlBuilder("my.host.com", null).toString();
        assertEquals("http://my.host.com/", url);
    }

    @Test
    void testAddPathSegment()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", null);
        assertEquals("http://my.host.com/foo", builder.addPathSegment("foo").toString());
        assertEquals("http://my.host.com/foo/bar", builder.addPathSegment("bar").toString());
        assertEquals("http://my.host.com/foo/bar/baz", builder.addPathSegment("/baz").toString());
        assertEquals("http://my.host.com/foo/bar/baz/qux", builder.addPathSegment("//qux//").toString());
    }

    @Test
    void testPathReturnsSlashSeparatedPath()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addPrefixedPathSegment("baz");
        assertEquals("/baz/foo/bar", builder.getPath());
    }

    @Test
    void testTrailingSlashPath()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar").addPathSegment("baz").includeTrailingSlash().toString();
        assertEquals("http://my.host.com/foo/bar/baz/", url);
    }

    @Test
    void testTrailingSlashPathWithParams()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar").addPathSegment("baz").includeTrailingSlash().addParameter("a", "b").toString();
        assertEquals("http://my.host.com/foo/bar/baz/?a=b", url);
    }

    @Test
    void testResetPath()
    {
        String url = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").withPath("baz").addPathSegment("qux").toString();
        assertEquals("http://my.host.com/baz/qux", url);
    }

    @Test
    void testAddDuplicateSlashPathSegment()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "/");
        builder.addPathSegment("/foo");
        assertEquals("http://my.host.com/foo", builder.toString());
    }

    @Test
    void testClearParameter()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "/foo.jpg");
        builder.addParameter("key0", "a");
        builder.addParameter("key1", "b");
        builder.addParameter("key2", "c");
        assertEquals("http://my.host.com/foo.jpg?key0=a&key1=b&key2=c", builder.toString());
        builder.clearParameter("key0", "key2");
        assertEquals("http://my.host.com/foo.jpg?key1=b", builder.toString());
    }

    @Test
    void testNullAndEmptyValuesDoNotEmitEqualsSign()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "/foo.jpg");
        builder.addParameter("key0", null);
        builder.addParameter("key1", "");
        builder.addParameter("key2", "c");
        assertEquals("http://my.host.com/foo.jpg?key0&key1&key2=c", builder.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://my.host.com:8080/bar?a=b#foo",
        "http://my.host.com/bar?a=b#foo",
        "https://my.host.com/bar?a=b#foo",
        "https://my.host.com:8080/bar?a=b&c=d"
    })
    void roundTripParseAndToString(String url)
    {
        UrlBuilder builder = new UrlBuilder(url);
        assertEquals(url, builder.toString());
    }

    @ParameterizedTest
    @MethodSource("pathSegmentTestCases")
    void pathSegmentsAreParsedCorrectly(String url, String expectedPath, List<String> expectedSegments)
    {
        UrlBuilder builder = new UrlBuilder(url);
        assertEquals(expectedPath, builder.getPath());
        assertEquals(expectedSegments, builder.getPathSegments());
    }

    static Stream<Arguments> pathSegmentTestCases()
    {
        return Stream.of(
            Arguments.of("http://my.host.com", "/", Collections.emptyList()),
            Arguments.of("http://my.host.com/foo/bar", "/foo/bar", Arrays.asList("foo", "bar")),
            Arguments.of("http://my.host.com/foo//bar/", "/foo/bar", Arrays.asList("foo", "bar"))
        );
    }

    @Test
    void queryParametersAsMap()
    {
        UrlBuilder builder = new UrlBuilder("https://my.host.com/bar?a=x&b=2&c=3&c=4&a&d#foo");

        assertEquals(4, builder.getQueryParameters().size());
        assertEquals(Arrays.asList("x", ""), builder.getQueryParameters().get("a"));
        assertEquals(Arrays.asList("2"), builder.getQueryParameters().get("b"));
        assertEquals(Arrays.asList("3", "4"), builder.getQueryParameters().get("c"));
        assertEquals(Arrays.asList(""), builder.getQueryParameters().get("d"));
    }

}
