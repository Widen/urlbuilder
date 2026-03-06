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

/**
 * Tests for URL parsing from fully-qualified URL strings.
 */
class UrlBuilderParsingTest
{
    @Test
    void parsesFullyQualifiedUrlWithPort()
    {
        String url = new UrlBuilder("http://my.host.com:8080/bar?a=b#foo").toString();
        assertEquals("http://my.host.com:8080/bar?a=b#foo", url);
    }

    @Test
    void parsesHttpsUrlOnDefaultPort()
    {
        String url = new UrlBuilder("https://my.host.com/bar?a=b#foo").toString();
        assertEquals("https://my.host.com/bar?a=b#foo", url);
    }

    @Test
    void normalizesExplicitPort443ToImplicit()
    {
        String url = new UrlBuilder("https://my.host.com:443/bar?a=b#foo").toString();
        assertEquals("https://my.host.com/bar?a=b#foo", url);
    }

    @Test
    void parsesMultipleQueryParameters()
    {
        String url = new UrlBuilder("https://my.host.com:8080/bar?a=b&c=d").toString();
        assertEquals("https://my.host.com:8080/bar?a=b&c=d", url);
    }

    @Test
    void preservesEncodedQueryParameters()
    {
        String url = new UrlBuilder("https://my.host.com:8080/bar?a%20b=c%20d").toString();
        assertEquals("https://my.host.com:8080/bar?a%20b=c%20d", url);
    }

    @Test
    void throwsOnInvalidUrl()
    {
        assertThrows(UrlBuilder.NonParsableUrl.class, () ->
            new UrlBuilder("htt").toString());
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
