# Implementation Status

## Completed in this workspace

- `device_sso` token-type/scope primitives.
- ThunderID `DeviceSecretProvider` interface.
- Default stateless signed-then-encrypted device-secret provider.
- Engine provider wiring and standalone server wiring.
- Authorization-code device-secret issuance.
- ID Token `sid`, `ds_hash`, and `scope` support.
- Token response `device_secret` field.
- Native token-exchange actor-token branch.
- DPoP `cnf.jkt` binding.
- `ds_hash` and `sid` binding.
- Original-scope subset enforcement.
- `device_sso` rejection on token exchange.
- Android Java Super App and Child App demonstration.
- Signature-protected app-to-app credential bridge.
- Android Keystore-backed token storage and DPoP/client assertion keys.
- API and architecture documentation.

## Deployment assumptions

- eSignet client registration must include `device_sso` in `allowed_authorization_scopes`.
- The two Android clients must be registered with their respective public signing JWKs and `private_key_jwt` token endpoint authentication.
- Both Android APKs must be signed with the same certificate.
- Configure the mobile `ISSUER`/endpoints and client IDs before building.

## Verification note

The source repositories require Go 1.26. The current execution environment did not have that toolchain available, so the final Go test suite must be run on the development machine with Go 1.26. The implementation was formatted and statically inspected here; no claim is made that a full Go test run completed in this environment.
