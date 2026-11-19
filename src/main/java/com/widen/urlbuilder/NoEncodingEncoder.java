package com.widen.urlbuilder;

/**
 * Encoder that performs no encoding, returning text values unchanged.
 * <p>
 * This encoder is useful when:
 * <ul>
 *   <li>Input is already percent-encoded and should not be double-encoded</li>
 *   <li>Signatures in a URL-safe alphabet that should be preserved exactly as-is</li>
 *   <li>Testing or debugging URL construction</li>
 *   <li>Special cases where encoding must be handled externally</li>
 * </ul>
 * <p>
 * <b>Warning:</b> Using this encoder may produce invalid URLs if the input contains
 * characters that require encoding (spaces, special characters, etc.).
 * <p>
 * Example usage:
 * <pre>{@code
 * builder.addParameter("signature", urlSafeBase64Signature, NoEncodingEncoder.INSTANCE);
 * }</pre>
 *
 * @see PathSegmentEncoder
 * @see QueryParameterEncoder
 * @since 1.0.0
 */
public final class NoEncodingEncoder implements Encoder
{
    /**
     * Singleton instance for reuse.
     *
     * @since 3.0.0
     */
    public static final NoEncodingEncoder INSTANCE = new NoEncodingEncoder();

    /**
     * Creates a new NoEncodingEncoder.
     * <p>
     * Prefer using {@link #INSTANCE} singleton instead of creating new instances.
     */
    public NoEncodingEncoder()
    {
        // Default constructor - prefer INSTANCE singleton
    }
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
