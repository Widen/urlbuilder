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
 * Tests for query parameter handling.
 */
class UrlBuilderQueryParamsTest
{
    @Test
    void addsSingleParameter()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value", url);
    }

    @Test
    void encodesSpacesInParameters()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key has spaces", "value has spaces").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key%20has%20spaces=value%20has%20spaces", url);
    }

    @Test
    void addsMultipleParameters()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").addParameter("key2", "value2").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value&key2=value2", url);

        String url2 = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").addParameter("key2", "value2").addParameter("key3", "value3").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value&key2=value2&key3=value3", url2);
    }

    @Test
    void clearsSpecificParameters()
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
    void nullAndEmptyValuesOmitEqualsSign()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "/foo.jpg");
        builder.addParameter("key0", null);
        builder.addParameter("key1", "");
        builder.addParameter("key2", "c");
        assertEquals("http://my.host.com/foo.jpg?key0&key1&key2=c", builder.toString());
    }

    @Test
    void encodesPlusSign()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").addParameter("thekey", "+plus").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?thekey=%2Bplus", url);
    }

    @Test
    void encodesAmpersandInPathAndParameters()
    {
        // RFC 3986: & is a sub-delim, allowed unencoded in paths but must be encoded in query params
        String url = new UrlBuilder("my.host.com", "foo & bar").addParameter("1&2", "3&4").addParameter("a", "b&c").toString();
        assertEquals("http://my.host.com/foo%20&%20bar?1%262=3%264&a=b%26c", url);
    }

    @Test
    void encodesAtSignInQueryParameter()
    {
        String url = new UrlBuilder("my.host.com", "/path")
            .addParameter("email", "user@example.com")
            .toString();
        assertEquals("http://my.host.com/path?email=user%40example.com", url);
    }

    @Test
    void encodesColonInQueryParameter()
    {
        String url = new UrlBuilder("my.host.com", "/path")
            .addParameter("time", "12:30:00")
            .toString();
        assertEquals("http://my.host.com/path?time=12%3A30%3A00", url);
    }

    @Test
    void encodesSubDelimsInQueryParameter()
    {
        String url = new UrlBuilder("my.host.com", "/path")
            .addParameter("special", "a!b$c")
            .toString();
        assertEquals("http://my.host.com/path?special=a%21b%24c", url);
    }

    @Test
    void pathAndQueryEncodeDifferently()
    {
        // Same content in path vs query should encode differently
        String url = new UrlBuilder("my.host.com", "user@host:8080")
            .addParameter("ref", "user@host:8080")
            .toString();
        // Path: @ and : not encoded (RFC 3986)
        // Query: @ and : are encoded
        assertEquals("http://my.host.com/user@host:8080?ref=user%40host%3A8080", url);
    }
}
