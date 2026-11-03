import Foundation
import SwiftData
import SwiftUI
import WidgetKit

/// One stored day: the DayLog as JSON, keyed by its date. Like the Android Room table.
@Model
final class StoredDay {
    @Attribute(.unique) var epochDay: Int
    var json: Data

    init(epochDay: Int, json: Data) {
        self.epochDay = epochDay
        self.json = json
    }
}

/// The app's single source of truth: profile and settings, the stored days, and the state built from them.
@MainActor
@Observable
final class PulseStore {
    private(set) var profile: Profile?
    private(set) var dashboard: [String]
    private(set) var days: [DayLog] = []
    private(set) var state: HomeState?
    private(set) var lastSync: Date?
    private(set) var syncing = false
    private(set) var healthAsked = false

    private let container: ModelContainer
    private let defaults = Snapshot.defaults
    private let demo: Bool
    var isDemo: Bool { demo }

    init(demo: Bool = false) {
        self.demo = demo
        healthAsked = demo
        let schema = Schema([StoredDay.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: demo)
        container = (try? ModelContainer(for: schema, configurations: [config]))
            ?? (try! ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)]))
        if demo {
            profile = DemoData.profile
            dashboard = Tiles.defaultIds
            days = DemoData.days()
            lastSync = Date()
        } else {
            profile = (defaults.data(forKey: "profile")).flatMap { try? JSONDecoder().decode(Profile.self, from: $0) }
            dashboard = defaults.stringArray(forKey: "dashboard") ?? Tiles.defaultIds
            lastSync = defaults.object(forKey: "lastSync") as? Date
            days = loadDays()
        }
        rebuild()
    }

    // MARK: - Settings

    func saveProfile(_ p: Profile) {
        profile = p
        if !demo, let data = try? JSONEncoder().encode(p) { defaults.set(data, forKey: "profile") }
        rebuild()
    }

    func setDashboard(_ ids: [String]) {
        dashboard = ids
        if !demo { defaults.set(ids, forKey: "dashboard") }
    }

    func reset() {
        let ctx = container.mainContext
        try? ctx.delete(model: StoredDay.self)
        try? ctx.save()
        for k in ["profile", "dashboard", "lastSync"] { defaults.removeObject(forKey: k) }
        profile = nil
        days = []
        dashboard = Tiles.defaultIds
        state = nil
    }

    // MARK: - Health

    func connectHealth() async {
        guard !demo, HealthReader.isAvailable else { return }
        try? await HealthReader.shared.requestAuthorization()
        healthAsked = true
        startObserving()
        await sync()
    }

    func startObserving() {
        guard !demo, HealthReader.isAvailable else { return }
        HealthReader.shared.observe { [weak self] in await self?.sync() }
    }

    /// Reads Apple Health (90 days the first time, then the last 3), stores the days, rebuilds the state,
    /// and refreshes the widgets and the watch.
    func sync() async {
        guard !demo, HealthReader.isAvailable, !syncing else { return }
        healthAsked = await HealthReader.shared.hasAsked()
        guard healthAsked else { return }
        syncing = true
        defer { syncing = false }
        let today = LocalDate.today()
        let firstDone = defaults.bool(forKey: "firstSyncDone")
        let from = today.minusDays(firstDone ? 3 : 90)
        let fresh = await HealthReader.shared.days(from: from, to: today)
        store(fresh)
        defaults.set(true, forKey: "firstSyncDone")
        lastSync = Date()
        defaults.set(lastSync, forKey: "lastSync")
        rebuild()
    }

    /// Fills onboarding from what Health already knows (height, age, sex, latest weight).
    func healthBody() async -> HealthReader.Body? {
        guard !demo, HealthReader.isAvailable else { return nil }
        return await HealthReader.shared.body()
    }

    // MARK: - Storage

    private func loadDays() -> [DayLog] {
        let rows = (try? container.mainContext.fetch(FetchDescriptor<StoredDay>(sortBy: [SortDescriptor(\.epochDay)]))) ?? []
        return rows.compactMap { try? JSONDecoder().decode(DayLog.self, from: $0.json) }
    }

    private func store(_ fresh: [DayLog]) {
        let ctx = container.mainContext
        let enc = JSONEncoder()
        for d in fresh {
            guard let data = try? enc.encode(d) else { continue }
            let key = d.date.epochDay
            let existing = try? ctx.fetch(FetchDescriptor<StoredDay>(predicate: #Predicate { $0.epochDay == key })).first
            if let existing { existing.json = data } else { ctx.insert(StoredDay(epochDay: key, json: data)) }
        }
        try? ctx.save()
        days = loadDays()
    }

    /// Rebuilds the state for "now" (also called each minute by the Today screen so time-of-day moods move).
    func rebuild() {
        guard let p = profile else { state = nil; return }
        let s = HomeState.build(profile: p, days: days, now: Date(), lastSync: lastSync)
        state = s
        guard !demo || ProcessInfo.processInfo.arguments.contains("-demoWidgets") else { return }
        let snap = Snapshot(s)
        var old = Snapshot.load()
        old?.updated = snap.updated
        if snap != old {
            snap.save()
            WidgetCenter.shared.reloadAllTimelines()
        }
        WatchLink.shared.send(profile: p, snapshot: snap)
    }
}
