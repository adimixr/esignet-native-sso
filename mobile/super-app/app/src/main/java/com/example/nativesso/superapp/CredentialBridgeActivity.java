package com.example.nativesso.superapp;

import android.app.Activity; import android.content.Intent; import android.os.Bundle;
public class CredentialBridgeActivity extends Activity {
    @Override protected void onCreate(Bundle b){super.onCreate(b); try{
        SecureStore s=new SecureStore(this); String id=s.get("id_token"), ds=s.get("device_secret");
        if(id==null||ds==null){setResult(RESULT_CANCELED);finish();return;}
        String proof=CryptoJwt.dpopProof(SsoConfig.TOKEN_ENDPOINT,"POST");
        Intent out=new Intent(); out.putExtra("id_token",id); out.putExtra("device_secret",ds); out.putExtra("dpop_proof",proof); setResult(RESULT_OK,out); finish();
    }catch(Exception e){setResult(RESULT_CANCELED);finish();}}
}
