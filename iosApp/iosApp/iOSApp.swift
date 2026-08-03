import SwiftUI
import FirebaseCore
import GoogleSignIn
import ComposeApp

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        MainViewControllerKt.initialize(googleAuth: GoogleAuthAdapter())
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ComposeContainerViewController(MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Compose's view controller defers system gestures on every edge, which makes
/// the home-indicator swipe need two attempts. This container re-enables the
/// normal single-swipe home gesture.
class ComposeContainerViewController: UIViewController {
    private let compose: UIViewController

    init(_ compose: UIViewController) {
        self.compose = compose
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
    }

    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge { [] }
    override var childForScreenEdgesDeferringSystemGestures: UIViewController? { nil }
    override var childForHomeIndicatorAutoHidden: UIViewController? { nil }
}
