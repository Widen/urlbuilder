package com.widen.util;

/**
 * GET URLs require conversion to preserve special characters. By default the {@link BuiltinEncoder} is used.
 */
public interface Encoder
{

	public String encode(String text);

	public String decode(String text);

}
