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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link UrlSigner.SigningContext}.
 *
 * <p>The values given to the constructor describe the builder's URL and never change.
 * {@link #addParameter(String, Object)} appends to them, and the query string, the parameter map,
 * and the URL are computed from both. Reading the URL or the query string closes the context to
 * further parameters.
 *
 * <p>Package-private as this is an internal implementation detail.
 * Users interact with the {@link UrlSigner.SigningContext} interface.
 *
 * @since 3.0.0
 */
final class SigningContextImpl implements UrlSigner.SigningContext {
    
    private final String protocol;
    private final String hostname;
    private final Integer port;
    private final String encodedPath;
    private final String encodedQuery;
    private final Map<String, String> parameters;
    private final String fragment;
    private final String url;
    private final boolean ssl;
    private final UrlBuilder.GenerationMode generationMode;
    private final Encoder queryEncoder;
    private final List<UrlBuilder.QueryParam> addedParameters = new ArrayList<>();
    private boolean textToSignRead;

    /**
     * Creates a new signing context that encodes added parameters with {@link QueryParameterEncoder}.
     * 
     * @param protocol The protocol (http/https) or empty string for protocol-relative
     * @param hostname The hostname
     * @param port The port number or {@code null} for default
     * @param encodedPath The encoded path
     * @param encodedQuery The encoded query string without leading "?"
     * @param parameters The raw (unencoded) query parameters map
     * @param fragment The fragment without leading "#", or null
     * @param url The complete unsigned URL
     * @param ssl True if using SSL
     * @param generationMode The generation mode
     */
    SigningContextImpl(String protocol, String hostname, Integer port,
                       String encodedPath, String encodedQuery, Map<String, String> parameters,
                       String fragment,
                       String url, boolean ssl, UrlBuilder.GenerationMode generationMode) {
        this(protocol, hostname, port, encodedPath, encodedQuery, parameters, fragment, url, ssl, generationMode,
            new QueryParameterEncoder());
    }

    /**
     * Creates a new signing context.
     *
     * @param queryEncoder The encoder for parameters added with {@link #addParameter(String, Object)}
     */
    SigningContextImpl(String protocol, String hostname, Integer port,
                       String encodedPath, String encodedQuery, Map<String, String> parameters,
                       String fragment,
                       String url, boolean ssl, UrlBuilder.GenerationMode generationMode, Encoder queryEncoder) {
        this.protocol = protocol;
        this.hostname = hostname;
        this.port = port;
        this.encodedPath = encodedPath;
        this.encodedQuery = encodedQuery;
        this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : Collections.emptyMap();
        this.fragment = fragment;
        this.url = url;
        this.ssl = ssl;
        this.generationMode = generationMode;
        this.queryEncoder = queryEncoder;
    }

    @Override
    public UrlSigner.SigningContext addParameter(String name, Object value) {
        if (textToSignRead) {
            throw new IllegalStateException("Cannot add parameter '" + name
                + "': the signer has already read the text to sign, so the signature would not cover it.");
        }

        UrlBuilder.addQueryParam(addedParameters, name, value, queryEncoder);
        return this;
    }

    /**
     * @return the parameters added with {@link #addParameter(String, Object)}, in the order they were added
     */
    List<UrlBuilder.QueryParam> getAddedParameters() {
        return addedParameters;
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
    public Integer getPort() {
        return port;
    }
    
    @Override
    public String getEncodedPath() {
        return encodedPath;
    }
    
    @Override
    public String getEncodedQuery() {
        textToSignRead = true;
        return withAddedParameters(encodedQuery, "");
    }

    /**
     * @param text the unsigned URL, or a part of it, that ends with the builder's query string
     * @param queryStart the text that starts the query string when the builder's query string is empty
     * @return the text followed by the added parameters
     */
    private String withAddedParameters(String text, String queryStart) {
        if (addedParameters.isEmpty()) {
            return text;
        }

        String separator = encodedQuery.isEmpty() ? queryStart : "&";
        return text + separator + UrlBuilder.buildParams(addedParameters, queryEncoder);
    }
    
    @Override
    public Map<String, String> getParameters() {
        if (addedParameters.isEmpty()) {
            return parameters;
        }

        Map<String, String> all = new LinkedHashMap<>(parameters);
        for (UrlBuilder.QueryParam added : addedParameters) {
            all.put(added.key, added.value);
        }
        return Collections.unmodifiableMap(all);
    }
    
    @Override
    public String getFragment() {
        return fragment;
    }
    
    @Override
    public String getUrl() {
        textToSignRead = true;
        return withAddedParameters(url, "?");
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
