package com.example.nativesso.superapp;

public final class SsoConfig {
    // Emulator reaches services running on the Windows host through 10.0.2.2.
    public static final String ISSUER = "http://10.0.2.2:8081";
    public static final String AUTHORIZATION_ENDPOINT = ISSUER + "/oauth2/authorize";
    public static final String PAR_ENDPOINT = ISSUER + "/oauth2/par";
    public static final String TOKEN_ENDPOINT = ISSUER + "/oauth2/token";
    public static final String DPOP_PAR_HTU = "http://localhost:8081/oauth2/par";
    public static final String DPOP_TOKEN_HTU = "http://localhost:8081/oauth2/token";
    // Use the issuer host advertised by eSignet for client_assertion aud claims.
    public static final String ASSERTION_PAR_AUDIENCE = "http://localhost:8081";
    public static final String ASSERTION_TOKEN_AUDIENCE = "http://localhost:8081";
    public static final String SIGNER_ENDPOINT = "http://10.0.2.2:8090/assertion";
    public static final String CLIENT_ID = "native-sso-superapp";
    public static final String REDIRECT_URI = "com.nativesso.superapp://oauth";
    public static final String SCOPE = "openid profile email device_sso";
    private SsoConfig() {}
}
