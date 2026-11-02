# Native SSO integration patch notes

## Applied
- Established `mobile/` as the one canonical Android Studio project with `:super-app` and `:child-app` modules.
- Added a Gradle 8.9 wrapper to match Android Gradle Plugin 8.7.3 and avoid opening the Gradle 9.3/9.6 starter projects by accident.
- Removed machine-specific `local.properties` from the distributed bundle.
- Enabled cleartext HTTP for the current local-emulator demo manifests (`10.0.2.2:8088`). This is intentionally demo-only and must be replaced with HTTPS for release.
- Added connect/read timeouts to Android HTTP helpers.
- Changed token exchange scope handling in the canonical ThunderID source to reject requests containing any scope not granted to the source token/device secret; valid downscoping remains allowed.
- Added unit tests for broader-scope rejection and valid downscoping.
- Added an iOS Swift source scaffold and explicit notes about what still requires macOS/Xcode integration.

## Not verified in this environment
- Android Gradle build: not run because this environment cannot download Gradle/dependencies or access the user's Android SDK.
- Go tests: not run because the module declares Go 1.26 and this environment could not download that toolchain.
- Docker end-to-end flow: not run against the user's local services.
- iOS: no Xcode/macOS build environment was available; the iOS source is explicitly a scaffold, not a finished/verified iOS client.

## Before demo/submission
1. Open `mobile/` in Android Studio, use JDK 17, Android SDK 35, and run `gradlew.bat assembleDebug`.
2. Ensure both apps are signed with the same signing certificate.
3. Register each app's client key/JWK and confirm the deployed issuer, endpoint paths, client IDs, redirect URI, and `device_sso` allow-list.
4. Run the full browser login → token issuance → bridge → token exchange flow against local Docker.
5. Run Go tests with Go 1.26 in the actual ThunderID module.
6. Build and validate iOS separately on macOS/Xcode before representing it as complete.


## Client-authentication and callback fixes (2026-10-24)

- Added `native-sso-signer` to sign short-lived RS256 `private_key_jwt` assertions with the registered RSA key ID.
- Removed client assertion signing from Android's EC DPoP helper; DPoP remains ES256 using AndroidKeyStore.
- Super App obtains assertions for PAR and token exchange from the local signer.
- Updated callback intent filter to match registered redirect URI `com.nativesso.superapp://oauth`.
- Added `START_HERE.md` with Windows setup and verification steps.
- The registered RSA private key and machine-specific `local.properties` files are intentionally excluded from the output archive.
- Go signer tests and vet passed; Android build and live end-to-end flow require verification on the user's Windows/Android SDK setup.
