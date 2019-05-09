package com.widen.util;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CloudfrontUrlBuilderTest
{

    private PrivateKey pem;
    private PrivateKey der;

    @Before
    public void setup() throws IOException
    {
        pem = CloudfrontPrivateKeyUtils.fromPemString(IOUtils.toString(getClass().getResourceAsStream("/test-cf.pem")));
        der = CloudfrontPrivateKeyUtils.fromDerBinary(getClass().getResourceAsStream("/test-cf.der"));
    }

    @Test
    public void secondUseOfBuilderEqualsNewed()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem).withAttachmentFilename("test.jpg").expireAt(new Date(1381356586000L));
        builder.toString();

        builder.withKey("/0/b/c/d/test2.jpeg");
        String secondUseBuilderValue = builder.toString();

        CloudfrontUrlBuilder builder2 = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test2.jpeg", "APKAIW7O5EPF5UBMJ7KQ", der, "BC").withAttachmentFilename("test.jpg").expireAt(new Date(1381356586000L));
        Assert.assertEquals(secondUseBuilderValue, builder2.toString());
    }

    @Test
    public void testNonAsciiCharsInAttachment()
    {
        CloudfrontUrlBuilder builder = new CloudfrontUrlBuilder("dnnfhn216qiqy.cloudfront.net", "/0/b/c/d/test.jpeg", "APKAIW7O5EPF5UBMJ7KQ", pem).withAttachmentFilename("+ƒoo.jpg").expireAt(new Date(1381356586000L));

        Assert.assertEquals("http://dnnfhn216qiqy.cloudfront.net/0/b/c/d/test.jpeg?response-content-disposition=attachment%3B%20filename%3D%22%2Boo.jpg%22%3B%20filename*%3DUTF-8%27%27%252B%25C6%2592oo.jpg&Expires=1381356586&Signature=h8Z0hTcpvPzSxmgMjQGynOSCN-2pFTVnJQPG8bxXQ6rDWvVnVPvMOt3OrkACtLFf7NAhJbx4XpJTo3shlRYsG4E2cS5aRB6ko2N0C18hq3scySjZzLAMVLpqOTR6rK9j4Rc9dHpuZ6IlZ~qJ2xE8C516JvRXY4TLZp84WjBQZQOe6FiLuVy-sIFfAs5X1eqWgHCJKLgqBeozJlijH8jv3V1kTJADoGvOpvvKXDSjujv~u5QJ1pE6COo6vHn4PKNf4Dh-RiWU--Uqbtw26qo8fwQmBo6V4TJeQXwzWaZl74hwr7x4bUArdZLYQz892d3aHzdtZucKgIl~xMQy6kchVw__&Key-Pair-Id=APKAIW7O5EPF5UBMJ7KQ", builder.toString());
    }

}
