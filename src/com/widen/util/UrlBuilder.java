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
package com.widen.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Utility class for constructing syntactically correct HTTP URLs using a fluent method-chaining API.
 * It strives simply to be more robust then manually constructing URLs by string concatenation.
 * 
 * <p><b>Typical usage:</b>
 * <code>new UrlBuilder("my.host.com", "foo").addPathSegment("bar").addParameter("a", "b").toString()</code>
 * <b>produces</b> <code>http://my.host.com/foo/bar?a=b</code>
 * 
 * <p>The methods {@link #modeFullyQualified}, {@link #modeHostnameRelative}, {@link #modeProtocolRelative},
 * control the URL generation format.
 * 
 * @version 0.9.3
 */
public class UrlBuilder
{
	private boolean ssl = false;

	private String hostname;
	
	private int port;
	
	private List<String> path = new ArrayList<String>();
	
	private boolean trailingPathSlash = false;
	
	private String fragment;

	List<QueryParam> queryParams = new ArrayList<QueryParam>();

	private GenerationMode mode = GenerationMode.HOSTNAME_RELATIVE;

	private Encoder encoder = new BuiltinEncoder();

	/**
	 * Construct a UrlBuilder with no hostname or path.
	 * 
	 * @see #withHostname(String)
	 * @see #withPath(String)
	 */
	public UrlBuilder()
	{
	}

	/**
	 * Construct a UrlBuilder from a String.
	 *
	 * <p/>
	 * {@link java.net.URL java.net.URL} is used to parse the input.
	 * 
	 * @throws NonParsableUrl
	 * 		if input is not parsable into a java.net.URL object
	 */
	public UrlBuilder(String spec)
	{
		URL url = parseUrlInput(spec);

		usingSsl(url.getProtocol().equals("https") ? true : false);
		withHostname(url.getHost());
		setPort(url.getPort());
		withPath(url.getPath());

		String params = url.getQuery();
		if (params != null)
		{
			String[] pairs = params.split("&");
			for (String pair : pairs)
			{
				String[] keyValue = pair.split("=");
				if (keyValue.length == 2)
				{
					addParameter(decodeValue(keyValue[0]), decodeValue(keyValue[1]));
				}
			}
		}

		withFragment(url.getRef());

		mode = GenerationMode.FULLY_QUALIFIED;
	}

	private URL parseUrlInput(String spec)
	{
		try
		{
			return new URL(spec);
		}
		catch (MalformedURLException e)
		{
			throw new NonParsableUrl(e);
		}
	}

	public class NonParsableUrl extends RuntimeException
	{
		public NonParsableUrl(MalformedURLException e)
		{
			super(e);
		}
	}

	/**
	 * Construct a UrlBuilder with a hostname and an initial path.
	 */
	public UrlBuilder(String hostname, String path)
	{
		this(hostname, 80, path);
	}

	/**
	 * Construct a UrlBuilder with a hostname, port, and an initial path.
	 */
	public UrlBuilder(String hostname, int port, String path)
	{
		withHostname(hostname);
		setPort(port);
		withPath(path);
		mode = GenerationMode.FULLY_QUALIFIED;
	}


    public boolean isSslEnabled()
    {
        return ssl;
    }

    public int getPort()
    {
        return port;
    }

    public String getHostname()
    {
        return hostname;
    }

    public String getPath()
    {
        return "/" + StringUtilsInternal.join(path, "/");
    }

    public String getFragment()
    {
        return fragment;
    }

    public GenerationMode getMode()
    {
        return mode;
    }


    /**
	 * @param hostname
	 * 		FQDN to be used when generating fully qualified URLs
	 */
	public UrlBuilder withHostname(String hostname)
	{
		this.hostname = StringUtilsInternal.trimToEmpty(hostname);
		return this;
	}

	/**
	 * @param encoder
	 * 		alternative URL encoder
	 */
	public UrlBuilder usingEncoder(Encoder encoder)
	{
		this.encoder = encoder;
		return this;
	}

	/**
	 * @param port
	 * 		port to be appended after the hostname on fully qualified URLs
	 */
	public void setPort(int port)
	{
		this.port = port;
		
		if (port == 443)
		{
			usingSsl();
		}
	}

	/**
	 * Set path, replacing any previous path value.
	 * 
	 * Use {@link #addPathSegment(String)} to append onto the path
	 */
	public UrlBuilder withPath(String newPath)
	{
		path = makePathSegments(newPath);

		return this;
	}

	List<String> makePathSegments(String in)
	{
		ArrayList<String> list = new ArrayList<String>();

		if (in == null)
		{
			return list;
		}

		String[] split = in.split("/");

		for (String s : split)
		{
			if (StringUtilsInternal.isNotBlank(s))
			{
				list.add(encodeValue(s));
			}
		}

		return list;
	}

	/**
	 * URL protocol will be "https"
	 */
	public UrlBuilder usingSsl()
	{
		ssl = true;
		return this;
	}

	/**
	 * URL protocol will be "https" when useSsl = true
	 */
	public UrlBuilder usingSsl(boolean useSsl)
	{
		ssl = useSsl;
		return this;
	}

	/**
	 * Append a value to the path, segmented by a slash.
	 * If necessary, a leading slash will be automatically appended.
	 * Multiple consecutive slashes will be consolidated.
	 * Each path segment, separated by slashes, will be individually URLEncoded.
	 * 
	 * @param value
	 * 		Text to append to the path segment of the URL
	 */
	public UrlBuilder addPathSegment(String value)
	{
		if (StringUtilsInternal.isNotBlank(value))
		{
			path.addAll(makePathSegments(value));
		}

		return this;
	}

	/**
	 * Add a value to the beginning of the path.
	 *
	 * @param value
	 * @return
	 */
	public UrlBuilder addPrefixedPathSegment(String value)
	{
		if (StringUtilsInternal.isNotBlank(value))
		{
			path.addAll(0, makePathSegments(value));
		}

		return this;
	}
	
	/**
	 * By default, the path will <b>not</b> end with a trailing slash.
	 */
	public UrlBuilder includeTrailingSlash()
	{
		trailingPathSlash = true;
		return this;
	}

	/**
	 * Append parameter to the query string.
	 * @param key
	 * 		text for the query parameter key
	 * @param value
	 * 		toString() result will be added as the value
	 */
	public UrlBuilder addParameter(String key, Object value)
	{
		if (StringUtilsInternal.isNotBlank(key))
		{
			queryParams.add(new QueryParam(key, value != null ? value.toString() : null, encoder));
		}

		return this;
	}

    /**
     * Append parameter to the query string.
     * @param key
     * 		text for the query parameter key
     * @param value
     * 		toString() result will be added as the value
     * @param encoder
     *      encoder to use for this value
     */
    public UrlBuilder addParameter(String key, Object value, Encoder encoder)
    {
        if (StringUtilsInternal.isNotBlank(key))
        {
            queryParams.add(new QueryParam(key, value != null ? value.toString() : null, encoder));
        }

        return this;
    }

	/**
	 * Append a Map of parameters to the query string. Both keys and values
	 * will be escaped when added.
	 * @param params
	 * 		String key = text for the query parameter key<br/>
	 * 		Object value = toString() result at the time of  will be added as the value
	 */
	public UrlBuilder addParameters(Map<String, ?> params)
	{
		for (Entry<String, ?> e : params.entrySet())
		{
			addParameter(e.getKey(), e.getValue().toString());
		}
		return this;
	}

	/**
	 * Clear any previously added parameters.
	 */
	public UrlBuilder clearParameters()
	{
		queryParams.clear();

		return this;
	}

	/**
	 * Remove previously added query parameters
	 */
	public UrlBuilder clearParameter(String... params)
	{
		if (params != null)
		{
			List<String> remove = Arrays.asList(params);

			for(Iterator<QueryParam> iter = queryParams.iterator(); iter.hasNext();)
			{
				QueryParam next = iter.next();

				if (remove.contains(next.key))
				{
					iter.remove();
				}
			}
		}

		return this;
	}

	/**
	 * Text of query parameters as they would be append to the generated URL
	 * <ul>
	 * <li>Keys and values will be URL encoded
	 * <li>Key value pairs will be separated by an ampersand (&)
	 * </ul>
	 */
	public String getQueryParameterString()
	{
		return buildParams();
	}

	/**
	 * @param fragment
	 * 		text to appear after the '#' in the generated URL. Will be URLEncoded.
	 */
	public UrlBuilder withFragment(String fragment)
	{
		if (StringUtilsInternal.isNotBlank(fragment))
		{
			this.fragment = fragment;
		}
		return this;
	}

	/**
	 * Set generation mode to Protocol Relative; e.g. <code>"//my.host.com/foo/bar.html"</code>
	 */
	public UrlBuilder modeProtocolRelative()
	{
		mode = GenerationMode.PROTOCOL_RELATIVE;
		return this;
	}

	/**
	 * Set generation mode to Hostname Relative; e.g. <code>"/foo/bar.html"</code>
	 */
	public UrlBuilder modeHostnameRelative()
	{
		mode = GenerationMode.HOSTNAME_RELATIVE;
		return this;
	}

	/**
	 * Set generation mode to Fully Qualified. This is the default mode; e.g. <code>"http://my.host.com/foo/bar.html"</code>
	 */
	public UrlBuilder modeFullyQualified()
	{
		mode = GenerationMode.FULLY_QUALIFIED;
		return this;
	}

	/**
	 * Construct URL for the current configuration.
	 * 
	 * This method may be called multiple times, possibly returning different results based on current state.
	 * 
	 * @see #modeFullyQualified()
	 * @see #modeHostnameRelative()
	 * @see #modeProtocolRelative()
	 */
	@Override
	public String toString()
	{
		StringBuilder url = new StringBuilder();

		if (GenerationMode.FULLY_QUALIFIED.equals(mode) && StringUtilsInternal.isBlank(hostname))
		{
			throw new IllegalArgumentException("Hostname cannot be blank when generation mode is FULLY_QUALIFIED.");
		}

		if (GenerationMode.FULLY_QUALIFIED.equals(mode))
		{
			if (ssl)
			{
				url.append("https://" + hostname);
			}
			else
			{
				url.append("http://" + hostname);
			}
		}
		else if (GenerationMode.PROTOCOL_RELATIVE.equals(mode))
		{
			url.append("//" + hostname);
		}

		if(!GenerationMode.HOSTNAME_RELATIVE.equals(mode))
		{
			if (port != 80 && port != 443 && port > 0)
			{
				url.append(":" + port);
			}
		}

		url.append("/");

		if (!path.isEmpty())
		{
			url.append(StringUtilsInternal.join(path, "/"));

			if (trailingPathSlash)
			{
				url.append("/");
			}
		}

		if (!queryParams.isEmpty())
		{
			url.append("?");
			url.append(buildParams());
		}

		if (StringUtilsInternal.isNotBlank(fragment))
		{
			url.append("#" + encodeValue(fragment));
		}

		return url.toString();
	}

	private String buildParams()
	{
		StringBuilder params = new StringBuilder();

		boolean first = true;
		
		for (QueryParam qp : queryParams)
		{
			if (!first)
			{
				params.append("&");
			}
			
			params.append(qp.toString());
			
			first = false;
		}

		return params.toString();
	}

	private String encodeValue(String value)
	{
		if (value == null)
		{
			return "";
		}
		
		return encoder.encode(value);
	}

	private String decodeValue(String value)
	{
		if (value == null)
		{
			return "";
		}

		return encoder.decode(value);
	}

	public enum GenerationMode
	{
		FULLY_QUALIFIED,
		PROTOCOL_RELATIVE,
		HOSTNAME_RELATIVE
	}

	class QueryParam
	{
		String key;
		String value;
        Encoder encoder;

		QueryParam(String key, String value, Encoder encoder)
		{
			this.key = key;
			this.value = value;
            this.encoder = encoder;
        }

		@Override
		public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append(encoder.encode(key));

            if (StringUtilsInternal.isNotBlank(value))
            {
                sb.append("=").append(encoder.encode(value));
            }

            return sb.toString();
        }
    }

}
