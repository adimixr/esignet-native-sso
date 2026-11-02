package com.example.nativesso.superapp;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Calls the local development signer. The RSA private key must never be stored in this APK. */
public final class SignerClient {
    private SignerClient() {}

    public static String clientAssertion(String audience) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(SsoConfig.SIGNER_ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        JSONObject request = new JSONObject().put("audience", audience);
        try (OutputStream out = c.getOutputStream()) { out.write(request.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) b.append(line);
            body = b.toString();
        } finally { c.disconnect(); }
        if (code < 200 || code >= 300) throw new java.io.IOException("Local signer HTTP " + code + ": " + body);
        JSONObject response = new JSONObject(body);
        return response.getString("client_assertion");
    }
}
