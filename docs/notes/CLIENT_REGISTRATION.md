# Client registration checklist

## Super App

- client_id: `super-app`
- response type: `code`
- grant type: authorization_code
- token endpoint authentication: `private_key_jwt`
- redirect URI: `nativesso-super://oauth/callback`
- `allowed_authorization_scopes`: `device_sso`
- DPoP-bound access tokens: enabled
- public JWK: Super App Keystore public key, with `kid=super-app`

## Child App

- client_id: `child-app`
- grant type: token_exchange
- token endpoint authentication: `private_key_jwt`
- DPoP-bound access tokens: enabled
- public JWK: Child App Keystore public key, with `kid=child-app`

The vendor must sign both APKs with the same certificate. The app-to-app bridge additionally uses Android's signature-level permission.
