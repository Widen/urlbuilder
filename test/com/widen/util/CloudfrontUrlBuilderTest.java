package com.widen.util;

import java.io.FileInputStream;
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
        pem = CloudfrontPrivateKeyUtils.fromPemString(IOUtils.toString(new FileInputStream("test-resources/test-cf.pem")));
        der = CloudfrontPrivateKeyUtils.fromDerBinary(new FileInputStream("test-resources/test-cf.der"));
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

        Assert.assertEquals("http://dnnfhn216qiqy.cloudfront.net/0/b/c/d/test.jpeg?response-content-disposition=attachment%3B%20filename%3D%22%2Boo.jpg%22&Expires=1381356586&Signature=RCox6ROYo4adkdPkG-6HFIoAcyQLh~aih4wt4yfplKVzQBFXN2hUQfTN17w6~IayG9sYKKA11iNEWFJRGXZzlE32TUDFNW4x6ETir9mlPsq5EKeKzRpZQluQhdW1TSfDyvPQNnMdObCt3MZCpl~BJKfG6FJtyFWot2~ISGU-URQBq0ItnLzBkigmM4a2xKI2NQ0W-bIEqowfigBvaf-GBtNrknXt7sRyc6mE1XGxh4zh-9t1TJDRH3EvzzJWpATUJrh8D69kuv7BoJ~jKWMrbXFSXHDQjP1ZFAPAY9fSjaoyrPh1AlYA1s6qruGPcr7JmiVoUiAakDzQmq92xjdGnA__&Key-Pair-Id=APKAIW7O5EPF5UBMJ7KQ", builder.toString());
    }

}
