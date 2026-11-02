# eSignet Native SSO — Implementation Design

## Scope

This implementation adds OpenID Connect Native SSO for two native applications that belong to the same trusted vendor family. It follows the challenge's required artifacts: `device_sso`, a signed-then-encrypted `device_secret`, `ds_hash`/`sid` binding, DPoP key binding, and OAuth 2.0 Token Exchange.

The OpenID Connect Native SSO profile defines `ds_hash` as an authorization-server-controlled binding between the ID Token and `device_secret`, and requires a session identifier (`sid`) in the ID Token. urlOpenID Connect Native SSO specificationhttps://openid.net/specs/openid-connect-native-sso-1_0.html

## Flow

```text
Super App                 eSignet / ThunderID                 Child App
   |                              |                              |
   | authorization request        |                              |
   | device_sso + openid          |                              |
   |----------------------------->|                              |
   |<------ browser / OTP ---------|                              |
   |                              |                              |
   | authorization_code            |                              |
   |----------------------------->|                              |
   |                              | issue access + ID + DS       |
   |<-----------------------------|                              |
   | store tokens in Keystore     |                              |
   |                              |                              |
   |                              |      signed intent request   |
   |                              |<-----------------------------|
   |<------ ID token + DS + DPoP ---------------------------------|
   |                              |                              |
   |                              | token_exchange               |
   |                              | actor=device_secret          |
   |                              | subject=id_token             |
   |                              | DPoP uses original key      |
   |                              |<-----------------------------|
   |                              |                              |
   |                              |---- access token ----------->|
```

## Device secret

The default ThunderID provider creates:

1. Inner signed JWT containing `cnf.jkt`, `auth_id`, `exp`, `client_id`, `scope`, `acr`, `claims`, `iss`, `sid` and standard JWT claims.
2. A `ds_hash` derived from the signed inner JWT using SHA-256/base64url. The profile deliberately leaves the exact binding function to the authorization server.
3. A JWE wrapper using the server's configured encryption key.

The plaintext secret is never logged.

## Token exchange validation

For `actor_token_type=urn:openid:params:token-type:device-secret` the handler:

- requires an ID Token subject token;
- validates the ID Token signature and expiry;
- validates the device secret with `DeviceSecretProvider`;
- checks `device_secret.client_id == ID-token aud`;
- checks `ds_hash` equality;
- checks `sid` equality and presence;
- checks `device_secret.cnf.jkt == current DPoP jkt`;
- requires `device_sso` in the original authorization scope;
- rejects `device_sso` in the token-exchange request;
- downscopes requested scopes to the original device-secret scopes;
- removes `device_sso` from the resulting access-token scopes.

## Trust boundary

The native apps must be signed with the same vendor certificate. The demo Android bridge additionally uses a signature-level Android permission so an unrelated application cannot invoke the credential bridge.

The backend's client registration remains the authoritative trust boundary. A production deployment should additionally maintain an explicit vendor/client-family allow-list if clients from one deployment can belong to multiple vendors.
