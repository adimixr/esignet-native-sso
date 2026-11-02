# iOS Native SSO source scaffold

This directory adds initial Swift source for the iOS side. It is **not yet an end-to-end implementation** and has not been built with Xcode. The `ContentView` deliberately does not claim successful login; it identifies the remaining deployment-specific integration.

To complete and validate on macOS:

1. Create/open a SwiftUI iOS app project in Xcode and add `NativeSSO/*.swift` to the target.
2. Register the `nativesso-ios` URL scheme in the target's URL Types.
3. Implement PAR using the deployment's required `private_key_jwt` client assertion and DPoP proof, then launch the authorization URL with `ASWebAuthenticationSession`.
4. Handle the callback, verify state, exchange the code with PKCE and DPoP, and store credentials in Keychain.
5. Implement a signed app-to-app credential bridge and reuse the same device-bound DPoP key for token exchange. Do not export a private key to another app; broker signing operations or use a platform-supported secure mechanism.
6. Build, sign, and run on a physical iOS device; verify the same-vendor signing and consent behavior.

A Mac with Xcode is required for these steps. The current Windows environment cannot verify an iOS build.
