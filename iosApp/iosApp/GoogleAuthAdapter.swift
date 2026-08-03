import Foundation
import UIKit
import FirebaseCore
import GoogleSignIn
import ComposeApp

/// Swift implementation of the shared GoogleAuthController interface,
/// backed by the GoogleSignIn iOS SDK.
class GoogleAuthAdapter: GoogleAuthController {

    private let youtubeScope = "https://www.googleapis.com/auth/youtube.readonly"

    init() {
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        }
    }

    func signIn(callback: @escaping (GoogleTokens?, String?) -> Void) {
        DispatchQueue.main.async {
            guard GIDSignIn.sharedInstance.configuration != nil else {
                callback(nil, "Google sign-in is not configured: enable the Google provider in Firebase and re-download GoogleService-Info.plist")
                return
            }
            guard let root = Self.rootViewController() else {
                callback(nil, "No root view controller")
                return
            }
            GIDSignIn.sharedInstance.signIn(
                withPresenting: root,
                hint: nil,
                additionalScopes: [self.youtubeScope]
            ) { result, error in
                if let error = error {
                    callback(nil, error.localizedDescription)
                    return
                }
                guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                    callback(nil, "Google sign-in returned no token")
                    return
                }
                callback(
                    GoogleTokens(
                        idToken: idToken,
                        accessToken: user.accessToken.tokenString,
                        displayName: user.profile?.name
                    ),
                    nil
                )
            }
        }
    }

    func freshAccessToken(callback: @escaping (String?) -> Void) {
        DispatchQueue.main.async {
            let finish: (GIDGoogleUser?) -> Void = { user in
                guard let user = user,
                      user.grantedScopes?.contains(self.youtubeScope) == true else {
                    callback(nil)
                    return
                }
                user.refreshTokensIfNeeded { refreshed, _ in
                    callback(refreshed?.accessToken.tokenString)
                }
            }
            if let current = GIDSignIn.sharedInstance.currentUser {
                finish(current)
            } else {
                GIDSignIn.sharedInstance.restorePreviousSignIn { restored, _ in
                    finish(restored)
                }
            }
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }

    private static func rootViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap { $0.windows }.first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
