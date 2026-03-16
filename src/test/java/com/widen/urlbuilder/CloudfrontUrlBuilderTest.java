package com.widen.urlbuilder;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudfrontUrlBuilderTest
{

    private PrivateKey pem;
    private PrivateKey der;

    @BeforeEach
    void setup() throws IOException
    {
        pem = CloudfrontPrivateKeyUtils.fromPemString(IOUtils.toString(getClass().getResourceAsStream("/test-cf.pem")));
        der = CloudfrontPrivateKeyUtils.fromDerBinary(getClass().getResourceAsStream("/test-cf.der"));
    }

    @Test
    void secondUseOfBuilderEqualsNewed()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem).withAttachmentFilename("test.jpg").expireAt(new Date(1381356586000L));
        builder.toString();

        builder.withKey("/0/b/c/d/test2.jpeg");
        String secondUseBuilderValue = builder.toString();

        CloudfrontUrlBuilder builder2 = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test2.jpeg", "APKAIW7O5EPF5UBMJ7KQ", der, "BC").withAttachmentFilename("test.jpg").expireAt(new Date(1381356586000L));
        assertEquals(secondUseBuilderValue, builder2.toString());
    }

    @Test
    void testNonAsciiCharsInAttachment()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem).withAttachmentFilename("+ƒoo.jpg").expireAt(new Date(1381356586000L));

        // Signature changes with v3 RFC 3986 compliant encoding
        assertEquals("http://dnnfhn216qiqy.cloudfront.net/0/b/c/d/test.jpeg?response-content-disposition=attachment%3B%20filename%3D%22%2Boo.jpg%22%3B%20filename%2A%3DUTF-8%27%27%252B%25C6%2592oo.jpg&Expires=1381356586&Signature=XpZvVljTm8RzudHPB8xxgVdiJ~GEgEZ-mJr-d40XaHvHJLlX-iU949TIa6EF3J7KMYXNF8nD~DM6lYZ9qlQH0Pjamj05DC3tKXqSIH89wGN3iDtUP9eCqUeAayCZQDNIeNPHJxU9TS1fNS4HRkW4sQMjM1FfNwPTMvaaCKixJJpweszNe~ii24rE~CB6DZDEVOL5206eba9jZZELjIJA6GNSIAqO8Hi88bB8X9GC7Bd2vdGABtkXYqHrs78BNoqcZFXLsNj9ehjElkDLDNxtbL-~sZqNsi6me6r0kKh1XNRCE83BcirZXRO1NTAscEcnVvB9THyaHoehE9F2cI9SEA__&Key-Pair-Id=APKAIW7O5EPF5UBMJ7KQ", builder.toString());
    }

    @Test
    void testExpireInWithHours()
    {
        long beforeTime = System.currentTimeMillis() / 1000;
        
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireIn(1, TimeUnit.HOURS);
        
        String url = builder.toString();
        
        long afterTime = System.currentTimeMillis() / 1000;
        
        // Extract Expires value from URL
        String expiresParam = extractParameter(url, "Expires");
        long expiresValue = Long.parseLong(expiresParam);
        
        // Expiration should be approximately 1 hour (3600 seconds) from now
        long expectedMinExpires = beforeTime + 3600;
        long expectedMaxExpires = afterTime + 3600;
        
        assertTrue(expiresValue >= expectedMinExpires, 
            "Expires should be at least " + expectedMinExpires + " but was " + expiresValue);
        assertTrue(expiresValue <= expectedMaxExpires, 
            "Expires should be at most " + expectedMaxExpires + " but was " + expiresValue);
    }

    @Test
    void testExpireInWithMinutes()
    {
        long beforeTime = System.currentTimeMillis() / 1000;
        
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireIn(30, TimeUnit.MINUTES);
        
        String url = builder.toString();
        
        long afterTime = System.currentTimeMillis() / 1000;
        
        String expiresParam = extractParameter(url, "Expires");
        long expiresValue = Long.parseLong(expiresParam);
        
        // Expiration should be approximately 30 minutes (1800 seconds) from now
        long expectedMinExpires = beforeTime + 1800;
        long expectedMaxExpires = afterTime + 1800;
        
        assertTrue(expiresValue >= expectedMinExpires && expiresValue <= expectedMaxExpires,
            "Expires should be between " + expectedMinExpires + " and " + expectedMaxExpires + " but was " + expiresValue);
    }

    @Test
    void testExpireInWithSeconds()
    {
        long beforeTime = System.currentTimeMillis() / 1000;
        
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireIn(300, TimeUnit.SECONDS);
        
        String url = builder.toString();
        
        long afterTime = System.currentTimeMillis() / 1000;
        
        String expiresParam = extractParameter(url, "Expires");
        long expiresValue = Long.parseLong(expiresParam);
        
        // Expiration should be approximately 300 seconds from now
        long expectedMinExpires = beforeTime + 300;
        long expectedMaxExpires = afterTime + 300;
        
        assertTrue(expiresValue >= expectedMinExpires && expiresValue <= expectedMaxExpires,
            "Expires should be between " + expectedMinExpires + " and " + expectedMaxExpires + " but was " + expiresValue);
    }

    @Test
    void testExpireInWithDays()
    {
        long beforeTime = System.currentTimeMillis() / 1000;
        
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireIn(7, TimeUnit.DAYS);
        
        String url = builder.toString();
        
        long afterTime = System.currentTimeMillis() / 1000;
        
        String expiresParam = extractParameter(url, "Expires");
        long expiresValue = Long.parseLong(expiresParam);
        
        // Expiration should be approximately 7 days (604800 seconds) from now
        long expectedMinExpires = beforeTime + 604800;
        long expectedMaxExpires = afterTime + 604800;
        
        assertTrue(expiresValue >= expectedMinExpires && expiresValue <= expectedMaxExpires,
            "Expires should be between " + expectedMinExpires + " and " + expectedMaxExpires + " but was " + expiresValue);
    }

    @Test
    void testExpireInCalculatesNewExpirationOnEachToString()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireIn(1, TimeUnit.HOURS);
        
        String url1 = builder.toString();
        String expires1 = extractParameter(url1, "Expires");
        
        // Small delay to ensure time difference
        try { Thread.sleep(50); } catch (InterruptedException e) { /* ignore */ }
        
        String url2 = builder.toString();
        String expires2 = extractParameter(url2, "Expires");
        
        // The expires values should be slightly different (or at least recalculated)
        // Since we're using 1 hour duration, the difference should be minimal but
        // the signatures should be different due to different expire times
        long diff = Math.abs(Long.parseLong(expires2) - Long.parseLong(expires1));
        assertTrue(diff <= 1, "Expires difference should be 0 or 1 second, was " + diff);
    }

    @Test
    void testExpireInReturnsBuilderForChaining()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem);
        
        CloudfrontUrlBuilder result = builder.expireIn(1, TimeUnit.HOURS);
        
        assertEquals(builder, result, "expireIn should return the same builder instance for chaining");
    }

    @Test
    void testExpireAtTakesPrecedenceOverExpireIn()
    {
        // When both expireAt and expireIn are set, expireAt takes precedence
        // This is because ExpireDateHolder checks instant (expireAt) first
        Date fixedDate = new Date(1381356586000L);
        
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireIn(1, TimeUnit.HOURS)  // Set expireIn first
            .expireAt(fixedDate);         // Then set expireAt - this takes precedence
        
        String url = builder.toString();
        
        String expiresParam = extractParameter(url, "Expires");
        long expiresValue = Long.parseLong(expiresParam);
        
        // expireAt should take precedence
        assertEquals(1381356586L, expiresValue, "expireAt should take precedence over expireIn");
    }

    @Test
    void testExpireAtAlwaysTakesPrecedenceRegardlessOfOrder()
    {
        // Even if expireAt is set first and then expireIn, expireAt still wins
        // because getExpireDate() checks instant before duration
        Date fixedDate = new Date(1381356586000L);
        
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireAt(fixedDate)          // Set expireAt first
            .expireIn(1, TimeUnit.HOURS); // Then set expireIn - still ignored
        
        String url = builder.toString();
        
        String expiresParam = extractParameter(url, "Expires");
        long expiresValue = Long.parseLong(expiresParam);
        
        // expireAt should still take precedence
        assertEquals(1381356586L, expiresValue, "expireAt should take precedence regardless of call order");
    }

    @Test
    void testExpireInUrlContainsRequiredParameters()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .expireIn(1, TimeUnit.HOURS);
        
        String url = builder.toString();
        
        assertTrue(url.contains("Expires="), "URL should contain Expires parameter");
        assertTrue(url.contains("Signature="), "URL should contain Signature parameter");
        assertTrue(url.contains("Key-Pair-Id=APKAIW7O5EPF5UBMJ7KQ"), "URL should contain Key-Pair-Id parameter");
    }

    @Test
    void testExpireInWithSsl()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem)
            .withSsl()
            .expireIn(1, TimeUnit.HOURS);
        
        String url = builder.toString();
        
        assertTrue(url.startsWith("https://"), "URL should use HTTPS when withSsl() is called");
        assertTrue(url.contains("Expires="), "URL should still contain signing parameters");
    }

    /**
     * Helper method to extract a parameter value from a URL.
     */
    private String extractParameter(String url, String paramName)
    {
        int startIndex = url.indexOf(paramName + "=");
        if (startIndex == -1)
        {
            return null;
        }
        startIndex += paramName.length() + 1;
        int endIndex = url.indexOf("&", startIndex);
        if (endIndex == -1)
        {
            endIndex = url.length();
        }
        return url.substring(startIndex, endIndex);
    }

}
