package com.example.nativesso.superapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class SecureStore {
    private static final String PREF = "native_sso_secure";
    private static final String KEY_ALIAS = "native_sso_storage_key";
    private final SharedPreferences prefs;

    public SecureStore(Context context) { prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public void put(String name, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            prefs.edit().putString(name, b64(iv) + "." + b64(encrypted)).apply();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public String get(String name) {
        String raw = prefs.getString(name, null); if (raw == null) return null;
        try {
            String[] p = raw.split("\\.", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new javax.crypto.spec.GCMParameterSpec(128, fromB64(p[0])));
            return new String(cipher.doFinal(fromB64(p[1])), StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    private SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private static String b64(byte[] b) { return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE); }
    private static byte[] fromB64(String s) { return android.util.Base64.decode(s, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE); }
}
