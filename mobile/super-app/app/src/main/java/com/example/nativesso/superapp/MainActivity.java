package com.example.nativesso.superapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.Activity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private SecureStore store;
    private TextView status;
    private String state, verifier;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); store = new SecureStore(this); try { CryptoJwt.ensureKey(); } catch(Exception e) { android.util.Log.e("NativeSSO", "DPoP key setup failed", e); }
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(32,64,32,32);
        status = new TextView(this); status.setText("Native SSO Super App\n\nNot signed in.");
        Button login = new Button(this); login.setText("Sign in with eSignet"); login.setOnClickListener(v -> startLogin());
        box.addView(status); box.addView(login); setContentView(box);
        handleCallback(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){ super.onNewIntent(intent); handleCallback(intent); }
    private void startLogin() {
        try {
            state = random(); verifier = random();
            String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    String assertion = SignerClient.clientAssertion(SsoConfig.ASSERTION_PAR_AUDIENCE);
                    String dpop = CryptoJwt.dpopProof(SsoConfig.DPOP_PAR_HTU, "POST");
                    String form = "response_type=code&client_id="+enc(SsoConfig.CLIENT_ID)+"&redirect_uri="+enc(SsoConfig.REDIRECT_URI)+"&scope="+enc(SsoConfig.SCOPE)+"&state="+enc(state)+"&code_challenge="+enc(challenge)+"&code_challenge_method=S256"
                            +"&client_assertion_type="+enc("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")+"&client_assertion="+enc(assertion);
                    org.json.JSONObject par = new org.json.JSONObject(Http.post(SsoConfig.PAR_ENDPOINT, form, dpop));
                    String requestUri = par.getString("request_uri");
                    String url = SsoConfig.AUTHORIZATION_ENDPOINT + "?client_id="+enc(SsoConfig.CLIENT_ID)+"&request_uri="+enc(requestUri);
                    runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
                } catch(Exception e) { runOnUiThread(() -> status.setText("PAR failed: "+e.getMessage())); }
            });
        } catch(Exception e){ status.setText("Login setup failed: "+e.getMessage()); }
    }
    private void handleCallback(Intent intent){
        Uri u=intent.getData(); if(u==null || !"com.nativesso.superapp".equals(u.getScheme())) return;
        if(!"oauth".equals(u.getHost())) return; String code=u.getQueryParameter("code"); String returnedState=u.getQueryParameter("state");
        if(code==null || state==null || !state.equals(returnedState)){ status.setText("Invalid OAuth state"); return; }
        Executors.newSingleThreadExecutor().execute(() -> { try { String dpop=CryptoJwt.dpopProof(SsoConfig.DPOP_TOKEN_HTU,"POST"); String assertion=SignerClient.clientAssertion(SsoConfig.ASSERTION_TOKEN_AUDIENCE);
            String body="grant_type=authorization_code&code="+enc(code)+"&redirect_uri="+enc(SsoConfig.REDIRECT_URI)+"&code_verifier="+enc(verifier)+"&client_id="+enc(SsoConfig.CLIENT_ID)+"&client_assertion_type="+enc("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")+"&client_assertion="+enc(assertion);
            String json=Http.post(SsoConfig.TOKEN_ENDPOINT,body,dpop); org.json.JSONObject o=new org.json.JSONObject(json);
            store.put("id_token",o.getString("id_token")); store.put("device_secret",o.getString("device_secret")); store.put("access_token",o.getString("access_token"));
            runOnUiThread(() -> status.setText("Signed in with eSignet.\nNative SSO device secret stored securely.\nOpen the Child App to continue."));
        } catch(Exception e){ runOnUiThread(() -> status.setText("Token exchange failed: "+e.getMessage())); }}); }
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,StandardCharsets.UTF_8.name());}
    private static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return b64(b);}
    private static String b64(byte[] b){return android.util.Base64.encodeToString(b,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE|android.util.Base64.NO_PADDING);}
}
