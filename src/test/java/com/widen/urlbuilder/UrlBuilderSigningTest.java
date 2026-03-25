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

class UrlBuilderSigningTest {
    
    @Test
    void testSimpleSignerWithLambda() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .usingUrlSigner(context -> {
                return Collections.singletonMap("signature", "abc123");
            });
        
        String url = builder.toString();
        assertEquals("http://example.com/test?signature=abc123", url);
    }
    
    @Test
    void testSignerWithExistingQueryParams() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .addParameter("user", "john")
            .addParameter("id", "42")
            .usingUrlSigner(context -> {
                assertTrue(context.getEncodedQuery().contains("user=john"));
                assertTrue(context.getEncodedQuery().contains("id=42"));
                return Collections.singletonMap("signature", "xyz");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("user=john"));
        assertTrue(url.contains("id=42"));
        assertTrue(url.contains("signature=xyz"));
    }
    
    @Test
    void testSignerWithSpecialCharactersNotEncoded() {
        UrlBuilder builder = new UrlBuilder("example.com", null)
            .usingUrlSigner(context -> {
                return Collections.singletonMap("sig", "abc+123=def/xyz-_~");
            });
        
        String url = builder.toString();
        assertTrue(url.contains("sig=abc+123=def/xyz-_~"), 
            "Signature should not be URL encoded - special chars must be preserved");
    }
    
    @Test
    void testSignerReturnsMultipleParams() {
        UrlBuilder builder = new UrlBuilder("example.com", null)
            .usingUrlSigner(context -> {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("signature", "sig123");
                params.put("expires", "1234567890");
                params.put("keyid", "key-abc");
                return params;
            });
        
        String url = builder.toString();
        assertTrue(url.contains("signature=sig123"));
        assertTrue(url.contains("expires=1234567890"));
        assertTrue(url.contains("keyid=key-abc"));
    }
    
    @Test
    void testSignerReturnsEmptyMap() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .usingUrlSigner(context -> Collections.emptyMap());
        
        String url = builder.toString();
        assertEquals("http://example.com/test", url);
    }
    
    @Test
    void testSignerReturnsNull() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .usingUrlSigner(context -> null);
        
        String url = builder.toString();
        assertEquals("http://example.com/test", url);
    }
    
    @Test
    void testSignerWithNullValueInMap() {
        UrlBuilder builder = new UrlBuilder("example.com", null)
            .usingUrlSigner(context -> {
                Map<String, String> params = new HashMap<>();
                params.put("signature", "sig123");
                params.put("nullparam", null);
                return params;
            });
        
        String url = builder.toString();
        assertTrue(url.contains("signature=sig123"));
        assertFalse(url.contains("nullparam"));
    }
    
    @Test
    void testNoSignerSet() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test");
        
        String url = builder.toString();
        assertEquals("http://example.com/test", url);
    }
    
    @Test
    void testSigningContextHasCorrectData() {
        final boolean[] signerCalled = {false};
        
        UrlBuilder builder = new UrlBuilder("example.com", "/path/to/resource")
            .usingSsl()
            .addParameter("key", "value")
            .withFragment("section")
            .usingUrlSigner(context -> {
                signerCalled[0] = true;
                
                assertEquals("https", context.getProtocol());
                assertEquals("example.com", context.getHostname());
                assertEquals(-1, context.getPort());
                assertEquals("/path/to/resource", context.getEncodedPath());
                assertTrue(context.getEncodedQuery().contains("key=value"));
                assertEquals("section", context.getFragment());
                assertTrue(context.isSsl());
                assertEquals(UrlBuilder.GenerationMode.FULLY_QUALIFIED, context.getGenerationMode());
                
                String url = context.getUrl();
                assertTrue(url.startsWith("https://example.com/path/to/resource?"));
                assertTrue(url.contains("key=value"));
                assertFalse(url.contains("#section"));
                
                return Collections.singletonMap("sig", "test");
            });
        
        builder.toString();
        assertTrue(signerCalled[0], "Signer should have been called");
    }
    
    @Test
    void testSignerWithCustomPort() {
        UrlBuilder builder = new UrlBuilder("example.com", 8080, null)
            .usingUrlSigner(context -> {
                assertEquals(8080, context.getPort());
                assertTrue(context.getUrl().contains(":8080"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.contains(":8080"));
    }
    
    @Test
    void testSignerWithProtocolRelativeMode() {
        UrlBuilder builder = new UrlBuilder("example.com", null)
            .modeProtocolRelative()
            .usingUrlSigner(context -> {
                assertEquals("", context.getProtocol());
                assertEquals(UrlBuilder.GenerationMode.PROTOCOL_RELATIVE, context.getGenerationMode());
                assertTrue(context.getUrl().startsWith("//example.com"));
                return Collections.singletonMap("sig", "test");
            });
        
        String url = builder.toString();
        assertTrue(url.startsWith("//example.com"));
    }
    
    @Test
    void testMultipleToStringCallsReSignWithoutMutation() {
        final int[] callCount = {0};
        
        UrlBuilder builder = new UrlBuilder("example.com", null)
            .addParameter("original", "param")
            .usingUrlSigner(context -> {
                callCount[0]++;
                return Collections.singletonMap("sig", "call" + callCount[0]);
            });
        
        String url1 = builder.toString();
        assertTrue(url1.contains("sig=call1"));
        assertTrue(url1.contains("original=param"));
        
        String url2 = builder.toString();
        assertTrue(url2.contains("sig=call2"));
        assertTrue(url2.contains("original=param"));
        
        // CRITICAL: Verify no signature accumulation
        assertEquals(1, url1.split("sig=").length - 1, "URL1 should have exactly one sig param");
        assertEquals(1, url2.split("sig=").length - 1, "URL2 should have exactly one sig param");
        
        assertEquals(1, url1.split("original=").length - 1);
        assertEquals(1, url2.split("original=").length - 1);
        
        assertEquals(2, callCount[0]);
    }
}
