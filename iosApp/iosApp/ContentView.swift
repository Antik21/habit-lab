import SwiftUI
import Shared

struct ContentView: View {
    let presenter: AppPresenter

    var body: some View {
        ComposeViewController(presenter: presenter)
            .ignoresSafeArea(.keyboard)
    }
}

private struct ComposeViewController: UIViewControllerRepresentable {
    let presenter: AppPresenter

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(presenter: presenter)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
