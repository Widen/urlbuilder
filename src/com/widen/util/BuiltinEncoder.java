package com.widen.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Encoder implementation that uses {@link URLEncoder}
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
}
