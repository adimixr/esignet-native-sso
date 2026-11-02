package com.example.nativesso.superapp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class Http {
    public static String post(String url, String body, String dpop) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setRequestProperty("Accept", "application/json");
        if (dpop != null) c.setRequestProperty("DPoP", dpop);

        try (OutputStream o = c.getOutputStream()) {
            o.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String response = in == null ? "" : read(in);
        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + response);
        }
        return response;
    }

    private static String read(InputStream i) throws Exception {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(i, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) b.append(l);
            return b.toString();
        }
    }
}
