package com.widen.urlbuilder;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
