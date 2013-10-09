package com.widen.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;

public class CloudfrontPrivateKeyUtils
{

    public static PrivateKey fromDerBinary(InputStream stream)
    {
        try
        {
            PKCS8EncodedKeySpec key = new PKCS8EncodedKeySpec(InternalUtils.toByteArray(stream));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(key);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey fromPemString(String pem)
    {
        registerBouncyCastleProvider();

        try
        {
            PEMReader reader = new PEMReader(new StringReader(pem));
            KeyPair pair = (KeyPair) reader.readObject();
            return pair.getPrivate();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void registerBouncyCastleProvider()
    {
        Security.addProvider(new BouncyCastleProvider());
    }

}
