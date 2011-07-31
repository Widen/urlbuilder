package com.widen.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Encoder implementation that uses {@link URLEncoder} and {@link URLDecoder}
 */
public class BuiltinEncoder implements Encoder
{
	public String encode(String text)
	{
		try
		{
			String encoded = URLEncoder.encode(text, "UTF-8");
			return encoded.replace("+", "%20");
		}
		catch (UnsupportedEncodingException uee)
		{
			throw new RuntimeException("UTF-8 encoding not found.");
		}
	}

	public String decode(String text)
	{
		try
		{
			return URLDecoder.decode(text, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new RuntimeException("UTF-8 encoding not found.");
		}
	}
}
