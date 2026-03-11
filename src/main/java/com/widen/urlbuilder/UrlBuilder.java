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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API.
 * It strives simply to be more robust than manually constructing URLs by string concatenation.
 * <p>
 * <b>Typical usage:</b>
 * <pre>
 * new UrlBuilder("my.host.com", "foo")
 *     .addPathSegment("bar")
 *     .addParameter("a", "b")
 *     .toString()
 * // produces: http://my.host.com/foo/bar?a=b
 * </pre>
 * <p>
 * <b>URL Encoding:</b>
 * <ul>
 *   <li>Path segments are encoded using {@link PathSegmentEncoder} (RFC 3986 Section 3.3)</li>
 *   <li>Query parameters are encoded using {@link QueryParameterEncoder} (RFC 3986 Section 3.4)</li>
 *   <li>Custom encoders can be set via {@link #usingPathEncoder(Encoder)} and {@link #usingQueryEncoder(Encoder)}</li>
 *   <li>For v2.x backward compatibility, use {@link #usingLegacyPathEncoding()}</li>
 * </ul>
 * <p>
 * <b>Generation Modes:</b>
 * The methods {@link #modeFullyQualified()}, {@link #modeHostnameRelative()}, and {@link #modeProtocolRelative()}
 * control the URL generation format.
 *
 * @see PathSegmentEncoder
 * @see QueryParameterEncoder
 * @see GenerationMode
 */
public class UrlBuilder {
    private boolean ssl = false;

    private String hostname;

    private int port;

    private List<String> path = new ArrayList<>();

    private boolean trailingPathSlash = false;

    private String fragment;

    List<QueryParam> queryParams = new ArrayList<>();

    private GenerationMode mode = GenerationMode.HOSTNAME_RELATIVE;

    private Encoder pathEncoder = new PathSegmentEncoder();

    private Encoder queryEncoder = new QueryParameterEncoder();

    /**
     * Construct a UrlBuilder with no hostname or path.
     *
     * @see #withHostname(String)
     * @see #withPath(String)
     */
    public UrlBuilder() {
    }

    /**
     * Construct a UrlBuilder by parsing an existing URL string.
     * <p>
     * {@link java.net.URL} is used to parse the input. The resulting builder
     * will be set to {@link GenerationMode#FULLY_QUALIFIED} mode.
     *
     * @param spec the URL string to parse
     * @throws NonParsableUrl if input is not parsable into a java.net.URL object
     */
    public UrlBuilder(String spec) {
        URL url = parseUrlInput(spec);

        usingSsl(url.getProtocol().equals("https"));
        withHostname(url.getHost());
        setPort(url.getPort());
        withPath(url.getPath());

        String params = url.getQuery();
        if (params != null) {
            String[] pairs = params.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    addParameter(queryEncoder.decode(keyValue[0]), queryEncoder.decode(keyValue[1]));
                }
                else if (keyValue.length == 1) {
                    addParameter(queryEncoder.decode(keyValue[0]), "");
                }
            }
        }

        withFragment(url.getRef());

        mode = GenerationMode.FULLY_QUALIFIED;
    }

    private URL parseUrlInput(String spec) {
        try {
            return new URL(spec);
        }
        catch (MalformedURLException e) {
            throw new NonParsableUrl(e);
        }
    }

    /**
     * Exception thrown when a URL string cannot be parsed.
     *
     * @see UrlBuilder#UrlBuilder(String)
     */
    public static class NonParsableUrl extends RuntimeException {
        /**
         * Construct a NonParsableUrl exception.
         *
         * @param e the underlying MalformedURLException
         */
        public NonParsableUrl(MalformedURLException e) {
            super(e);
        }
    }

    /**
     * Construct a UrlBuilder with a hostname and an initial path.
     * <p>
     * The builder will be set to {@link GenerationMode#FULLY_QUALIFIED} mode with port 80.
     *
     * @param hostname the hostname (e.g., "example.com")
     * @param path the initial path (e.g., "foo/bar"), may be null
     */
    public UrlBuilder(String hostname, String path) {
        this(hostname, 80, path);
    }

    /**
     * Construct a UrlBuilder with a hostname, port, and an initial path.
     * <p>
     * The builder will be set to {@link GenerationMode#FULLY_QUALIFIED} mode.
     * If port is 443, SSL will be enabled automatically.
     *
     * @param hostname the hostname (e.g., "example.com")
     * @param port the port number (e.g., 8080)
     * @param path the initial path (e.g., "foo/bar"), may be null
     */
    public UrlBuilder(String hostname, int port, String path) {
        withHostname(hostname);
        setPort(port);
        withPath(path);
        mode = GenerationMode.FULLY_QUALIFIED;
    }

    /**
     * Check if SSL (HTTPS) is enabled.
     *
     * @return true if the URL will use HTTPS, false for HTTP
     */
    public boolean isSslEnabled() {
        return ssl;
    }

    /**
     * Get the configured port number.
     *
     * @return the port number, or 0 if not explicitly set
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the configured hostname.
     *
     * @return the hostname, or null if not set
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Get the full path string of the URL.
     *
     * @return The URL path.
     */
    public String getPath() {
        return "/" + StringUtilsInternal.join(path, "/");
    }

    /**
     * Get the path segments in the URL. This effectively returns the path split on "/" in an efficient way.
     *
     * @return A read-only list of path segments.
     */
    public List<String> getPathSegments() {
        return Collections.unmodifiableList(path);
    }

    /**
     * Get the URL fragment (the part after '#').
     *
     * @return the fragment, or null if not set
     */
    public String getFragment() {
        return fragment;
    }

    /**
     * Get the current URL generation mode.
     *
     * @return the generation mode
     * @see GenerationMode
     */
    public GenerationMode getMode() {
        return mode;
    }

    /**
     * Get the query parameters as a map of multiple values.
     *
     * @return A map of parameter values.
     */
    public Map<String, List<String>> getQueryParameters() {
        Map<String, List<String>> map = new LinkedHashMap<>();

        for (QueryParam queryParam : queryParams) {
            map.computeIfAbsent(queryParam.key, k -> new ArrayList<>())
                .add(queryParam.value);
        }

        return map;
    }

    /**
     * Get the query parameters formatted as a query string.
     * <p>
     * Keys and values will be URL encoded. Key-value pairs will be separated by ampersand ({@code &}).
     *
     * @return the formatted query string (without the leading {@code ?})
     */
    public String getQueryParameterString() {
        return buildParams();
    }

    /**
     * Set the hostname.
     *
     * @param hostname FQDN to be used when generating fully qualified URLs
     * @return this builder for method chaining
     */
    public UrlBuilder withHostname(String hostname) {
        this.hostname = StringUtilsInternal.trimToEmpty(hostname);
        return this;
    }

    /**
     * Enable v2.x-compatible encoding for backward compatibility.
     * <p>
     * By default, v3.x uses RFC 3986 compliant encoding which does not encode
     * characters like {@code @}, {@code :}, and sub-delimiters in path segments,
     * and uses proper query parameter encoding.
     * <p>
     * Call this method if you need to maintain URL compatibility with v2.x output,
     * for example if you have signed URLs or caches keyed by URL strings. This sets
     * both the path encoder and query encoder to use the legacy v2.x behavior.
     * <p>
     * Example:
     * <pre>
     * // v3 default: http://host.com/user@example.com
     * new UrlBuilder("host.com", "user@example.com").toString();
     * 
     * // v2 compatible: http://host.com/user%40example.com
     * new UrlBuilder("host.com", "user@example.com")
     *     .usingLegacyPathEncoding()
     *     .toString();
     * </pre>
     *
     * @return this builder for method chaining
     * @see LegacyPathEncoder
     * @since 3.0.0
     */
    @SuppressWarnings("deprecation")
    public UrlBuilder usingLegacyPathEncoding() {
        LegacyPathEncoder legacyEncoder = new LegacyPathEncoder();
        this.pathEncoder = legacyEncoder;
        this.queryEncoder = legacyEncoder;
        return this;
    }

    /**
     * Set a custom encoder for path segments.
     * <p>
     * By default, {@link PathSegmentEncoder} is used for RFC 3986 compliant encoding.
     *
     * @param encoder the encoder to use for path segments
     * @return this builder for method chaining
     * @since 3.0.0
     */
    public UrlBuilder usingPathEncoder(Encoder encoder) {
        this.pathEncoder = encoder;
        return this;
    }

    /**
     * Set a custom encoder for query parameters.
     * <p>
     * By default, {@link QueryParameterEncoder} is used for RFC 3986 compliant encoding.
     *
     * @param encoder the encoder to use for query parameters
     * @return this builder for method chaining
     * @since 3.0.0
     */
    public UrlBuilder usingQueryEncoder(Encoder encoder) {
        this.queryEncoder = encoder;
        return this;
    }

    /**
     * Set the port number.
     * <p>
     * If port is 443, SSL will be enabled automatically.
     *
     * @param port port to be appended after the hostname on fully qualified URLs
     */
    public void setPort(int port) {
        this.port = port;

        if (port == 443) {
            usingSsl();
        }
    }

    /**
     * Set path, replacing any previous path value.
     * <p>
     * Use {@link #addPathSegment(String)} to append onto the path.
     *
     * @param newPath the path string (may contain slashes)
     * @return this builder for method chaining
     */
    public UrlBuilder withPath(String newPath) {
        path = makePathSegments(newPath, true);

        return this;
    }

    /**
     * Set path from an already-encoded string, replacing any previous path value.
     * <p>
     * Unlike {@link #withPath(String)}, this method assumes the input is already URL-encoded
     * and will decode it for internal storage.
     *
     * @param newPath the URL-encoded path string
     * @return this builder for method chaining
     * @see #withPath(String)
     */
    public UrlBuilder withPathEncoded(String newPath) {
        path = makePathSegments(newPath, false);
        return this;
    }

    List<String> makePathSegments(String in, boolean decodeSegments) {
        ArrayList<String> list = new ArrayList<String>();

        if (in == null) {
            return list;
        }

        String[] split = in.split("/");

        for (String s : split) {
            if (StringUtilsInternal.isNotBlank(s)) {
                if (decodeSegments) {
                    // Store raw (decoded) segments - encoding happens at output time
                    list.add(s);
                }
                else {
                    // Input is already encoded, decode for storage
                    list.add(pathEncoder.decode(s));
                }
            }
        }

        return list;
    }

    /**
     * Encode path segments using the current path encoder.
     * This is called at output time to allow encoder changes to take effect.
     */
    private List<String> encodePathSegments() {
        List<String> encoded = new ArrayList<>(path.size());
        for (String segment : path) {
            encoded.add(pathEncoder.encode(segment));
        }
        return encoded;
    }

    /**
     * Enable SSL (HTTPS protocol).
     *
     * @return this builder for method chaining
     */
    public UrlBuilder usingSsl() {
        ssl = true;
        return this;
    }

    /**
     * Enable or disable SSL (HTTPS protocol).
     *
     * @param useSsl true to enable HTTPS, false for HTTP
     * @return this builder for method chaining
     */
    public UrlBuilder usingSsl(boolean useSsl) {
        ssl = useSsl;
        return this;
    }

    /**
     * Append a value to the path, segmented by a slash.
     * <p>
     * If necessary, a leading slash will be automatically appended.
     * Multiple consecutive slashes will be consolidated.
     * Each path segment, separated by slashes, will be individually URL encoded.
     *
     * @param value text to append to the path segment of the URL
     * @return this builder for method chaining
     */
    public UrlBuilder addPathSegment(String value) {
        if (StringUtilsInternal.isNotBlank(value)) {
            path.addAll(makePathSegments(value, true));
        }
        return this;
    }

    /**
     * Add a value to the beginning of the path.
     *
     * @param value text to prepend to the path
     * @return this builder for method chaining
     */
    public UrlBuilder addPrefixedPathSegment(String value) {
        if (StringUtilsInternal.isNotBlank(value)) {
            path.addAll(0, makePathSegments(value, true));
        }
        return this;
    }

    /**
     * By default, the path will <b>not</b> end with a trailing slash.
     *
     * @return this builder for method chaining
     */
    public UrlBuilder includeTrailingSlash() {
        trailingPathSlash = true;
        return this;
    }

    /**
     * Append parameter to the query string.
     *
     * @param key text for the query parameter key
     * @param value toString() result will be added as the value
     * @return this builder for method chaining
     */
    public UrlBuilder addParameter(String key, Object value) {
        if (StringUtilsInternal.isNotBlank(key)) {
            queryParams.add(new QueryParam(key, value != null ? value.toString() : null, queryEncoder));
        }
        return this;
    }

    /**
     * Append parameter to the query string.
     *
     * @param key text for the query parameter key
     * @param value toString() result will be added as the value
     * @param encoder encoder to use for this parameter's key and value
     * @return this builder for method chaining
     */
    public UrlBuilder addParameter(String key, Object value, Encoder encoder) {
        if (StringUtilsInternal.isNotBlank(key)) {
            queryParams.add(new QueryParam(key, value != null ? value.toString() : null, encoder));
        }
        return this;
    }

    /**
     * Append a Map of parameters to the query string. Both keys and values
     * will be escaped when added.
     *
     * @param params map where String key = query parameter key, Object value = toString() will be used as the value
     * @return this builder for method chaining
     */
    public UrlBuilder addParameters(Map<String, ?> params) {
        for (Entry<String, ?> e : params.entrySet()) {
            addParameter(e.getKey(), e.getValue().toString());
        }
        return this;
    }

    /**
     * Clear any previously added parameters.
     *
     * @return this builder for method chaining
     */
    public UrlBuilder clearParameters() {
        queryParams.clear();
        return this;
    }

    /**
     * Remove previously added query parameters by key.
     *
     * @param params parameter keys to remove
     * @return this builder for method chaining
     */
    public UrlBuilder clearParameter(String... params) {
        if (params != null) {
            List<String> remove = Arrays.asList(params);

            for (Iterator<QueryParam> iter = queryParams.iterator(); iter.hasNext(); ) {
                QueryParam next = iter.next();

                if (remove.contains(next.key)) {
                    iter.remove();
                }
            }
        }
        return this;
    }

    /**
     * Set the URL fragment (the part after '#').
     *
     * @param fragment text to appear after the '#' in the generated URL; will not be URL encoded
     * @return this builder for method chaining
     */
    public UrlBuilder withFragment(String fragment) {
        if (StringUtilsInternal.isNotBlank(fragment)) {
            this.fragment = fragment;
        }
        return this;
    }

    /**
     * Set generation mode to Protocol Relative; e.g. <code>"//my.host.com/foo/bar.html"</code>
     *
     * @return this builder for method chaining
     */
    public UrlBuilder modeProtocolRelative() {
        mode = GenerationMode.PROTOCOL_RELATIVE;
        return this;
    }

    /**
     * Set generation mode to Hostname Relative; e.g. <code>"/foo/bar.html"</code>
     *
     * @return this builder for method chaining
     */
    public UrlBuilder modeHostnameRelative() {
        mode = GenerationMode.HOSTNAME_RELATIVE;
        return this;
    }

    /**
     * Set generation mode to Fully Qualified. This is the default mode; e.g. <code>"http://my.host.com/foo/bar.html"</code>
     *
     * @return this builder for method chaining
     */
    public UrlBuilder modeFullyQualified() {
        mode = GenerationMode.FULLY_QUALIFIED;
        return this;
    }

    /**
     * Construct a {@link URI} for the current configuration.
     *
     * @return a URI representing the current builder configuration
     * @throws IllegalArgumentException if the resulting URI string violates RFC 2396
     * @see #toString()
     */
    public URI toURI() {
        return URI.create(toString());
    }

    /**
     * Construct a {@link URL} for the current configuration.
     *
     * @return a URL representing the current builder configuration
     * @throws RuntimeException if the URL cannot be constructed (wraps {@link MalformedURLException})
     * @see #toString()
     */
    public URL toURL() {
        try {
            return toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construct URL for the current configuration.
     * <p>
     * This method may be called multiple times, possibly returning different results based on current state.
     *
     * @see #modeFullyQualified()
     * @see #modeHostnameRelative()
     * @see #modeProtocolRelative()
     */
    @Override
    public String toString() {
        StringBuilder url = new StringBuilder();

        if (GenerationMode.FULLY_QUALIFIED.equals(mode) && StringUtilsInternal.isBlank(hostname)) {
            throw new IllegalArgumentException("Hostname cannot be blank when generation mode is FULLY_QUALIFIED.");
        }

        if (GenerationMode.FULLY_QUALIFIED.equals(mode)) {
            if (ssl) {
                url.append("https://").append(hostname);
            }
            else {
                url.append("http://").append(hostname);
            }
        }
        else if (GenerationMode.PROTOCOL_RELATIVE.equals(mode)) {
            url.append("//").append(hostname);
        }

        if (!GenerationMode.HOSTNAME_RELATIVE.equals(mode)) {
            if (port != 80 && port != 443 && port > 0) {
                url.append(":").append(port);
            }
        }

        url.append("/");

        if (!path.isEmpty()) {
            url.append(StringUtilsInternal.join(encodePathSegments(), "/"));

            if (trailingPathSlash) {
                url.append("/");
            }
        }

        if (!queryParams.isEmpty()) {
            url.append("?");
            url.append(buildParams());
        }

        if (StringUtilsInternal.isNotBlank(fragment)) {
            url.append("#").append(fragment);
        }

        return url.toString();
    }

    private String buildParams() {
        StringBuilder params = new StringBuilder();

        boolean first = true;

        for (QueryParam qp : queryParams) {
            if (!first) {
                params.append("&");
            }

            params.append(qp.toString());

            first = false;
        }

        return params.toString();
    }

    /**
     * URL generation mode that determines the format of the output URL.
     *
     * @see #modeFullyQualified()
     * @see #modeProtocolRelative()
     * @see #modeHostnameRelative()
     */
    public enum GenerationMode {
        /**
         * Generate a fully qualified URL with protocol and hostname.
         * <p>
         * Example: {@code http://my.host.com/foo/bar.html}
         */
        FULLY_QUALIFIED,
        /**
         * Generate a protocol-relative URL (omits http/https).
         * <p>
         * Example: {@code //my.host.com/foo/bar.html}
         */
        PROTOCOL_RELATIVE,
        /**
         * Generate a hostname-relative URL (path only).
         * <p>
         * Example: {@code /foo/bar.html}
         */
        HOSTNAME_RELATIVE
    }

    /**
     * Internal representation of a query parameter with its key, value, and encoder.
     */
    static class QueryParam {
        /** The parameter key (unencoded). */
        String key;
        /** The parameter value (unencoded). */
        String value;
        /** The encoder used to encode this parameter. */
        Encoder encoder;

        /**
         * Construct a query parameter.
         *
         * @param key the parameter key
         * @param value the parameter value
         * @param encoder the encoder to use for encoding
         */
        QueryParam(String key, String value, Encoder encoder) {
            this.key = key;
            this.value = value;
            this.encoder = encoder;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(encoder.encode(key));
            if (StringUtilsInternal.isNotBlank(value)) {
                sb.append("=").append(encoder.encode(value));
            }
            return sb.toString();
        }
    }
}
