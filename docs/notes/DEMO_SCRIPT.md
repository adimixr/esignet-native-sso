# 3-minute demo script

## 0:00–0:20 — Problem

Show two vendor-signed Android apps. Explain that a user who authenticated in App A should not have to repeat authentication in App B.

## 0:20–1:10 — First login

Open Super App. Tap **Sign in with eSignet**. Complete the normal browser/OTP/consent flow. Show the Super App home screen.

Briefly show the token response in a developer log or Postman response containing `access_token`, `id_token`, and `device_secret`.

## 1:10–1:40 — Secure handoff

Open Child App. Tap **Sign In with Native SSO**. Explain that the Child App invokes the exported Super App bridge protected by a signature-level permission.

The Super App returns the ID Token, device secret, and a newly generated DPoP proof using its original device key. The private key never leaves Android Keystore.

## 1:40–2:30 — Token exchange

Show the token request:

```text
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
subject_token_type=urn:ietf:params:oauth:token-type:id_token
actor_token_type=urn:openid:params:token-type:device-secret
```

Point out that `device_sso` is intentionally absent from the exchange request.

Show the successful Child App access token response.

## 2:30–3:00 — Security checks

Show the implementation checks:

- `ds_hash` binds the ID Token and device secret.
- `sid` binds both artifacts to the same authentication session.
- `cnf.jkt` must equal the current DPoP key thumbprint.
- Requested scopes must be a subset of the original grant.
- `device_sso` is rejected during token exchange.

End by showing the architecture diagram and repository structure.
