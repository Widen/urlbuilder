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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for URL path segment handling.
 */
class UrlBuilderPathTest
{
    @Test
    void stripsLeadingSlashFromPath()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void handlesNullPath()
    {
        String url = new UrlBuilder("my.host.com", null).toString();
        assertEquals("http://my.host.com/", url);
    }

    @Test
    void addsPathSegmentsIncrementally()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", null);
        assertEquals("http://my.host.com/foo", builder.addPathSegment("foo").toString());
        assertEquals("http://my.host.com/foo/bar", builder.addPathSegment("bar").toString());
        assertEquals("http://my.host.com/foo/bar/baz", builder.addPathSegment("/baz").toString());
        assertEquals("http://my.host.com/foo/bar/baz/qux", builder.addPathSegment("//qux//").toString());
    }

    @Test
    void getPathReturnsSlashSeparatedPath()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addPrefixedPathSegment("baz");
        assertEquals("/baz/foo/bar", builder.getPath());
    }

    @Test
    void includesTrailingSlash()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar").addPathSegment("baz").includeTrailingSlash().toString();
        assertEquals("http://my.host.com/foo/bar/baz/", url);
    }

    @Test
    void includesTrailingSlashWithQueryParams()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar").addPathSegment("baz").includeTrailingSlash().addParameter("a", "b").toString();
        assertEquals("http://my.host.com/foo/bar/baz/?a=b", url);
    }

    @Test
    void resetsPathWithNewValue()
    {
        String url = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").withPath("baz").addPathSegment("qux").toString();
        assertEquals("http://my.host.com/baz/qux", url);
    }

    @Test
    void deduplicatesSlashesInPathSegment()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "/");
        builder.addPathSegment("/foo");
        assertEquals("http://my.host.com/foo", builder.toString());
    }

    @Test
    void encodesSpacesInPath()
    {
        String url = new UrlBuilder("my.host.com", "foo bar baz.html").toString();
        assertEquals("http://my.host.com/foo%20bar%20baz.html", url);
    }

    @Test
    void doesNotEncodeAtSignInPath()
    {
        String url = new UrlBuilder("my.host.com", "user@example.com").toString();
        assertEquals("http://my.host.com/user@example.com", url);
    }

    @Test
    void doesNotEncodeColonInPath()
    {
        String url = new UrlBuilder("my.host.com", "time:12:30:00").toString();
        assertEquals("http://my.host.com/time:12:30:00", url);
    }

    @Test
    void doesNotEncodeSubDelimsInPath()
    {
        // sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        String url = new UrlBuilder("my.host.com", "file!name$var&test'quote(paren)*star+plus,comma;semi=equals").toString();
        assertEquals("http://my.host.com/file!name$var&test'quote(paren)*star+plus,comma;semi=equals", url);
    }

    @Test
    void doesNotEncodeTildeInPath()
    {
        String url = new UrlBuilder("my.host.com", "~user/home").toString();
        assertEquals("http://my.host.com/~user/home", url);
    }

    @Test
    void encodesGenDelimsInPath()
    {
        // gen-delims that ARE NOT allowed unencoded in path segments: ? # [ ]
        // Note: / is allowed but treated as segment delimiter
        String url = new UrlBuilder("my.host.com", "path?query#fragment").toString();
        assertEquals("http://my.host.com/path%3Fquery%23fragment", url);
    }

    @Test
    void encodesSquareBracketsInPath()
    {
        String url = new UrlBuilder("my.host.com", "array[0]").toString();
        assertEquals("http://my.host.com/array%5B0%5D", url);
    }

    @Test
    void encodesPercentInPath()
    {
        String url = new UrlBuilder("my.host.com", "100%complete").toString();
        assertEquals("http://my.host.com/100%25complete", url);
    }
}
