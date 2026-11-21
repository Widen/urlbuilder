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

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests demonstrating typical UrlBuilder usage patterns.
 */
class UrlBuilderTest
{
    @Test
    void typicalUsageWithPathAndParameter()
    {
        String url = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString();
        assertEquals("http://my.host.com/foo/bar?a=b", url);
    }

    @Test
    void simplePathWithoutParameters()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void replacesHostname()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").withHostname("new.host.com").toString();
        assertEquals("http://new.host.com/foo/bar/baz.html", url);
    }

    @Test
    void trimsWhitespaceFromHostname()
    {
        String url = new UrlBuilder(" my.host.com  ", "/foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void addsFragment()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").withFragment("chapter/1").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html#chapter/1", url);
    }

    @Test
    void convertsToUriAndUrl() throws Exception
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "a b").usingSsl().addParameter("q", "x&y");

        assertEquals(new URI("https://my.host.com/a%20b?q=x%26y"), builder.toURI());
        assertEquals("https://my.host.com/a%20b?q=x%26y", builder.toURL().toString());
    }

    @Test
    void rejectsRelativeUrlInToUrl()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "foo").modeHostnameRelative();

        assertThrows(IllegalArgumentException.class, builder::toURL);
    }

    @Test
    void writesDocumentedReadmeExamples()
    {
        String url = new UrlBuilder("files.example.com", "/reports/2024/")
            .usingSsl()
            .addPathSegment("Q3 summary #2.pdf")
            .addParameter("q", "cats & dogs")
            .addParameter("page", 2)
            .toString();
        assertEquals("https://files.example.com/reports/2024/Q3%20summary%20%232.pdf?q=cats%20%26%20dogs&page=2", url);

        String prefixed = new UrlBuilder("my.host.com", "users/42")
            .addPrefixedPathSegment("api/v2")
            .includeTrailingSlash()
            .toString();
        assertEquals("http://my.host.com/api/v2/users/42/", prefixed);
    }

    @Test
    void writesDocumentedChangelogEncodingExamples()
    {
        String current = new UrlBuilder("my.host.com", "a b+c@d").addParameter("q", "x y*z~").toString();
        assertEquals("http://my.host.com/a%20b+c@d?q=x%20y%2Az~", current);

        String legacy = new UrlBuilder("my.host.com", "a b+c@d")
            .usingLegacyEncoding()
            .addParameter("q", "x y*z~")
            .toString();
        assertEquals("http://my.host.com/a%20b%2Bc%40d?q=x%20y*z%7E", legacy);
    }
}
