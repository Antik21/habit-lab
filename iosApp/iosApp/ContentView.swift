import SwiftUI
import Shared

struct ContentView: View {
    let presenter: AppPresenter
    let navigationEvents: AppNavigationEventBridge

    var body: some View {
        ComposeViewController(presenter: presenter, navigationEvents: navigationEvents)
            .ignoresSafeArea(.all)
    }
}

private struct ComposeViewController: UIViewControllerRepresentable {
    let presenter: AppPresenter
    let navigationEvents: AppNavigationEventBridge

    func makeUIViewController(context: Context) -> UIViewController {
        let composeController = MainViewControllerKt.MainViewController(
            presenter: presenter,
            navigationEvents: navigationEvents
        )
        return NavigationBackGestureController(
            contentController: composeController,
            navigationEvents: navigationEvents
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Temporary iOS adapter for the Navigation 3 KMP spike. Compose 1.12 installs its own
/// UIScreenEdgePanGestureRecognizer, but that recognizer is not dispatched when the Compose
/// controller is embedded by SwiftUI. This controller keeps Nav3 as the sole back-stack owner and
/// forwards one completed native edge gesture into the common navigator.
private final class NavigationBackGestureController: UIViewController, UIGestureRecognizerDelegate {
    private let contentController: UIViewController
    private let navigationEvents: AppNavigationEventBridge
    private lazy var backGesture = LeadingEdgePanGestureRecognizer(
        target: self,
        action: #selector(handleBackGesture(_:))
    )

    init(contentController: UIViewController, navigationEvents: AppNavigationEventBridge) {
        self.contentController = contentController
        self.navigationEvents = navigationEvents
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        addChild(contentController)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(contentController.view)
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentController.view.topAnchor.constraint(equalTo: view.topAnchor),
            contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentController.didMove(toParent: self)

        backGesture.edge = semanticLeadingEdge
        backGesture.delegate = self
        view.addGestureRecognizer(backGesture)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        backGesture.edge = semanticLeadingEdge
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        DispatchQueue.main.async { [weak self] in
            self?.giveNativeBackGesturePriority()
        }
    }

    private func giveNativeBackGesturePriority() {
        guard let window = view.window else { return }
        let leadingEdge = semanticLeadingEdge
        for recognizer in screenEdgeRecognizers(in: window)
        where recognizer.edges == leadingEdge && isComposeBackRecognizer(recognizer) {
            recognizer.require(toFail: backGesture)
        }
    }

    private var semanticLeadingEdge: UIRectEdge {
        view.effectiveUserInterfaceLayoutDirection == .rightToLeft ? .right : .left
    }

    private func isComposeBackRecognizer(_ recognizer: UIScreenEdgePanGestureRecognizer) -> Bool {
        String(reflecting: type(of: recognizer)).contains("BackGestureRecognizer")
    }

    private func screenEdgeRecognizers(in view: UIView) -> [UIScreenEdgePanGestureRecognizer] {
        let own = view.gestureRecognizers?.compactMap { $0 as? UIScreenEdgePanGestureRecognizer } ?? []
        return own + view.subviews.flatMap(screenEdgeRecognizers(in:))
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard let recognizer = gestureRecognizer as? UIPanGestureRecognizer else { return true }
        let velocity = recognizer.velocity(in: view)
        let directedVelocity = velocity.x * semanticDirection
        return directedVelocity > 0 && directedVelocity > abs(velocity.y)
    }

    @objc
    private func handleBackGesture(_ recognizer: UIPanGestureRecognizer) {
        guard recognizer.state == .ended else { return }

        let translation = recognizer.translation(in: view)
        let velocity = recognizer.velocity(in: view)
        let distanceThreshold = max(48, view.bounds.width * 0.2)
        let direction = semanticDirection
        if translation.x * direction >= distanceThreshold || velocity.x * direction >= 500 {
            navigationEvents.requestBack()
        }
    }

    private var semanticDirection: CGFloat {
        semanticLeadingEdge == .right ? -1 : 1
    }
}

/// `UIScreenEdgePanGestureRecognizer` is not injected by XCUITest for a SwiftUI-embedded Compose
/// controller. This equivalent recognizer fails immediately unless the first touch is within the
/// native 24-point leading-edge band, so ordinary horizontal and scrolling gestures are untouched.
private final class LeadingEdgePanGestureRecognizer: UIPanGestureRecognizer {
    var edge: UIRectEdge = .left

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        super.touchesBegan(touches, with: event)
        guard let touch = touches.first, let view else {
            state = .failed
            return
        }
        let x = touch.location(in: view).x
        let isInsideLeadingEdge = edge == .right
            ? view.bounds.width - x <= 24
            : x <= 24
        if !isInsideLeadingEdge {
            state = .failed
        }
    }
}
