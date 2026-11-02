package com.example.nativesso.superapp;

import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;

/** Android-side DPoP key only. OAuth client assertions are signed by native-sso-signer. */
public final class CryptoJwt {
    private static final String ALIAS = "native_sso_dpop_key";
    private static final String KS = "AndroidKeyStore";
    private CryptoJwt() {}

    public static void ensureKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS); ks.load(null);
        if (ks.containsAlias(ALIAS)) return;
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC", KS);
        g.initialize(new android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_SIGN | android.security.keystore.KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256).build());
        g.generateKeyPair();
    }

    public static String dpopProof(String htu, String htm) throws Exception {
        ensureKey();
        JSONObject header = new JSONObject().put("typ", "dpop+jwt").put("alg", "ES256").put("jwk", publicJwk());
        JSONObject payload = new JSONObject().put("jti", UUID.randomUUID().toString()).put("htm", htm)
                .put("htu", htu).put("iat", System.currentTimeMillis() / 1000L);
        return sign(header, payload, getPrivateKey());
    }

    public static String publicJwkString() throws Exception { return publicJwk().toString(); }
    public static String jkt() throws Exception {
        JSONObject j = publicJwk();
        String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + j.getString("x") + "\",\"y\":\"" + j.getString("y") + "\"}";
        return b64(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static JSONObject publicJwk() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS); ks.load(null);
        ECPublicKey pub = (ECPublicKey) ks.getCertificate(ALIAS).getPublicKey();
        int len = 32;
        byte[] x = unsigned(pub.getW().getAffineX().toByteArray(), len), y = unsigned(pub.getW().getAffineY().toByteArray(), len);
        return new JSONObject().put("kty", "EC").put("crv", "P-256").put("kid", "native-sso-dpop-key").put("x", b64(x)).put("y", b64(y));
    }
    private static PrivateKey getPrivateKey() throws Exception { KeyStore ks=KeyStore.getInstance(KS); ks.load(null); return (PrivateKey) ks.getKey(ALIAS,null); }
    private static String sign(JSONObject h, JSONObject p, PrivateKey key) throws Exception {
        String a = b64(h.toString().getBytes(StandardCharsets.UTF_8)); String b = b64(p.toString().getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSA"); s.initSign(key); s.update((a+"."+b).getBytes(StandardCharsets.US_ASCII));
        return a+"."+b+"."+derToJose(s.sign());
    }
    private static byte[] derToJose(byte[] der) { int rLen=der[3]&255, rOff=4, sOff=4+rLen+2; int sLen=der[sOff-1]&255; byte[] out=new byte[64]; copyInt(der,rOff,rLen,out,0); copyInt(der,sOff,sLen,out,32); return out; }
    private static void copyInt(byte[] in,int off,int len,byte[] out,int dst){ while(len>32 && in[off]==0){off++;len--;} int copy=Math.min(len,32); System.arraycopy(in,off+len-copy,out,dst+32-copy,copy); }
    private static byte[] unsigned(byte[] x,int len){ byte[] out=new byte[len]; int copy=Math.min(x.length,len); System.arraycopy(x,x.length-copy,out,len-copy,copy); return out; }
    private static String b64(byte[] b){ return Base64.encodeToString(b,Base64.NO_WRAP|Base64.URL_SAFE|Base64.NO_PADDING); }
}
