# Native SSO for eSignet Go — MOSIP Decode 2026

Native SSO implementation workspace for the challenge **“Enable Native SSO in eSignet Go for Mobile Apps.”**

## Repository contents

- `mobile/` — Android Java Super App and Child App demonstration.
- `backend/thunderid/backend/` — focused ThunderID patch files only; apply these to the matching upstream ThunderID checkout.
- `native-sso-signer/` — local development service for RS256 `private_key_jwt` assertions.
- `ios/` — Swift source scaffold; not verified with Xcode.
- `docs/` — API, architecture, demo, client-registration, and implementation notes.
- `tools/postman/` — API collection.

This repository intentionally does **not** redistribute the complete upstream eSignet or ThunderID repositories. The backend folder is a focused patch overlay, not a standalone Go module.

## Important security notes

- No private signing key, local environment file, Android `local.properties`, keystore, generated APK, or build output is included.
- The local signer and cleartext HTTP configuration are for development/demo use only. Use HTTPS and managed keys/HSM-backed key storage for production.
- Never commit private keys, client secrets, access tokens, or real user data.

## Verification status

This is a source-level implementation bundle, not a claim of end-to-end success. The Go backend requires Go 1.26 and must be tested in the matching upstream checkout. Android build and the live browser-login → device-secret issuance → app bridge → token-exchange flow still require verification in the target environment. The iOS directory is scaffolding and has not been built with macOS/Xcode.

See `docs/notes/IMPLEMENTATION_STATUS.md`, `README_NATIVE_SSO.md`, and `START_HERE.md` before running the demo.
