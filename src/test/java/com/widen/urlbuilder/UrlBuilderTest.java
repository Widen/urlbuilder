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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UrlBuilderTest
{

    @Test
    public void testTypicalUsage()
    {
        String url = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString();
        assertEquals("http://my.host.com/foo/bar?a=b", url);
    }

    @Test
    public void testFQSpec()
    {
        String url = new UrlBuilder("http://my.host.com:8080/bar?a=b#foo").toString();
        assertEquals("http://my.host.com:8080/bar?a=b#foo", url);
    }

    @Test
    public void testFQPortOnDefaultPort()
    {
        String url = new UrlBuilder("https://my.host.com/bar?a=b#foo").toString();
        assertEquals("https://my.host.com/bar?a=b#foo", url);
    }

    @Test
    public void testFQPortOnDefaultPortSecure()
    {
        String url = new UrlBuilder("https://my.host.com:443/bar?a=b#foo").toString();
        assertEquals("https://my.host.com/bar?a=b#foo", url);
    }

    @Test
    public void testFQSpecMultipleQueryParams()
    {
        String url = new UrlBuilder("https://my.host.com:8080/bar?a=b&c=d").toString();
        assertEquals("https://my.host.com:8080/bar?a=b&c=d", url);
    }

    @Test
    public void testFQSpecEncodedQueryParams()
    {
        String url = new UrlBuilder("https://my.host.com:8080/bar?a%20b=c%20d").toString();
        assertEquals("https://my.host.com:8080/bar?a%20b=c%20d", url);
    }

    @Test(expected = UrlBuilder.NonParsableUrl.class)
    public void testInvalidFQSpec()
    {
        String url = new UrlBuilder("htt").toString();
    }

    @Test
    public void testNoParams()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testNoParamsPort()
    {
        String url = new UrlBuilder("my.host.com", 8080, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com:8080/foo/bar/baz.html", url);
    }

    @Test
    public void testAutoSSLOn443()
    {
        String url = new UrlBuilder("my.host.com", 443, "foo/bar/baz.html").toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testSSLOnCustomPort()
    {
        String url = new UrlBuilder("my.host.com", 4433, "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com:4433/foo/bar/baz.html", url);
    }

    @Test
    public void testNegativeInvalidPort()
    {
        String url = new UrlBuilder("my.host.com", -99, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testPort80NotIncluded()
    {
        String url = new UrlBuilder("my.host.com", 80, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testPort80AsSSL()
    {
        String url = new UrlBuilder("my.host.com", 80, "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testSsl()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testPathLeadingPrefix()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testSingleParam()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value", url);
    }

    @Test
    public void testParamEncoding()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key has spaces", "value has spaces").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key%20has%20spaces=value%20has%20spaces", url);
    }

    @Test
    public void testMultipleParams()
    {
        String url = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").addParameter("key2", "value2").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value&key2=value2", url);

        String url2 = new UrlBuilder("my.host.com", "/foo/bar/baz.html").addParameter("key", "value").addParameter("key2", "value2").addParameter("key3", "value3").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?key=value&key2=value2&key3=value3", url2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFullyQualifiedThrowsExceptionWhenHostnameNull()
    {
        new UrlBuilder(null, "foo/bar/baz.html").modeFullyQualified().toString();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFullyQualifiedThrowsExceptionWhenHostnameBlank()
    {
        new UrlBuilder("", "foo/bar/baz.html").modeFullyQualified().toString();
    }

    @Test
    public void testReplaceHostname()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").withHostname("new.host.com").toString();
        assertEquals("http://new.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testHostnameRelative()
    {
        String url = new UrlBuilder(null, "foo/bar/baz.html").modeHostnameRelative().toString();
        assertEquals("/foo/bar/baz.html", url);
    }

    @Test
    public void testHostnameRelativeWithPort()
    {
        String url = new UrlBuilder(null, 400, "foo/bar/baz.html").modeHostnameRelative().toString();
        assertEquals("/foo/bar/baz.html", url);
    }

    @Test
    public void testProtocolRelative()
    {
        String url = new UrlBuilder("another.host.com", "foo/bar/baz.html").modeProtocolRelative().toString();
        assertEquals("//another.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testAmpersand()
    {
        String url = new UrlBuilder("my.host.com", "foo & bar").addParameter("1&2", "3&4").addParameter("a", "b&c").toString();
        assertEquals("http://my.host.com/foo%20%26%20bar?1%262=3%264&a=b%26c", url);
    }

    @Test
    public void testProtocolRelativeWithPort()
    {
        String url = new UrlBuilder("another.host.com", 400, "foo/bar/baz.html").modeProtocolRelative().toString();
        assertEquals("//another.host.com:400/foo/bar/baz.html", url);
    }

    @Test
    public void testHostnameTrimming()
    {
        String url = new UrlBuilder(" my.host.com  ", "/foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    public void testFragment()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").withFragment("chapter/1").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html#chapter/1", url);
    }

    @Test
    public void testPlusEncoding()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").addParameter("thekey", "+plus").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html?thekey=%2Bplus", url);
    }

    @Test
    public void testPathWithSpaces()
    {
        String url = new UrlBuilder("my.host.com", "foo bar baz.html").toString();
        assertEquals("http://my.host.com/foo%20bar%20baz.html", url);
    }

    @Test
    public void testNullPath()
    {
        String url = new UrlBuilder("my.host.com", null).toString();
        assertEquals("http://my.host.com/", url);
    }

    @Test
    public void testAddPathSegment()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", null);
        assertEquals("http://my.host.com/foo", builder.addPathSegment("foo").toString());
        assertEquals("http://my.host.com/foo/bar", builder.addPathSegment("bar").toString());
        assertEquals("http://my.host.com/foo/bar/baz", builder.addPathSegment("/baz").toString());
        assertEquals("http://my.host.com/foo/bar/baz/qux", builder.addPathSegment("//qux//").toString());
    }

    @Test
    public void testPathReturnsSlashSeparatedPath()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addPrefixedPathSegment("baz");
        assertEquals("/baz/foo/bar", builder.getPath());
    }

    @Test
    public void testTrailingSlashPath()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar").addPathSegment("baz").includeTrailingSlash().toString();
        assertEquals("http://my.host.com/foo/bar/baz/", url);
    }

    @Test
    public void testTrailingSlashPathWithParams()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar").addPathSegment("baz").includeTrailingSlash().addParameter("a", "b").toString();
        assertEquals("http://my.host.com/foo/bar/baz/?a=b", url);
    }

    @Test
    public void testResetPath()
    {
        String url = new UrlBuilder("my.host.com", "foo").addPathSegment("bar").withPath("baz").addPathSegment("qux").toString();
        assertEquals("http://my.host.com/baz/qux", url);
    }

    @Test
    public void testAddDuplicateSlashPathSegment()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "/");
        builder.addPathSegment("/foo");
        assertEquals("http://my.host.com/foo", builder.toString());
    }

    @Test
    public void testClearParameter()
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
    public void testNullAndEmptyValuesDoNotEmitEqualsSign()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "/foo.jpg");
        builder.addParameter("key0", null);
        builder.addParameter("key1", "");
        builder.addParameter("key2", "c");
        assertEquals("http://my.host.com/foo.jpg?key0&key1&key2=c", builder.toString());
    }

}
