import Foundation

/// How a number on the dashboard is doing, in calm words (Bhavik: encouraging, never scary).
/// good = on track, steady = fine, grow = room to grow, wait = not enough data yet / too early / just info.
/// There is deliberately no "bad" state.
enum Mood: String, CaseIterable, Sendable { case good, steady, grow, wait }

/// [word] is the short label, [why] the one kind line under the number, [bar] a 0..1 fill (nil = no bar).
/// [win] is a sentence of praise for the verdict when good; [tip] one small next step when steady/grow.
struct Status: Equatable, Sendable {
    var mood: Mood
    var word: String
    var why: String
    var bar: Double? = nil
    var win: String? = nil
    var tip: String? = nil
}

enum Moods {
    static let onTrack = "On track"
    static let steady = "Steady"
    static let grow = "Room to grow"
    static let filling = "Filling in"

    /// Share of the waking day (7:00 to 22:00) gone by [t], 0..1. Steps happen while awake.
    static func wakingShare(_ t: LocalTime) -> Double { (((Double(t.hour) + Double(t.minute) / 60.0) - 7.0) / 15.0).clamped(0, 1) }

    /// Share of the whole day gone by [t], 0..1. The burned total counts from midnight.
    static func dayShare(_ t: LocalTime) -> Double { Double(t.secondOfDay) / 86_400.0 }

    private static func n(_ v: Int) -> String { Format.int(v) }

    static func steps(_ steps: Int, _ goal: Int, _ t: LocalTime) -> Status {
        let bar = (Double(steps) / Double(max(goal, 1))).clamped(0, 1)
        if steps >= goal { return Status(mood: .good, word: onTrack, why: "Goal reached", bar: 1, win: "You hit your step goal.") }
        let share = wakingShare(t)
        if share < 0.15 { return Status(mood: .wait, word: "Early", why: "The day’s just started", bar: bar) }
        let expected = max(roundToInt(Double(goal) * share), 1)
        let ratio = Double(steps) / Double(expected)
        if ratio >= 0.9 { return Status(mood: .good, word: onTrack, why: "On pace for \(n(goal))", bar: bar, win: "Your steps are on pace.") }
        if ratio >= 0.6 { return Status(mood: .steady, word: steady, why: "About \(n(expected - steps)) behind your pace", bar: bar, tip: "A 10-minute walk adds about 1,000 steps.") }
        return Status(mood: .grow, word: grow, why: "A walk later would catch you up", bar: bar, tip: "A 10-minute walk adds about 1,000 steps.")
    }

    /// [usual] is the usual full-day burn (Budget.Day.burn).
    static func burned(_ burned: Int?, _ usual: Int, _ t: LocalTime) -> Status {
        guard let burned else { return Status(mood: .wait, word: filling, why: "Shows when Apple Health has your burn") }
        let share = dayShare(t)
        let bar = (Double(burned) / Double(max(usual, 1))).clamped(0, 1)
        if share < 0.1 { return Status(mood: .wait, word: "Early", why: "The day’s just started", bar: bar) }
        let expected = max(roundToInt(Double(usual) * share), 1)
        let ratio = Double(burned) / Double(expected)
        if ratio >= 0.85 { return Status(mood: .good, word: onTrack, why: ratio >= 1.02 ? "A bit ahead of your usual" : "Right on your usual pace", bar: bar, win: "You’re burning right on pace.") }
        if ratio >= 0.7 { return Status(mood: .steady, word: steady, why: "Close to your usual by now", bar: bar, tip: "A short walk gives burned a nudge.") }
        return Status(mood: .grow, word: grow, why: "Quieter than usual so far", bar: bar, tip: "A short walk gives burned a nudge.")
    }

    /// [budget] is today's food budget (Budget.Day.budget).
    static func eaten(_ eaten: Int?, _ budget: Int) -> Status {
        guard let eaten else { return Status(mood: .wait, word: filling, why: "Shows once you log in MyFitnessPal") }
        let bar = (Double(eaten) / Double(max(budget, 1))).clamped(0, 1)
        if eaten <= budget { return Status(mood: .good, word: onTrack, why: "Within today’s plan", bar: bar, win: "Your eating is within plan.") }
        if eaten <= budget + 200 { return Status(mood: .steady, word: steady, why: "Just above today’s plan", bar: bar, tip: "A short walk after dinner evens it out.") }
        return Status(mood: .grow, word: grow, why: "\(n(eaten - budget)) above today’s plan", bar: bar, tip: "A 20-minute walk after dinner brings today back toward plan.")
    }

    /// [balance] = burned - eaten so far (positive = burned more).
    static func balance(_ balance: Int?) -> Status {
        guard let balance else { return Status(mood: .wait, word: filling, why: "Shows once food is logged") }
        if balance >= 0 { return Status(mood: .good, word: onTrack, why: "Burned more than eaten so far", win: "You’ve burned more than you’ve eaten.") }
        if balance >= -200 { return Status(mood: .steady, word: steady, why: "Nearly even", tip: "A short walk tips it back.") }
        return Status(mood: .grow, word: grow, why: "A short walk evens this out", tip: "A 20-minute walk after dinner brings today back toward plan.")
    }

    /// [left] = budget - eaten (negative = above plan).
    static func left(_ left: Int, _ budget: Int) -> Status {
        let bar: Double? = budget <= 0 ? nil : (Double(left) / Double(budget)).clamped(0, 1)
        if left >= 200 { return Status(mood: .good, word: onTrack, why: left >= budget ? "Your whole day’s budget is open" : "Room left in today’s plan", bar: bar, win: "Your budget’s in good shape.") }
        if left >= 0 { return Status(mood: .steady, word: steady, why: "Nearly used for today", bar: bar) }
        return Status(mood: .grow, word: grow, why: "Tomorrow is a fresh budget", bar: 1, tip: "A 20-minute walk after dinner brings today back toward plan.")
    }

    /// Last weigh-in against the trend. Never worse than steady: daily weight is mostly water.
    static func weight(_ kg: Double?, _ trendKg: Double?) -> Status? {
        guard let kg else { return nil }
        guard let trendKg else { return Status(mood: .wait, word: filling, why: "A trend forms after a few weigh-ins") }
        let diff = kg - trendKg
        if diff <= -0.1 { return Status(mood: .good, word: onTrack, why: "Below your trend", win: "You weighed in below your trend.") }
        if diff <= 1.0 { return Status(mood: .steady, word: steady, why: "Within the normal daily swing") }
        return Status(mood: .steady, word: steady, why: "Water and salt move this a lot")
    }

    /// The trend's change over the last week; needs a week of trend with at least 3 weigh-ins.
    static func trend(_ points: [TrendPoint], _ today: LocalDate) -> Status {
        let past = points.filter { !$0.date.isAfter(today) }.sorted { $0.date < $1.date }
        let weekAgo = past.last { !$0.date.isAfter(today.minusDays(7)) }
        guard let weekAgo, let now = past.last, past.filter({ $0.raw != nil }).count >= 3 else {
            return Status(mood: .wait, word: filling, why: "Gets clearer after a week of weigh-ins")
        }
        let change = now.trend - weekAgo.trend
        let kg = String(format: "%.1f", abs(change))
        if change <= -0.1 { return Status(mood: .good, word: onTrack, why: "Down \(kg) kg this week", win: "Your trend is heading down.") }
        if change < 0.1 { return Status(mood: .steady, word: steady, why: "Holding steady this week") }
        return Status(mood: .grow, word: grow, why: "A few days of burning more usually turns it", tip: "A few days of burning more than you eat usually turns the trend.")
    }

    /// Resting heart rate against Bhavik's own usual (median of up to 30 readings), or general ranges before there's a usual.
    static func resting(_ bpm: Int?, _ recent: [Int]) -> Status? {
        guard let bpm else { return nil }
        if recent.count >= 7 {
            let usual = recent.sorted()[recent.count / 2]
            if bpm <= usual { return Status(mood: .good, word: onTrack, why: "At or below your usual \(usual)", win: "Your heart is nicely rested.") }
            if bpm <= usual + 3 { return Status(mood: .steady, word: steady, why: "Close to your usual \(usual)") }
            return Status(mood: .grow, word: grow, why: "A little above your usual \(usual)", tip: "Water and an easy evening help your resting heart.")
        }
        if bpm < 70 { return Status(mood: .good, word: onTrack, why: "Nicely rested", win: "Your heart is nicely rested.") }
        if bpm < 90 { return Status(mood: .steady, word: steady, why: "Tends to drift down with regular walks") }
        return Status(mood: .grow, word: grow, why: "Tends to drift down with regular walks", tip: "Regular walks help your resting heart come down.")
    }

    static func sleep(_ min: Int?) -> Status? {
        guard let min else { return nil }
        let bar = (Double(min) / 480).clamped(0, 1)
        if min >= 7 * 60 { return Status(mood: .good, word: onTrack, why: "Right in the 7 to 9 hour zone", bar: bar, win: "You slept \(min / 60)h \(min % 60)m, nice.") }
        if min >= 6 * 60 { return Status(mood: .steady, word: steady, why: "Close to the 7 hour mark", bar: bar, tip: "An earlier night tonight would help.") }
        return Status(mood: .grow, word: grow, why: "An earlier night tonight would help", bar: bar, tip: "An earlier night tonight would help.")
    }

    static func deep(_ min: Int?) -> Status? {
        guard let min else { return nil }
        let bar = (Double(min) / 75).clamped(0, 1)
        if min >= 60 { return Status(mood: .good, word: onTrack, why: "Plenty of deep sleep", bar: bar, win: "Good deep sleep last night.") }
        if min >= 40 { return Status(mood: .steady, word: steady, why: "Around 1h is the sweet spot", bar: bar, tip: "Winding down a little earlier tonight helps deep sleep.") }
        return Status(mood: .grow, word: grow, why: "Around 1h is the sweet spot", bar: bar, tip: "Winding down a little earlier tonight helps deep sleep.")
    }

    /// The verdict's headline from the shown statuses, morning-aware. Never alarming.
    static func headline(_ moods: [Mood], _ t: LocalTime) -> String {
        let good = moods.filter { $0 == .good }.count
        let grow = moods.filter { $0 == .grow }.count
        let steady = moods.filter { $0 == .steady }.count
        if grow == 0 && good >= steady { return t.hour < 12 ? "Good start" : "Going well" }
        if grow == 0 { return "Steady day" }
        if good >= grow { return "Mostly on track" }
        return "Close to plan"
    }
}
