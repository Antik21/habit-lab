import Foundation
import SwiftUI
import Shared

@main
struct HabitLabApp: App {
    private let presenter: AppPresenter
    private let navigationEvents: AppNavigationEventBridge

    init() {
        presenter = HabitLabKoinKt.doInitHabitLabKoin(platformDescriptor: IosPlatformDescriptor())
        navigationEvents = AppNavigationEventBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView(presenter: presenter, navigationEvents: navigationEvents)
                .onOpenURL { url in
                    navigationEvents.accept(rawUrl: url.absoluteString)
                }
        }
    }
}

private final class IosPlatformDescriptor: NSObject, PlatformDescriptor {
    let name = "iOS"
}
