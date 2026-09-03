import Foundation
import SwiftUI
import Shared

@main
struct HabitLabApp: App {
    private let presenter: AppPresenter

    init() {
        presenter = HabitLabKoinKt.doInitHabitLabKoin(platformDescriptor: IosPlatformDescriptor())
    }

    var body: some Scene {
        WindowGroup {
            ContentView(presenter: presenter)
        }
    }
}

private final class IosPlatformDescriptor: NSObject, PlatformDescriptor {
    let name = "iOS"
}
