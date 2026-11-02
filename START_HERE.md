# Native SSO — updated development setup (Windows + Android emulator)

This is a source-level update to the supplied project. The original project has been kept in place in this archive; the changes are focused on the client-authentication mismatch and the redirect callback intent filter.

## What changed

- Added `native-sso-signer/`, a small Go service that signs short-lived `private_key_jwt` client assertions using the already-registered RSA key. It uses `RS256`, the registered client ID, and the registered key ID.
- The RSA private key is **not included** in this ZIP and must not be placed in the Android project or committed to Git.
- Added `SignerClient.java`; the Super App requests a client assertion from the local signer for PAR and token requests.
- `CryptoJwt.java` now only handles the AndroidKeyStore EC key used for DPoP (`ES256`). It no longer tries to use that EC key for the RSA client assertion.
- The callback intent filter now matches the registered redirect URI `com.nativesso.superapp://oauth` (the previous `/callback` path prevented matching that exact URI).
- The signer defaults to `127.0.0.1:8090`, accepts only local/private source addresses, and only signs a short allowlist of local eSignet audiences. This is a **development-only** signer, not a production authentication service.

## 1. Put the registered RSA private key beside the signer

Open PowerShell in the extracted updated project folder. Copy the key from your original local project (adjust the source path if yours differs):

```powershell
Copy-Item "<path-to-your-local-project>\client-private.pem" ".\native-sso-signer\client-private.pem"
```

If you extracted the original ZIP somewhere else, use that extracted folder's `build-native-sso\esignet-service\native-sso-client-secrets\client-private.pem` as the source. Do not paste the key into chat or commit it. The signer directory's `.gitignore` excludes PEM files.

The key must be the one matching the registered JWK for client `native-sso-superapp`.

## 2. Start the signer

Make sure the eSignet test container is already running and reachable on host port `8081`.

In PowerShell window 1, from the project root:

```powershell
Set-Location .\native-sso-signer
$env:NATIVE_SSO_CLIENT_KEY = (Resolve-Path .\client-private.pem).Path
$env:ESIGNET_BASE_URL = "http://127.0.0.1:8081"
go test ./...
go run .
```

Keep this window open. In PowerShell window 2, verify the local service:

```powershell
Invoke-RestMethod http://127.0.0.1:8090/health
```

You should see status `ok`. To test that it signs a PAR assertion without printing the JWT, run:

```powershell
$r = Invoke-RestMethod -Method Post `
  -Uri http://127.0.0.1:8090/assertion `
  -ContentType "application/json" `
  -Body '{"audience":"http://localhost:8081/oauth2/par"}'
"Assertion received: $([bool]$r.client_assertion); type: $($r.client_assertion_type)"
```

Expected: `Assertion received: True; type: urn:ietf:params:oauth:client-assertion-type:jwt-bearer`.

## 3. Build the Android apps

Open `mobile` in Android Studio, allow Gradle sync, and make sure Android SDK Platform 35 is installed. Or, from PowerShell:

```powershell
Set-Location .\mobile
.\gradlew.bat :super-app:assembleDebug :child-app:assembleDebug
```

The project intentionally does not include `local.properties`; Android Studio should generate it for your SDK. If building from a terminal, make sure `mobile\local.properties` points to your installed Android SDK.

Install on the running emulator:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r .\super-app\app\build\outputs\apk\debug\app-debug.apk
& $adb install -r .\child-app\app\build\outputs\apk\debug\app-debug.apk
```

If your Gradle output uses `child-app-debug.apk` / `super-app-debug.apk` instead, use the exact paths printed by Gradle or inspect `app\build\outputs\apk\debug` in each module.

## 4. End-to-end test

1. Keep eSignet and the signer running.
2. Launch Super App in the emulator and tap **Sign in with eSignet**.
3. Complete the browser sign-in and return through `com.nativesso.superapp://oauth`.
4. Confirm the Super App reports that it signed in and stored the device secret.
5. Open Child App and test the native SSO handoff.

## Troubleshooting

- **Signer cannot load key:** check that `native-sso-signer\client-private.pem` exists and is the RSA private key matching the registered public JWK.
- **`audience_not_allowed`:** do not disable the allowlist. Check `ESIGNET_BASE_URL` and the audience expected by this eSignet deployment. The default expected audiences are local PAR/token endpoint URLs plus the local issuer URL variants.
- **`invalid_client`:** inspect eSignet logs. Confirm the registered client ID is `native-sso-superapp`, `auth_methods` includes `private_key_jwt`, and the database public JWK `kid` matches `0L9pwn4iNkfC4r5QP-aFOQ`. Some deployments validate assertion audience against the issuer instead of the endpoint; this project uses `http://localhost:8081/oauth2/par` and `/oauth2/token` by default to match the advertised local issuer host. Adjust the two `ASSERTION_*_AUDIENCE` constants only after confirming server configuration.
- **Signer connection refused on Android:** verify `/health` works on Windows, port 8090 is not blocked by firewall, and the emulator uses `10.0.2.2:8090` to reach host loopback.
- **Callback does not return to the app:** verify the registered redirect URI exactly matches `com.nativesso.superapp://oauth` and reinstall the rebuilt APK.
- **Gradle cannot find SDK:** install Android SDK Platform 35 in Android Studio SDK Manager and set `sdk.dir` in `mobile\local.properties` locally.

## Verification status / limitations

- `go test ./...` and `go vet ./...` passed for `native-sso-signer` in the preparation environment.
- Android Gradle build and live eSignet OAuth / DPoP / Child App handoff were not verified here: the build environment could not reach `services.gradle.org` to download Gradle 8.9. Run the commands above on the Windows machine where Android SDK and Gradle dependencies are available.
- The signer returns a short-lived signed assertion to the local demo app. Although the RSA key stays on the host, this endpoint is still a constrained signing capability. Keep it bound to host loopback, use only on a development machine, and never expose it publicly. A production design should perform client authentication on a protected backend and use HTTPS, access control, secret management, and deployment-specific audience validation.
