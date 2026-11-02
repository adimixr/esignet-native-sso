import Foundation

enum SSOConfig {
    // Replace with the HTTPS issuer used by your eSignet deployment.
    static let issuer = URL(string: "https://YOUR-ESIGNET-HOST")!
    static let authorizationEndpoint = issuer.appending(path: "oauth2/authorize")
    static let parEndpoint = issuer.appending(path: "oauth2/par")
    static let tokenEndpoint = issuer.appending(path: "oauth2/token")
    static let clientID = "ios-super-app"
    static let redirectURI = "nativesso-ios://oauth/callback"
    static let scopes = "openid profile email device_sso"
}
