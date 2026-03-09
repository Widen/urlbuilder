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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for URL output modes (fully-qualified, protocol-relative, hostname-relative).
 */
class UrlBuilderModesTest
{
    @Test
    void modeFullyQualifiedThrowsWhenHostnameNull()
    {
        assertThrows(IllegalArgumentException.class, () ->
            new UrlBuilder(null, "foo/bar/baz.html").modeFullyQualified().toString());
    }

    @Test
    void modeFullyQualifiedThrowsWhenHostnameBlank()
    {
        assertThrows(IllegalArgumentException.class, () ->
            new UrlBuilder("", "foo/bar/baz.html").modeFullyQualified().toString());
    }

    @Test
    void modeHostnameRelativeOmitsHostAndProtocol()
    {
        String url = new UrlBuilder(null, "foo/bar/baz.html").modeHostnameRelative().toString();
        assertEquals("/foo/bar/baz.html", url);
    }

    @Test
    void modeHostnameRelativeIgnoresPort()
    {
        String url = new UrlBuilder(null, 400, "foo/bar/baz.html").modeHostnameRelative().toString();
        assertEquals("/foo/bar/baz.html", url);
    }

    @Test
    void modeProtocolRelativeOmitsProtocol()
    {
        String url = new UrlBuilder("another.host.com", "foo/bar/baz.html").modeProtocolRelative().toString();
        assertEquals("//another.host.com/foo/bar/baz.html", url);
    }

    @Test
    void modeProtocolRelativeIncludesPort()
    {
        String url = new UrlBuilder("another.host.com", 400, "foo/bar/baz.html").modeProtocolRelative().toString();
        assertEquals("//another.host.com:400/foo/bar/baz.html", url);
    }
}
