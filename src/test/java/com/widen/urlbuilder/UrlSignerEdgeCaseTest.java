/*
 * Copyright 2019 Widen Enterprises, Inc.
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for URL signing feature.
 */
class UrlSignerEdgeCaseTest {
    
    @Test
    void testSignerThrowsException() {
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.usingUrlSigner(context -> {
            throw new RuntimeException("Signing failed");
        });
        
        assertThrows(RuntimeException.class, () -> builder.toString());
    }
    
    @Test
    void testSignerWithUnicodeCharacters() {
        UrlBuilder builder = new UrlBuilder("example.com", "/文件");
        builder.usingUrlSigner(context -> {
            // Path should be encoded in context
            assertTrue(context.getEncodedPath().contains("%"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithEmptyPath() {
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.usingUrlSigner(context -> {
            assertEquals("/", context.getEncodedPath());
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithFragmentOnly() {
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.withFragment("section");
        builder.usingUrlSigner(context -> {
            assertEquals("section", context.getFragment());
            // URL should not include fragment for signing
            assertFalse(context.getUrl().contains("#"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertTrue(url.contains("#section"));
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithProtocolRelativeUrl() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test");
        builder.modeProtocolRelative();
        builder.usingUrlSigner(context -> {
            assertEquals("", context.getProtocol());
            assertEquals(UrlBuilder.GenerationMode.PROTOCOL_RELATIVE, context.getGenerationMode());
            assertTrue(context.getUrl().startsWith("//example.com"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertTrue(url.startsWith("//example.com"));
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithHostnameRelativeUrl() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test");
        builder.modeHostnameRelative();
        builder.usingUrlSigner(context -> {
            assertEquals(UrlBuilder.GenerationMode.HOSTNAME_RELATIVE, context.getGenerationMode());
            assertTrue(context.getUrl().startsWith("/test"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertEquals("/test?sig=test", url);
    }
    
    @Test
    void testSignerWithNonStandardPort() {
        UrlBuilder builder = new UrlBuilder("example.com", 8080, "");
        builder.usingUrlSigner(context -> {
            assertEquals(8080, context.getPort());
            assertTrue(context.getUrl().contains(":8080"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertTrue(url.contains(":8080"));
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testSignerWithStandardHttpPort() {
        UrlBuilder builder = new UrlBuilder("example.com", 80, "");
        builder.usingUrlSigner(context -> {
            // Standard port should not appear in URL
            assertFalse(context.getUrl().contains(":80"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertFalse(url.contains(":80"));
    }
    
    @Test
    void testSignerWithStandardHttpsPort() {
        UrlBuilder builder = new UrlBuilder("example.com", 443, "");
        builder.usingSsl();
        builder.usingUrlSigner(context -> {
            // Standard HTTPS port should not appear in URL
            assertFalse(context.getUrl().contains(":443"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertFalse(url.contains(":443"));
    }
    
    @Test
    void testSignerWithSpecialCharactersInQuery() {
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.addParameter("key", "value with spaces");
        builder.usingUrlSigner(context -> {
            // Query should be encoded
            assertTrue(context.getEncodedQuery().contains("%20") ||
                      context.getEncodedQuery().contains("+"));
            return Collections.singletonMap("sig", "test");
        });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=test"));
    }
    
    @Test
    void testMultipleSignersNotSupported() {
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.usingUrlSigner(context -> Collections.singletonMap("sig1", "test1"));
        builder.usingUrlSigner(context -> Collections.singletonMap("sig2", "test2"));
        
        String url = builder.toString();
        
        // Last signer wins
        assertTrue(url.contains("sig2=test2"));
        assertFalse(url.contains("sig1=test1"));
    }
    
    @Test
    void testSignerCanBeCleared() {
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.usingUrlSigner(context -> Collections.singletonMap("sig", "test"));
        builder.usingUrlSigner(null);
        
        String url = builder.toString();
        assertFalse(url.contains("sig="));
    }
    
    @Test
    void testSignerWithVeryLongSignature() {
        String longSignature = String.join("", Collections.nCopies(1000, "a"));
        
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.usingUrlSigner(context -> Collections.singletonMap("sig", longSignature));
        
        String url = builder.toString();
        assertTrue(url.contains("sig=" + longSignature));
    }
    
    @Test
    void testSignerWithEmptyStringValue() {
        UrlBuilder builder = new UrlBuilder("example.com", "");
        builder.usingUrlSigner(context -> Collections.singletonMap("sig", ""));
        
        String url = builder.toString();
        assertTrue(url.contains("sig="));
    }
}
