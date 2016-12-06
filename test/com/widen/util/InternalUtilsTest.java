package com.widen.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InternalUtilsTest
{
    @Test
    public void testCleanAttachmentFilename()
    {
        assertEquals("foo.jpg", InternalUtils.cleanAttachmentFilename("foo.jpg"));
        assertEquals("+oo.jpg", InternalUtils.cleanAttachmentFilename("+ƒoo.jpg"));
        assertEquals("foo.jpg", InternalUtils.cleanAttachmentFilename("𢃇𢞵𢫕foo𢭃𢯊𢱑𢱕.jpg"));
        assertEquals("helloworld.jpg", InternalUtils.cleanAttachmentFilename("hello\nworld.jpg"));
        assertEquals("resume.pdf", InternalUtils.cleanAttachmentFilename("résumé.pdf"));
    }
}
