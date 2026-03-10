package com.widen.urlbuilder;

/**
 * Encoder that performs no encoding, returning text values unchanged.
 * <p>
 * This encoder is useful when:
 * <ul>
 *   <li>Input is already percent-encoded and should not be double-encoded</li>
 *   <li>Testing or debugging URL construction</li>
 *   <li>Special cases where encoding must be handled externally</li>
 * </ul>
 * <p>
 * <b>Warning:</b> Using this encoder may produce invalid URLs if the input contains
 * characters that require encoding (spaces, special characters, etc.).
 *
 * @see PathSegmentEncoder
 * @see QueryParameterEncoder
 */
public class NoEncodingEncoder implements Encoder
{
    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns the input unchanged without any encoding.
     *
     * @return the input text unchanged, or null if input is null
     */
    @Override
    public String encode(String text)
    {
        return text;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns the input unchanged without any decoding.
     *
     * @return the input text unchanged, or null if input is null
     */
    @Override
    public String decode(String text)
    {
        return text;
    }
}
