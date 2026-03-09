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
}
