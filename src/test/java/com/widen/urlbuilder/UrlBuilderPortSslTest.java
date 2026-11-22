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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void keepsPort80WhenSslEnabledLater()
    {
        String url = new UrlBuilder("my.host.com", 80, "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com:80/foo/bar/baz.html", url);
    }

    @Test
    void keepsPort443WhenSslDisabledLater()
    {
        String url = new UrlBuilder("my.host.com", 443, "foo/bar/baz.html").usingSsl(false).toString();
        assertEquals("http://my.host.com:443/foo/bar/baz.html", url);
    }

    @Test
    void disablesSslOnPort80()
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", "foo/bar/baz.html").usingSsl();
        builder.setPort(80);
        assertEquals("http://my.host.com/foo/bar/baz.html", builder.toString());
    }

    @Test
    void enablesSslWithUsingSsl()
    {
        String url = new UrlBuilder("my.host.com", "foo/bar/baz.html").usingSsl().toString();
        assertEquals("https://my.host.com/foo/bar/baz.html", url);
    }

    @Test
    void leavesPortUnsetWithoutOne()
    {
        assertEquals(0, new UrlBuilder("my.host.com", "foo").getPort());
        assertEquals(0, new UrlBuilder("http://my.host.com/foo").getPort());
        assertEquals(0, new UrlBuilder("my.host.com", -99, "foo").getPort());
    }

    @ParameterizedTest
    @CsvSource({
        "https://localhost:80/app,  http://localhost/app",
        "http://localhost:443/app,  https://localhost/app",
        "https://localhost:443/app, https://localhost/app",
        "http://localhost:80/app,   http://localhost/app"
    })
    void portWinsOverParsedScheme(String spec, String expected)
    {
        assertEquals(expected, new UrlBuilder(spec).toString());
    }

    @ParameterizedTest
    @CsvSource({
        "true,  443, ",
        "true,  80,  80",
        "false, 80,  ",
        "false, 443, 443"
    })
    void reportsPortToSignerUnlessDefaultForScheme(boolean ssl, int port, Integer expectedPort)
    {
        UrlBuilder builder = new UrlBuilder("my.host.com", port, "foo").usingSsl(ssl).usingUrlSigner(context -> {
            assertEquals(expectedPort, context.getPort());
            return Collections.emptyMap();
        });

        builder.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://localhost:8443/app/page?x=1#top",
        "https://localhost:3000/",
        "https://127.0.0.1:5173/src/main.ts",
        "https://[::1]:8443/app"
    })
    void roundTripsHttpsDevServerUrl(String url)
    {
        assertEquals(url, new UrlBuilder(url).toString());
    }

    @Test
    void parsesHttpsDevServerPort()
    {
        UrlBuilder builder = new UrlBuilder("https://localhost:8443/app");
        assertEquals(8443, builder.getPort());
        assertTrue(builder.isSslEnabled());
    }

    @Test
    void includesHttpsDevServerPortInProtocolRelativeUrl()
    {
        String url = new UrlBuilder("https://localhost:8443/app").modeProtocolRelative().toString();
        assertEquals("//localhost:8443/app", url);
    }

    @Test
    void omitsHttpsDevServerPortInHostnameRelativeUrl()
    {
        String url = new UrlBuilder("https://localhost:8443/app").modeHostnameRelative().toString();
        assertEquals("/app", url);
    }

    @Test
    void signsHttpsDevServerUrlWithPort()
    {
        UrlBuilder builder = new UrlBuilder("https://localhost:8443/app?x=1").usingUrlSigner(context -> {
            assertEquals("https", context.getProtocol());
            assertEquals(Integer.valueOf(8443), context.getPort());
            assertEquals("https://localhost:8443/app?x=1", context.getUrl());
            return Collections.singletonMap("sig", "abc");
        });

        assertEquals("https://localhost:8443/app?x=1&sig=abc", builder.toString());
    }
}
