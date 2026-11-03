import Foundation

/// Today's glance, written by the app for its widgets and the watch (and by the watch for its complications).
struct Snapshot: Codable, Equatable, Sendable {
    var day: LocalDate
    var updated: Date
    var name: String
    var burned: Int?
    var eaten: Int?
    var balance: Int?
    var left: Int
    var budget: Int
    var steps: Int
    var stepGoal: Int
    var headline: String
    var headlineGrow: Bool
    var trendKg: Double?
    var goalKg: Double
    var goalDate: LocalDate?

    init(_ s: HomeState) {
        day = s.today
        updated = s.now
        name = s.profile.name
        burned = s.burned
        eaten = s.eaten
        balance = s.balance
        left = s.budget.left
        budget = s.budget.budget
        steps = s.steps
        stepGoal = s.profile.stepGoal
        let v = Verdict(s.glanceStatuses, s.time)
        headline = v.headline
        headlineGrow = v.anyGrow
        trendKg = s.trendKg
        goalKg = s.profile.goalKg
        goalDate = s.forecast.goal.projected ?? s.forecast.goal.reachedOn
    }

    static let groupId = "group.com.bhavikpandya.pulse"
    private static let key = "snapshot"

    static var defaults: UserDefaults { UserDefaults(suiteName: groupId) ?? .standard }

    func save() {
        if let data = try? JSONEncoder().encode(self) { Self.defaults.set(data, forKey: Self.key) }
    }

    static func load() -> Snapshot? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }

    /// A glance for widget previews and the gallery.
    static var preview: Snapshot {
        let p = Profile(name: "Bhavik", heightCm: 175.3, age: 32, male: true, startKg: 120, goalKg: 85, startDate: LocalDate.today().minusDays(30))
        var days: [DayLog] = []
        for i in 0..<30 {
            days.append(DayLog(date: p.startDate.plusDays(i), steps: 9000, burnedTotal: 2900, eaten: 2100, weightKg: 120 - Double(i) * 0.1))
        }
        days.append(DayLog(date: LocalDate.today(), steps: 6400, burnedTotal: 2240, eaten: 1620))
        return Snapshot(HomeState.build(profile: p, days: days))
    }
}
