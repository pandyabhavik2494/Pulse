import SwiftUI
import HealthKit

@main
struct PulseApp: App {
    @State private var store: PulseStore

    init() {
        let args = ProcessInfo.processInfo.arguments
        _store = State(initialValue: PulseStore(demo: args.contains("-demo") || UserDefaults.standard.bool(forKey: "PulseDemoData")))
        WatchLink.shared.activate()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .preferredColorScheme(.dark)
                .tint(C.green)
        }
    }
}

struct RootView: View {
    @Environment(PulseStore.self) private var store
    @Environment(\.scenePhase) private var phase
    @AppStorage("onboardingDone") private var onboardingDone = false
    @State private var tab = Tab.today

    enum Tab: String { case today, journey, history, settings }

    var body: some View {
        Group {
            if let s = store.state, onboardingDone || store.isDemo {
                TabView(selection: $tab) {
                    SwiftUI.Tab("Today", systemImage: "sun.max.fill", value: Tab.today) { TodayView(s: s) }
                    SwiftUI.Tab("Journey", systemImage: "flag.fill", value: Tab.journey) { JourneyView(s: s) }
                    SwiftUI.Tab("History", systemImage: "calendar", value: Tab.history) { HistoryView(s: s) }
                    SwiftUI.Tab("Settings", systemImage: "gearshape.fill", value: Tab.settings) { SettingsView(s: s) }
                }
            } else {
                OnboardingView()
            }
        }
        .background(C.bg.ignoresSafeArea())
        .task { await launch() }
        .onChange(of: phase) { _, p in
            if p == .active { Task { await store.sync() } }
        }
        .task {
            // Time-of-day moods ("on pace for now") move as the day goes.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                store.rebuild()
            }
        }
        .onOpenURL { url in
            if let t = Tab(rawValue: url.host() ?? "") { tab = t }
        }
    }

    private func launch() async {
        let args = ProcessInfo.processInfo.arguments
        if let t = args.firstIndex(of: "-tab").flatMap({ args.indices.contains($0 + 1) ? Tab(rawValue: args[$0 + 1]) : nil }) { tab = t }
        #if DEBUG
        if args.contains("-seedHealth") {
            // Simulator screenshots: Bhavik's sample profile plus five weeks of sample data written into Health.
            try? await HealthSeeder.seed(HealthReader.shared.store)
            store.saveProfile(DemoData.profile)
            onboardingDone = true
        }
        #endif
        store.startObserving()
        await store.sync()
    }
}
