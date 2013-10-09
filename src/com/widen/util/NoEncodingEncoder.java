package com.widen.util;

/**
 *  Encoder that performs no encoding -- simply passing the text value back unmodified.
 */
public class NoEncodingEncoder implements Encoder
{

    public String encode(String text)
    {
        return text;
    }

    public String decode(String text)
    {
        return text;
    }

}
