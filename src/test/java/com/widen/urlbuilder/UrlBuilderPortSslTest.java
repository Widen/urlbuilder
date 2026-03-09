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
 * Tests for port and SSL/TLS handling.
 */
class UrlBuilderPortSslTest
{
    @Test
    void includesCustomPort()
    {
        String url = new UrlBuilder("my.host.com", 8080, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com:8080/foo/bar/baz.html", url);
    }

    @Test
    void autoEnablesSslOnPort443()
    {
        String url = new UrlBuilder("my.host.com", 443, "foo/bar/baz.html").toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void includesCustomPortWithSsl()
    {
        String url = new UrlBuilder("my.host.com", 4433, "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com:4433/foo/bar/baz.html", url);
    }

    @Test
    void ignoresNegativePort()
    {
        String url = new UrlBuilder("my.host.com", -99, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void omitsDefaultPort80()
    {
        String url = new UrlBuilder("my.host.com", 80, "foo/bar/baz.html").toString();
        assertEquals("http://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void omitsPort80WhenSslEnabled()
    {
        String url = new UrlBuilder("my.host.com", 80, "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void enablesSslWithUsingSsl()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }
}
