# Native SSO submission workspace

## Canonical Android Studio project
Open the **`mobile`** directory (not `mobile/android/SuperApp` or `mobile/android/ChildApp2`). The root includes the `:super-app` and `:child-app` modules and a Gradle 8.9 wrapper compatible with Android Gradle Plugin 8.7.3.

### Build
On Windows PowerShell, from `mobile`:

```powershell
.\gradlew.bat projects
.\gradlew.bat assembleDebug
```

The first run downloads Gradle and Android dependencies. Android SDK 35 and JDK 17 are required. If your Android SDK is elsewhere, configure it locally using Android Studio; do not commit `local.properties`.

## Local emulator configuration
The Android apps point to eSignet at `http://10.0.2.2:8081`; the local assertion signer listens on host loopback port `8090` and is reached from the emulator as `http://10.0.2.2:8090`. See `START_HERE.md` for the required signer setup. The app manifests allow cleartext only for this local demo setup. **Do not ship this configuration to production**: use HTTPS and remove `android:usesCleartextTraffic`.

## Native SSO demo flow
1. Start the configured eSignet test container on host port 8081.
2. Start `native-sso-signer` using the registered RSA private key copied locally (the key is intentionally not distributed in this ZIP).
3. Ensure client `native-sso-superapp` is registered with `private_key_jwt`, the matching RSA public key, and redirect URI `com.nativesso.superapp://oauth`. The two Android APKs must be signed with the same certificate so the signature-protected bridge works.
4. Ensure `device_sso` is an allowed authorization scope for `native-sso-superapp`.
5. Open Super App, sign in through the browser, then open Child App and request Native SSO.

Check client registration and the issuer's actual endpoint paths before running; local settings may differ between environments. The generated public JWKs are logged under `NativeSSO` for development registration only. Never log private keys or real tokens.

## Backend scope policy
Token exchange now rejects requested scopes not present in the source token/device-secret granted scopes, instead of silently filtering them. `device_sso` remains forbidden in the token-exchange request scope. Unit tests were added for rejection and valid downscoping.

## iOS status
The `ios/` directory contains Swift source scaffolding and integration notes. This workspace was not built with Xcode: macOS/Xcode is required to create/sign/run the iOS app and to verify the platform-specific cryptography and callback behavior. Do not describe iOS as verified until it is built and tested on a Mac.

## Verification status
This package is a corrected source bundle, not a claim of end-to-end success. Verify Android builds on your machine and run the Go tests with Go 1.26 as required by the module. Then run the full browser-login → device-secret issuance → signed bridge → token-exchange flow against your Docker deployment.


## Updated local signer

The Android client assertion is no longer signed with the Android EC DPoP key. `native-sso-signer/` uses the registered RSA private key to issue short-lived RS256 assertions. Keep the key out of the APK and Git. The signer is for local development only; see `START_HERE.md`.
