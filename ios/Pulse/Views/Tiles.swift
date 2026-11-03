import SwiftUI

/// What a dashboard tile shows: the number and the unit line under it.
struct DashValue { let value: String; let unit: String }

/// One number the dashboard can show. [read] gives nil when there's no data for it.
struct DashTile: Identifiable {
    let id: String
    let label: String
    let group: String
    let color: Color
    let read: (HomeState) -> DashValue?

    /// Which Today section the tile opens.
    var area: Area {
        switch group {
        case "Calories": .calories
        case "Activity": .activity
        case "Weight": .body
        case "Heart": .heart
        default: .sleep
        }
    }
}

/// The sections behind the tiles, each with its icon.
enum Area: String, Identifiable, CaseIterable {
    case calories, activity, body, heart, sleep
    var id: String { rawValue }

    var title: String {
        switch self {
        case .calories: "Calories"
        case .activity: "Activity"
        case .body: "Body"
        case .heart: "Heart and vitals"
        case .sleep: "Sleep"
        }
    }

    var icon: String {
        switch self {
        case .calories: "flame.fill"
        case .activity: "figure.walk"
        case .body: "scalemass.fill"
        case .heart: "heart.fill"
        case .sleep: "moon.fill"
        }
    }

    var color: Color {
        switch self {
        case .calories: C.green
        case .activity: C.steps
        case .body: C.wt
        case .heart: C.heart
        case .sleep: C.sleep
        }
    }
}

private func d1(_ v: Double) -> String { String(format: "%.1f", v) }

/// The newest value of [pick] in the last week, with "" for today or " · 30 Oct" for an older day.
private func recent<T>(_ s: HomeState, _ pick: (DayLog) -> T?) -> (T, String)? {
    guard let (v, date) = s.latest(pick) else { return nil }
    return (v, date == s.today ? "" : " · \(Format.date(date, s.today))")
}

enum Tiles {
    static let defaultIds = [
        "burned", "eaten", "calories", "left_to_eat", "steps", "distance",
        "weight", "weight_trend", "resting_only", "resting_hr", "sleep", "deep_sleep",
    ]

    /// Every number Pulse can put on the dashboard, grouped like the sections.
    static let all: [DashTile] = [
        DashTile(id: "calories", label: "Calories", group: "Calories", color: C.green) { s in
            s.balance.map { DashValue(value: Format.int(abs($0)), unit: Words.balance(eatenMore: $0 < 0)) }
        },
        DashTile(id: "left_to_eat", label: "Left to eat", group: "Calories", color: C.green) { s in
            let b = s.budget
            return b.left < 0 ? DashValue(value: Format.int(-b.left), unit: "kcal past today’s plan") : DashValue(value: Format.int(b.left), unit: "kcal left to eat")
        },
        DashTile(id: "burned", label: "Burned", group: "Calories", color: C.food) { s in s.burned.map { DashValue(value: Format.int($0), unit: "kcal so far") } },
        DashTile(id: "eaten", label: "Eaten", group: "Calories", color: C.food) { s in s.eaten.map { DashValue(value: Format.int($0), unit: "kcal logged") } },
        DashTile(id: "active", label: "Active", group: "Calories", color: C.hot) { s in s.active.map { DashValue(value: Format.int($0), unit: "active kcal") } },
        DashTile(id: "avg7", label: "7-day balance", group: "Calories", color: C.green) { s in
            s.avg7.map { DashValue(value: Format.int(abs($0)), unit: $0 < 0 ? "a day more eaten" : "a day more burned") }
        },
        DashTile(id: "protein", label: "Protein", group: "Calories", color: C.food) { s in s.day.proteinG.map { DashValue(value: "\($0)", unit: "g protein") } },
        DashTile(id: "carbs", label: "Carbs", group: "Calories", color: C.food) { s in s.day.carbsG.map { DashValue(value: "\($0)", unit: "g carbs") } },
        DashTile(id: "fat", label: "Fat", group: "Calories", color: C.food) { s in s.day.fatG.map { DashValue(value: "\($0)", unit: "g fat") } },
        DashTile(id: "water", label: "Water", group: "Calories", color: C.steps) { s in s.day.waterMl.map { DashValue(value: d1(Double($0) / 1000), unit: "litres") } },
        DashTile(id: "streak", label: "Streak", group: "Calories", color: C.green) { s in
            DashValue(value: "\(s.streak)", unit: s.streak == 1 ? "day burned more" : "days burned more")
        },

        DashTile(id: "steps", label: "Steps", group: "Activity", color: C.steps) { s in DashValue(value: Format.int(s.steps), unit: "of \(Format.int(s.profile.stepGoal))") },
        DashTile(id: "distance", label: "Distance", group: "Activity", color: C.steps) { s in
            s.day.distanceM.map { DashValue(value: String(format: "%.2f", Double($0) / 1000), unit: "km · \(String(format: "%.1f", Double($0) / 1609.344)) mi") }
        },
        DashTile(id: "floors", label: "Floors", group: "Activity", color: C.steps) { s in s.day.floors.map { DashValue(value: "\($0)", unit: "floors climbed") } },
        DashTile(id: "exercise", label: "Exercise", group: "Activity", color: C.steps) { s in s.day.exerciseMin.map { DashValue(value: "\($0)", unit: "min exercise") } },

        DashTile(id: "weight", label: "Weight", group: "Weight", color: C.wt) { s in
            s.history.first { $0.weightKg != nil }.map { d in
                DashValue(value: Format.kg(d.weightKg!), unit: "kg \(d.date == s.today ? "today" : Format.date(d.date, s.today))")
            }
        },
        DashTile(id: "weight_trend", label: "Weight trend", group: "Weight", color: C.wt) { s in s.trendKg.map { DashValue(value: Format.kg($0), unit: "kg trend") } },
        DashTile(id: "lost", label: "Lost", group: "Weight", color: C.wt) { s in
            s.trendKg.map { kg in
                let lost = s.profile.startKg - kg
                return DashValue(value: Format.kg(abs(lost)), unit: lost >= 0 ? "kg down" : "kg up")
            }
        },
        DashTile(id: "to_goal", label: "To goal", group: "Weight", color: C.wt) { s in
            s.trendKg.map { DashValue(value: Format.kg(max(0, $0 - s.profile.goalKg)), unit: "kg to \(Words.kgShort(s.profile.goalKg))") }
        },
        DashTile(id: "in_2_weeks", label: "In 2 weeks", group: "Weight", color: C.wt2) { s in
            let o = TwoWeeks.outlook(s.profile, s.history, s.today)
            return DashValue(value: Format.kg(o.toKg), unit: "kg · \(Words.signedKg(o.changeKg))\(o.early ? " · early" : "")")
        },
        DashTile(id: "goal_date", label: "Goal date", group: "Weight", color: C.wt2) { s in
            s.forecast.goal.projected.map { DashValue(value: Format.date($0, s.today), unit: "at this pace") }
        },
        DashTile(id: "bmi", label: "BMI", group: "Weight", color: C.wt) { s in
            guard let kg = s.trendKg, s.profile.heightCm > 0 else { return nil }
            let m = s.profile.heightCm / 100
            return DashValue(value: d1(kg / (m * m)), unit: "BMI")
        },
        DashTile(id: "body_fat", label: "Body fat", group: "Weight", color: C.wt) { s in recent(s) { $0.bodyFatPct }.map { DashValue(value: d1($0.0), unit: "%\($0.1)") } },
        DashTile(id: "lean", label: "Lean mass", group: "Weight", color: C.wt) { s in recent(s) { $0.leanKg }.map { DashValue(value: d1($0.0), unit: "kg lean\($0.1)") } },

        DashTile(id: "resting_hr", label: "Heart", group: "Heart", color: C.heart) { s in
            if let l = s.day.hrLatest { return DashValue(value: "\(l)", unit: "bpm at \(s.day.hrLatestAt ?? "—")") }
            if let (v, w) = recent(s, { $0.restingHr }) { return DashValue(value: "\(v)", unit: "bpm resting\(w)") }
            return s.day.hrAvg.map { DashValue(value: "\($0)", unit: "bpm average") }
        },
        DashTile(id: "resting_only", label: "Resting heart", group: "Heart", color: C.heart) { s in recent(s) { $0.restingHr }.map { DashValue(value: "\($0.0)", unit: "bpm resting\($0.1)") } },
        DashTile(id: "avg_hr", label: "Avg heart", group: "Heart", color: C.heart) { s in s.day.hrAvg.map { DashValue(value: "\($0)", unit: "bpm average") } },
        DashTile(id: "hrv", label: "HRV", group: "Heart", color: C.heart) { s in recent(s) { $0.hrvMs }.map { DashValue(value: "\($0.0)", unit: "ms\($0.1)") } },
        DashTile(id: "spo2", label: "Blood oxygen", group: "Heart", color: C.heart) { s in recent(s) { $0.spo2Pct }.map { DashValue(value: "\(roundToInt($0.0))", unit: "%\($0.1)") } },
        DashTile(id: "breathing", label: "Breathing", group: "Heart", color: C.heart) { s in recent(s) { $0.breathingRate }.map { DashValue(value: d1($0.0), unit: "breaths/min\($0.1)") } },
        DashTile(id: "vo2", label: "VO2 max", group: "Heart", color: C.heart) { s in recent(s) { $0.vo2Max }.map { DashValue(value: d1($0.0), unit: "ml/kg/min\($0.1)") } },

        DashTile(id: "sleep", label: "Sleep", group: "Sleep", color: C.sleep) { s in s.day.sleepMinutes.map { DashValue(value: Words.hm($0), unit: "last night") } },
        DashTile(id: "deep_sleep", label: "Deep sleep", group: "Sleep", color: C.sleep) { s in s.day.sleepDeepMin.map { DashValue(value: Words.hm($0), unit: "deep") } },
        DashTile(id: "rem_sleep", label: "REM sleep", group: "Sleep", color: C.sleep) { s in s.day.sleepRemMin.map { DashValue(value: Words.hm($0), unit: "REM") } },
        DashTile(id: "bedtime", label: "Bedtime", group: "Sleep", color: C.sleep) { s in s.day.bedTime.map { DashValue(value: $0, unit: "wake \(s.day.wakeTime ?? "—")") } },
    ]

    static let byId: [String: DashTile] = Dictionary(uniqueKeysWithValues: all.map { ($0.id, $0) })

    /// Dashboard groups, in order, and the tile groups each holds.
    static let groups: [(String, [String])] = [
        ("Food", ["Calories"]), ("Move", ["Activity"]), ("Body", ["Weight"]), ("Recovery", ["Heart", "Sleep"]),
    ]

    private static func restingNow(_ s: HomeState) -> Int? { s.history.prefix(7).lazy.compactMap(\.restingHr).first }
    private static func restingUsual(_ s: HomeState) -> [Int] { Array(s.history.compactMap(\.restingHr).dropFirst().prefix(30)) }

    /// The calm status of a tile (Mood.swift holds the rules), or nil for numbers that just show plain.
    static func status(_ id: String, _ s: HomeState) -> Status? {
        let t = s.time
        let goal = s.profile.stepGoal
        switch id {
        case "steps": return Moods.steps(s.steps, goal, t)
        case "distance":
            guard s.day.distanceM != nil else { return nil }
            var st = Moods.steps(s.steps, goal, t)
            if st.mood != .wait { st.why = "Moves with your steps" }
            st.bar = nil
            return st
        case "burned": return Moods.burned(s.burned, s.budget.burn, t)
        case "eaten": return Moods.eaten(s.eaten, s.budget.budget)
        case "calories": return Moods.balance(s.balance)
        case "left_to_eat": return Moods.left(s.budget.left, s.budget.budget)
        case "weight": return Moods.weight(s.history.first { $0.weightKg != nil }?.weightKg, s.trendKg)
        case "weight_trend": return s.trendKg == nil ? nil : Moods.trend(s.trend, s.today)
        case "resting_only": return Moods.resting(restingNow(s), restingUsual(s))
        case "resting_hr":
            if s.day.hrLatest != nil { return Status(mood: .wait, word: "Latest", why: "Moves with what you’re doing") }
            return Moods.resting(restingNow(s), restingUsual(s))
        case "sleep": return Moods.sleep(s.day.sleepMinutes)
        case "deep_sleep": return Moods.deep(s.day.sleepDeepMin)
        case "streak":
            return s.streak > 0 ? Status(mood: .good, word: Moods.onTrack, why: "Keep it going", win: "\(s.streak) days in a row of burning more than you eat.") : nil
        default: return nil
        }
    }
}
