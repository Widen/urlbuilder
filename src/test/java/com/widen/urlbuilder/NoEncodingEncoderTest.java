package com.widen.urlbuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoEncodingEncoderTest {
    
    @Test
    void testEncodeReturnsOriginalString() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "hello+world=test/abc";
        assertEquals(input, encoder.encode(input));
    }
    
    @Test
    void testDecodeReturnsOriginalString() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "hello+world=test/abc";
        assertEquals(input, encoder.decode(input));
    }
    
    @Test
    void testEncodeWithSpecialCharacters() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "abc123!@#$%^&*()_+-=[]{}|;:',.<>?/~`";
        assertEquals(input, encoder.encode(input));
    }
    
    @Test
    void testEncodeWithUnicode() {
        NoEncodingEncoder encoder = NoEncodingEncoder.INSTANCE;
        String input = "hello世界🌍";
        assertEquals(input, encoder.encode(input));
    }
    
    @Test
    void testInstanceIsSingleton() {
        NoEncodingEncoder instance1 = NoEncodingEncoder.INSTANCE;
        NoEncodingEncoder instance2 = NoEncodingEncoder.INSTANCE;
        assertSame(instance1, instance2);
    }
}
