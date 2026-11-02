# Native SSO API Contract

## Authorization

The first app requests:

```text
scope=openid profile email device_sso
```

The client must have `device_sso` in `additional_config.allowed_authorization_scopes`.

## Authorization-code token response

When `device_sso` was granted, `/oauth2/token` returns the normal OAuth response plus:

```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "id_token": "...",
  "device_secret": "..."
}
```

The ID Token contains:

```json
{
  "sid": "authentication-session-id",
  "ds_hash": "base64url-binding",
  "scope": "openid profile email"
}
```

`device_sso` is intentionally not emitted as an access-token permission scope.

## Token exchange

Request:

```text
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
subject_token=<id_token>
subject_token_type=urn:ietf:params:oauth:token-type:id_token
actor_token=<device_secret>
actor_token_type=urn:openid:params:token-type:device-secret
scope=openid profile
```

The request must contain a DPoP proof whose JWK thumbprint equals the `cnf.jkt` inside the device secret.

The request **must not** contain `device_sso` in `scope`.

## Errors

| Condition | Error |
|---|---|
| `device_sso` in token exchange scope | `invalid_scope` |
| actor type device secret without ID-token subject | `invalid_request` |
| invalid device secret | `invalid_request` |
| `ds_hash` mismatch | `invalid_request` |
| `sid` mismatch/missing | `invalid_request` |
| DPoP key mismatch | `invalid_dpop_proof` |
| requested scope outside original grant | `invalid_scope` |
