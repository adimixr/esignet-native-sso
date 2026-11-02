import SwiftUI
import AuthenticationServices
import CryptoKit
import Security

/// iOS implementation starter. The source is intentionally explicit about the remaining
/// integration step: PAR/client_assertion/DPoP formats must match the deployed ThunderID config.
struct ContentView: View {
    @State private var status = "Not signed in"
    @State private var webSession: ASWebAuthenticationSession?

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "person.badge.key.fill").font(.system(size: 44))
            Text("Native SSO Super App").font(.title2).bold()
            Text(status).multilineTextAlignment(.center)
            Button("Prepare eSignet sign-in") {
                status = "Configuration loaded. Complete PAR + client assertion + DPoP integration for your eSignet deployment before using this app."
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(28)
    }
}

/// Reusable PKCE helper for the full ASWebAuthenticationSession flow.
enum PKCE {
    static func verifier() -> String {
        let bytes = (0..<32).map { _ in UInt8.random(in: 0...255) }
        return Data(bytes).base64URLEncodedString()
    }

    static func challenge(for verifier: String) -> String {
        Data(SHA256.hash(data: Data(verifier.utf8))).base64URLEncodedString()
    }
}

extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
