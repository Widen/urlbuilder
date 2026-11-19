package com.widen.urlbuilder;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Base64;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CloudfrontPrivateKeyUtilsTest
{
    @Test
    void fromPemStringReadsPkcs8Key() throws IOException
    {
        byte[] pkcs8Der = IOUtils.toByteArray(getClass().getResourceAsStream("/test-cf.der"));
        String pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(pkcs8Der)
            + "\n-----END PRIVATE KEY-----\n";

        PrivateKey key = CloudfrontPrivateKeyUtils.fromPemString(pkcs8Pem);

        assertArrayEquals(pkcs8Der, key.getEncoded());
    }
}
