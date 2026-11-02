package com.example.nativesso.childapp;
public final class SsoConfig {
    public static final String ISSUER="http://10.0.2.2:8088";
    public static final String TOKEN_ENDPOINT=ISSUER+"/oauth2/token";
    public static final String CLIENT_ID="child-app";
    public static final String BRIDGE="com.example.nativesso.SIGNATURE";
    private SsoConfig(){}
}
