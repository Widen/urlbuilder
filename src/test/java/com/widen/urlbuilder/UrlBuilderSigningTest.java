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
import java.util.function.Function;
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
    void testSignerOutputIsEncoded() {
        UrlBuilder builder = new UrlBuilder("example.com", null)
            .usingUrlSigner(context -> {
                return Collections.singletonMap("sig key", "a-_~.+/=&admin=true#x");
            });

        assertEquals("http://example.com/?sig%20key=a-_~.%2B%2F%3D%26admin%3Dtrue%23x", builder.toString());
    }

    @Test
    void testSignerCanAddParametersThatTheSignatureCovers() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .addParameter("user", "john")
            .usingUrlSigner(context -> {
                context.addParameter("expires", 1795127806L).addParameter("", "ignored");

                assertEquals("http://example.com/test?user=john&expires=1795127806", context.getStringToSign());
                assertEquals(context.getStringToSign(), context.getUrl());
                assertEquals("user=john&expires=1795127806", context.getEncodedQuery());
                assertEquals(Collections.singletonList("1795127806"), context.getParameters().get("expires"));

                return Collections.singletonMap("signature", "sig-of-" + context.getParameters().size());
            });

        String signedUrl = "http://example.com/test?user=john&expires=1795127806&signature=sig-of-2";
        assertEquals(signedUrl, builder.toString());
        assertEquals(signedUrl, builder.toString(), "the added parameter is not kept on the builder");
    }

    @Test
    void testSignerSeesEveryValueOfARepeatedParameter() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .addParameter("a", "1")
            .addParameter("a", "2")
            .addParameter("flag", null)
            .usingUrlSigner(context -> {
                context.addParameter("a", "3");

                Map<String, List<String>> parameters = context.getParameters();
                assertEquals(Arrays.asList("1", "2", "3"), parameters.get("a"));
                assertEquals(Collections.singletonList(null), parameters.get("flag"));
                assertThrows(UnsupportedOperationException.class, () -> parameters.get("a").add("4"));
                assertThrows(UnsupportedOperationException.class, () -> parameters.remove("a"));

                return Collections.emptyMap();
            });

        assertEquals("http://example.com/test?a=1&a=2&flag&a=3", builder.toString());
    }

    @Test
    void testSignerCanAddSeveralParametersAtOnce() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .usingUrlSigner(context -> {
                Map<String, Object> signed = new LinkedHashMap<>();
                signed.put("expires", 1795127806L);
                signed.put("key-id", "k2");
                context.addParameters(signed);

                assertEquals("http://example.com/test?expires=1795127806&key-id=k2", context.getStringToSign());

                return Collections.singletonMap("signature", "s");
            });

        assertEquals("http://example.com/test?expires=1795127806&key-id=k2&signature=s", builder.toString());
    }

    @Test
    void testSignerCanAddTheFirstParameter() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .usingUrlSigner(context -> {
                context.addParameter("key id", "a&b");

                assertEquals("http://example.com/test?key%20id=a%26b", context.getUrl());

                return Collections.singletonMap("signature", "s");
            });

        assertEquals("http://example.com/test?key%20id=a%26b&signature=s", builder.toString());
    }

    @Test
    void testSignerCannotAddParametersAfterReadingTheTextToSign() {
        List<Function<UrlSigner.SigningContext, String>> textToSign = Arrays.asList(
            UrlSigner.SigningContext::getStringToSign,
            UrlSigner.SigningContext::getUrl,
            UrlSigner.SigningContext::getEncodedQuery,
            UrlSigner.SigningContext::getEncodedPathAndQuery);

        for (Function<UrlSigner.SigningContext, String> read : textToSign) {
            UrlBuilder builder = new UrlBuilder("example.com", "/test")
                .usingUrlSigner(context -> {
                    String signature = read.apply(context);
                    context.addParameter("key-id", "k2");
                    return Collections.singletonMap("signature", signature);
                });

            assertThrows(IllegalStateException.class, builder::toString);
        }
    }

    @Test
    void testPathAndQueryOmitsTheHostAndIncludesAddedParameters() {
        UrlBuilder withParameters = new UrlBuilder("example.com", "/a b/c")
            .addParameter("user", "john")
            .usingUrlSigner(context -> {
                context.addParameter("expires", 1795127806L);
                return Collections.singletonMap("signed", context.getEncodedPathAndQuery());
            });
        UrlBuilder withoutParameters = new UrlBuilder("example.com", "/test")
            .usingUrlSigner(context -> Collections.singletonMap("signed", context.getEncodedPathAndQuery()));

        assertEquals("http://example.com/a%20b/c?user=john&expires=1795127806"
            + "&signed=%2Fa%2520b%2Fc%3Fuser%3Djohn%26expires%3D1795127806", withParameters.toString());
        assertEquals("http://example.com/test?signed=%2Ftest", withoutParameters.toString());
    }

    @Test
    void testSignerValuesAreWrittenWithToString() {
        UrlBuilder builder = new UrlBuilder("example.com", null)
            .usingUrlSigner(context -> {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("expires", 1795127806L);
                params.put("version", 2);
                return params;
            });

        assertEquals("http://example.com/?expires=1795127806&version=2", builder.toString());
    }

    @Test
    void testSignerOutputWithBlankKeyIsIgnored() {
        UrlBuilder builder = new UrlBuilder("example.com", "/test")
            .usingUrlSigner(context -> {
                Map<String, String> params = new HashMap<>();
                params.put(null, "a");
                params.put(" ", "b");
                return params;
            });

        assertEquals("http://example.com/test", builder.toString());
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
                assertNull(context.getPort());
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
