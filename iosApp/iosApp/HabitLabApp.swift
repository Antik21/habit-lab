import Foundation
import SwiftUI
import Shared

@main
struct HabitLabApp: App {
    private let runtime: HabitLabRuntime
    private let navigationEvents: AppNavigationEventBridge

    init() {
        runtime = MainViewControllerKt.createIosHabitLabRuntime(
            platformDescriptor: IosPlatformDescriptor(),
            isDebugBuild: isDebugBuild
        )
        #if DEBUG
        HabitLabDebugRuntimeHolder.shared.install(runtime: runtime)
        #endif
        navigationEvents = AppNavigationEventBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView(presenter: runtime.presenter, navigationEvents: navigationEvents)
                .onOpenURL { url in
                    navigationEvents.accept(rawUrl: url.absoluteString)
                }
        }
    }
}

private let isDebugBuild: Bool = {
    #if DEBUG
    true
    #else
    false
    #endif
}()

private final class IosPlatformDescriptor: NSObject, PlatformDescriptor {
    let name = "iOS"
}

#if DEBUG
/**
 * Debug/LLDB bridge for the one runtime constructed by [HabitLabApp]. It never creates Koin or a
 * database itself, and this entire holder is omitted from release builds.
 */
@objcMembers
final class HabitLabDebugRuntimeHolder: NSObject {
    static let shared = HabitLabDebugRuntimeHolder()

    private(set) var runtime: HabitLabRuntime?

    func install(runtime: HabitLabRuntime) {
        precondition(self.runtime == nil, "The debug runtime may only be installed once")
        self.runtime = runtime
    }

    /** LLDB-callable reset: `HabitLabDebugRuntimeHolder.shared.resetAndSeed { print($0) }`. */
    func resetAndSeed(completion: @escaping (Bool) -> Void) {
        guard let control = runtime?.debugDatabaseControl else {
            completion(false)
            return
        }
        control.resetAndSeed { result, error in
            completion(error == nil && result is DebugDatabaseResetResultReset)
        }
    }
}
#endif
