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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
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
 * <p>Several methods from <code><a href="http://commons.apache.org/lang/">org.apache.commons.lang.StringUtils</a></code>
 * are used privately and are included internally.
 * 
 * @version 0.9.1
 */
public class UrlBuilder
{
	private boolean ssl = false;

	private String hostname;
	
	private int port;
	
	private List<String> path = new ArrayList<String>();
	
	private boolean trailingPathSlash = false;
	
	private String fragment;

	private List<QueryParam> queryParams = new ArrayList<QueryParam>();

	private GenerationMode mode = GenerationMode.HOSTNAME_RELATIVE;

	/**
	 * Construct a UrlBuilder with no hostname or path.
	 * 
	 * @see #withHostname(String)
	 * @see #withPath(String)
	 */
	public UrlBuilder()
	{
		mode = GenerationMode.HOSTNAME_RELATIVE;
	}

	/**
	 * Construct a UrlBuilder with only an initial path.
	 * 
	 * @see #withHostname(String)
	 */
	public UrlBuilder(String path)
	{
		withPath(path);
		mode = GenerationMode.HOSTNAME_RELATIVE;
	}

	/**
	 * Construct a UrlBuilder with a hostname and an initial path.
	 */
	public UrlBuilder(String hostname, String path)
	{
		withHostname(hostname);
		withPath(path);
		mode = GenerationMode.FULLY_QUALIFIED;
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
			if (UrlBuilder.StringUtilsInternal.isNotBlank(s))
			{
				list.add(encode(s));
			}
		}

		return list;
	}

	public String getHostname()
	{
		return hostname;
	}

	public String getPath()
	{
		return path.toString();
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
			queryParams.add(new QueryParam(key, value.toString()));
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
	public UrlBuilder addParameters(Map<String, ? extends Object> params)
	{
		for (Entry<String, ? extends Object> e : params.entrySet())
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
			url.append("#" + encode(fragment));
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

	private static String encode(String value)
	{
		if (value == null)
		{
			return "";
		}
		
		try
		{
			String encoded = URLEncoder.encode(value, "UTF-8");
			return encoded.replace("+", "%20");
		}
		catch (UnsupportedEncodingException uee)
		{
			throw new RuntimeException("UTF-8 encoding not found.");
		}
	}
	
	private enum GenerationMode
	{
		FULLY_QUALIFIED,
		PROTOCOL_RELATIVE,
		HOSTNAME_RELATIVE;
	}

	private static class QueryParam
	{
		private String key;
		private String value;

		QueryParam(String key, String value)
		{
			this.key = key;
			this.value = value;
		}

		@Override
		public String toString()
		{
			return encode(key) + "=" + encode(value);
		}
	}
	
	protected static class StringUtilsInternal
	{
		/*
		 * Apache Commons Lang
		 * 
		 * Copyright 2010 The Apache Software Foundation
		 * 
		 * Licensed to the Apache Software Foundation (ASF) under one or more
		 * contributor license agreements.  See the NOTICE file distributed with
		 * this work for additional information regarding copyright ownership.
		 * The ASF licenses this file to You under the Apache License, Version 2.0
		 * (the "License"); you may not use this file except in compliance with
		 * the License.  You may obtain a copy of the License at
		 * 
		 *      http://www.apache.org/licenses/LICENSE-2.0
		 * 
		 * Unless required by applicable law or agreed to in writing, software
		 * distributed under the License is distributed on an "AS IS" BASIS,
		 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		 * See the License for the specific language governing permissions and
		 * limitations under the License.
		 */
		
	    /**
	     * <p>Checks if a String is whitespace, empty ("") or null.</p>
	     *
	     * <pre>
	     * StringUtils.isBlank(null)      = true
	     * StringUtils.isBlank("")        = true
	     * StringUtils.isBlank(" ")       = true
	     * StringUtils.isBlank("bob")     = false
	     * StringUtils.isBlank("  bob  ") = false
	     * </pre>
	     *
	     * @param str  the String to check, may be null
	     * @return <code>true</code> if the String is null, empty or whitespace
	     * @since 2.0
	     */
	    static boolean isBlank(String str) {
	        int strLen;
	        if (str == null || (strLen = str.length()) == 0) {
	            return true;
	        }
	        for (int i = 0; i < strLen; i++) {
	            if ((Character.isWhitespace(str.charAt(i)) == false)) {
	                return false;
	            }
	        }
	        return true;
	    }
	    
	    /**
	     * <p>Checks if a String is not empty (""), not null and not whitespace only.</p>
	     *
	     * <pre>
	     * StringUtils.isNotBlank(null)      = false
	     * StringUtils.isNotBlank("")        = false
	     * StringUtils.isNotBlank(" ")       = false
	     * StringUtils.isNotBlank("bob")     = true
	     * StringUtils.isNotBlank("  bob  ") = true
	     * </pre>
	     *
	     * @param str  the String to check, may be null
	     * @return <code>true</code> if the String is
	     *  not empty and not null and not whitespace
	     * @since 2.0
	     */
	    static boolean isNotBlank(String str)
	    {
	    	return !isBlank(str);
	    }
	    
	    /**
	     * <p>Removes control characters (char &lt;= 32) from both
	     * ends of this String returning an empty String ("") if the String
	     * is empty ("") after the trim or if it is <code>null</code>.
	     *
	     * <p>The String is trimmed using {@link String#trim()}.
	     * Trim removes start and end characters &lt;= 32.</p>
	     *
	     * <pre>
	     * StringUtils.trimToEmpty(null)          = ""
	     * StringUtils.trimToEmpty("")            = ""
	     * StringUtils.trimToEmpty("     ")       = ""
	     * StringUtils.trimToEmpty("abc")         = "abc"
	     * StringUtils.trimToEmpty("    abc    ") = "abc"
	     * </pre>
	     *
	     * @param str  the String to be trimmed, may be null
	     * @return the trimmed String, or an empty String if <code>null</code> input
	     * @since 2.0
	     */
	    static String trimToEmpty(String str) {
	        return str == null ? "" : str.trim();
	    }
	    
	    /**
	     * <p>Removes a substring only if it is at the begining of a source string,
	     * otherwise returns the source string.</p>
	     *
	     * <p>A <code>null</code> source string will return <code>null</code>.
	     * An empty ("") source string will return the empty string.
	     * A <code>null</code> search string will return the source string.</p>
	     *
	     * <pre>
	     * StringUtils.removeStart(null, *)      = null
	     * StringUtils.removeStart("", *)        = ""
	     * StringUtils.removeStart(*, null)      = *
	     * StringUtils.removeStart("www.domain.com", "www.")   = "domain.com"
	     * StringUtils.removeStart("domain.com", "www.")       = "domain.com"
	     * StringUtils.removeStart("www.domain.com", "domain") = "www.domain.com"
	     * StringUtils.removeStart("abc", "")    = "abc"
	     * </pre>
	     *
	     * @param str  the source String to search, may be null
	     * @param remove  the String to search for and remove, may be null
	     * @return the substring with the string removed if found,
	     *  <code>null</code> if null String input
	     * @since 2.1
	     */
	    static String removeStart(String str, String remove) {
	        if (isEmpty(str) || isEmpty(remove)) {
	            return str;
	        }
	        if (str.startsWith(remove)){
	            return str.substring(remove.length());
	        }
	        return str;
	    }
	    
	    /**
	     * <p>Checks if a String is empty ("") or null.</p>
	     *
	     * <pre>
	     * StringUtils.isEmpty(null)      = true
	     * StringUtils.isEmpty("")        = true
	     * StringUtils.isEmpty(" ")       = false
	     * StringUtils.isEmpty("bob")     = false
	     * StringUtils.isEmpty("  bob  ") = false
	     * </pre>
	     *
	     * <p>NOTE: This method changed in Lang version 2.0.
	     * It no longer trims the String.
	     * That functionality is available in isBlank().</p>
	     *
	     * @param str  the String to check, may be null
	     * @return <code>true</code> if the String is empty or null
	     */
	    static boolean isEmpty(String str) {
	        return str == null || str.length() == 0;
	    }

		/**
		 * <p>Joins the elements of the provided <code>Collection</code> into
		 * a single String containing the provided elements.</p>
		 * <p/>
		 * <p>No delimiter is added before or after the list.
		 * A <code>null</code> separator is the same as an empty String ("").</p>
		 * <p/>
		 *
		 * @param collection the <code>Collection</code> of values to join together, may be null
		 * @param separator  the separator character to use, null treated as ""
		 * @return the joined String, <code>null</code> if null iterator input
		 * @since 2.3
		 */
		public static String join(Collection collection, String separator)
		{
			if (collection == null)
			{
				return null;
			}
			return join(collection.iterator(), separator);
		}

		/**
		 * <p>Joins the elements of the provided <code>Iterator</code> into
		 * a single String containing the provided elements.</p>
		 * <p/>
		 * <p>No delimiter is added before or after the list.
		 * A <code>null</code> separator is the same as an empty String ("").</p>
		 * <p/>
		 *
		 * @param iterator  the <code>Iterator</code> of values to join together, may be null
		 * @param separator the separator character to use, null treated as ""
		 * @return the joined String, <code>null</code> if null iterator input
		 */
		public static String join(Iterator iterator, String separator)
		{
			// handle null, zero and one elements before building a buffer
			if (iterator == null)
			{
				return null;
			}
			if (!iterator.hasNext())
			{
				return "";
			}

			Object first = iterator.next();

			if (!iterator.hasNext())
			{
				return toString(first);
			}

			// two or more elements
			StringBuffer buf = new StringBuffer(256); // Java default is 16, probably too small
			if (first != null)
			{
				buf.append(first);
			}

			while (iterator.hasNext())
			{
				if (separator != null)
				{
					buf.append(separator);
				}
				Object obj = iterator.next();
				if (obj != null)
				{
					buf.append(obj);
				}
			}
			return buf.toString();
		}

		/**
		 * <p>Gets the <code>toString</code> of an <code>Object</code> returning
		 * an empty string ("") if <code>null</code> input.</p>
		 * <p/>
		 * <pre>
		 * ObjectUtils.toString(null)         = ""
		 * ObjectUtils.toString("")           = ""
		 * ObjectUtils.toString("bat")        = "bat"
		 * ObjectUtils.toString(Boolean.TRUE) = "true"
		 * </pre>
		 *
		 * @param obj the Object to <code>toString</code>, may be null
		 * @return the passed in Object's toString, or nullStr if <code>null</code> input
		 * @see String#valueOf(Object)
		 * @since 2.0
		 */
		public static String toString(Object obj)
		{
			return obj == null ? "" : obj.toString();
		}

	}

}
