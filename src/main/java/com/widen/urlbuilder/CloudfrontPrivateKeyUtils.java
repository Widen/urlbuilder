package com.widen.urlbuilder;

import java.io.InputStream;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

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
            PEMParser reader = new PEMParser(new StringReader(pem));
            // A PKCS#1 key ("BEGIN RSA PRIVATE KEY") parses to a PEMKeyPair and a PKCS#8 key ("BEGIN PRIVATE KEY")
            // to a PrivateKeyInfo.
            Object pemObject = reader.readObject();
            PrivateKeyInfo keyInfo = pemObject instanceof PEMKeyPair
                ? ((PEMKeyPair) pemObject).getPrivateKeyInfo()
                : (PrivateKeyInfo) pemObject;
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyInfo.getEncoded());

            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(privateKeySpec);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void registerBouncyCastleProvider()
    {
        Security.addProvider(new BouncyCastleProvider());
    }

}
