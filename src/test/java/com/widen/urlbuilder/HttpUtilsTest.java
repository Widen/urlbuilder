package com.widen.urlbuilder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpUtilsTest
{
    @Test
    public void testCreateContentDispositionHeader()
    {
        assertEquals(
            "inline; filename=\"foo.jpg\"",
            HttpUtils.createContentDispositionHeader("inline", "foo.jpg")
        );
        assertEquals(
            "inline; filename=\"hello world.jpg\"",
            HttpUtils.createContentDispositionHeader("inline", "hello world.jpg")
        );
        assertEquals(
            "inline; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%22world.jpg",
            HttpUtils.createContentDispositionHeader("inline", "hello\"world.jpg")
        );
        assertEquals(
            "inline; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%5Cworld.jpg",
            HttpUtils.createContentDispositionHeader("inline", "hello\\world.jpg")
        );
        assertEquals(
            "inline; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%25world.jpg",
            HttpUtils.createContentDispositionHeader("inline", "hello%world.jpg")
        );
        assertEquals(
            "attachment; filename=\"+oo.jpg\"; filename*=UTF-8''%2B%C6%92oo.jpg",
            HttpUtils.createContentDispositionHeader("attachment", "+ƒoo.jpg")
        );
        assertEquals(
            "attachment; filename=\"foo.jpg\"; filename*=UTF-8''%F0%A2%83%87%F0%A2%9E%B5%F0%A2%AB%95foo%F0%A2%AD%83%F0%A2%AF%8A%F0%A2%B1%91%F0%A2%B1%95.jpg",
            HttpUtils.createContentDispositionHeader("attachment", "𢃇𢞵𢫕foo𢭃𢯊𢱑𢱕.jpg")
        );
        assertEquals(
            "attachment; filename=\"helloworld.jpg\"; filename*=UTF-8''hello%0Aworld.jpg",
            HttpUtils.createContentDispositionHeader("attachment", "hello\nworld.jpg")
        );
        assertEquals(
            "attachment; filename=\"resume.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf",
            HttpUtils.createContentDispositionHeader("attachment", "résumé.pdf")
        );
    }
}
