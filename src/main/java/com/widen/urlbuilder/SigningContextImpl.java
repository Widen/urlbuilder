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

/**
 * Immutable implementation of {@link UrlSigner.SigningContext}.
 * 
 * <p>Package-private as this is an internal implementation detail.
 * Users interact with the {@link UrlSigner.SigningContext} interface.
 * 
 * @since 3.1.0
 */
final class SigningContextImpl implements UrlSigner.SigningContext {
    
    private final String protocol;
    private final String hostname;
    private final int port;
    private final String encodedPath;
    private final String encodedQuery;
    private final String fragment;
    private final String url;
    private final boolean ssl;
    private final UrlBuilder.GenerationMode generationMode;
    
    /**
     * Creates a new signing context with all fields.
     * 
     * @param protocol The protocol (http/https) or empty string for protocol-relative
     * @param hostname The hostname
     * @param port The port number or -1 for default
     * @param encodedPath The encoded path
     * @param encodedQuery The encoded query string without leading "?"
     * @param fragment The fragment without leading "#", or null
     * @param url The complete unsigned URL
     * @param ssl True if using SSL
     * @param generationMode The generation mode
     */
    SigningContextImpl(String protocol, String hostname, int port,
                       String encodedPath, String encodedQuery, String fragment,
                       String url, boolean ssl, UrlBuilder.GenerationMode generationMode) {
        this.protocol = protocol;
        this.hostname = hostname;
        this.port = port;
        this.encodedPath = encodedPath;
        this.encodedQuery = encodedQuery;
        this.fragment = fragment;
        this.url = url;
        this.ssl = ssl;
        this.generationMode = generationMode;
    }
    
    @Override
    public String getProtocol() {
        return protocol;
    }
    
    @Override
    public String getHostname() {
        return hostname;
    }
    
    @Override
    public int getPort() {
        return port;
    }
    
    @Override
    public String getEncodedPath() {
        return encodedPath;
    }
    
    @Override
    public String getEncodedQuery() {
        return encodedQuery;
    }
    
    @Override
    public String getFragment() {
        return fragment;
    }
    
    @Override
    public String getUrl() {
        return url;
    }
    
    @Override
    public boolean isSsl() {
        return ssl;
    }
    
    @Override
    public UrlBuilder.GenerationMode getGenerationMode() {
        return generationMode;
    }
}
