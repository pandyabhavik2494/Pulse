import Foundation

/// Milestones and projected dates.
///
/// Dates follow the average of the last 7 complete days of food (eat more this week and every date moves
/// later, eat less and they come sooner). Before the first complete day, the 0.75 kg/week starting guess.
/// A constant rate: real loss slows a little as weight drops, so far-off dates are a little optimistic;
/// the rate is recomputed every day. The scale is still compared with logged food to learn the real burn.
enum Forecaster {
    static let startingGuessKgPerWeek = 0.75
    static let maxKgPerWeek = 1.5

    /// Days of trend needed before the scale is compared with logged food (learned burn).
    static let minDays = 14
    /// Complete days of food the pace averages (the latest ones).
    static let avgDays = 7
    /// Longest window used to compare the scale trend with logged food.
    static let scaleWindowDays = 28
    /// Targets closer than this are merged into one milestone.
    static let mergeKg = 0.5

    private static let eps = 1e-9

    // Label priority when merging targets that are within mergeKg of each other.
    private static let priKg = 0
    private static let priPercent = 1
    private static let priUnder100 = 2

    private struct Target { let kg: Double; let label: String; let priority: Int }

    /// The milestone weights and labels for a profile: every 5 kg ("First 5 kg", "10 kg down", ...),
    /// 5/10/15/20/25% down, "Under 100 kg" when the journey crosses 100 kg, and the goal ("Goal").
    /// Only targets strictly between start and goal are kept, plus the goal, which is always last.
    /// Targets within 0.5 kg of each other merge, keeping the more meaningful label
    /// (Goal > Under 100 kg > percentage > kg step). Sorted from heaviest to lightest.
    static func targets(_ p: Profile) -> [(kg: Double, label: String)] {
        let start = p.startKg
        let goal = p.goalKg
        let between = { (kg: Double) in kg < start - eps && kg > goal + eps }
        var raw: [Target] = []
        var k = 1
        while true {
            let kg = start - 5.0 * Double(k)
            if kg <= goal + eps { break }
            raw.append(Target(kg: kg, label: k == 1 ? "First 5 kg" : "\(5 * k) kg down", priority: priKg))
            k += 1
        }
        for pct in [5, 10, 15, 20, 25] {
            let kg = Format.round1(start * Double(100 - pct) / 100.0)
            if between(kg) { raw.append(Target(kg: kg, label: "\(pct)% down", priority: priPercent)) }
        }
        if start > 100.0 && goal < 100.0 { raw.append(Target(kg: 100.0, label: "Under 100 kg", priority: priUnder100)) }

        // Anything within mergeKg of the goal folds into the goal.
        let candidates = raw.filter { between($0.kg) && $0.kg - goal >= mergeKg - eps }
            .enumerated()
            .sorted { a, b in
                if a.element.kg != b.element.kg { return a.element.kg > b.element.kg }
                if a.element.priority != b.element.priority { return a.element.priority > b.element.priority }
                return a.offset < b.offset
            }
            .map(\.element)

        // Greedy clustering from heaviest down: a target joins the current cluster if it is within
        // mergeKg of the cluster's heaviest member. Each cluster keeps its highest-priority target.
        var merged: [Target] = []
        var cluster: [Target] = []
        for t in candidates {
            if let head = cluster.first, head.kg - t.kg >= mergeKg - eps {
                merged.append(cluster.max { $0.priority < $1.priority }!)
                cluster = []
            }
            cluster.append(t)
        }
        if !cluster.isEmpty { merged.append(cluster.max { $0.priority < $1.priority }!) }

        return merged.map { ($0.kg, $0.label) } + [(goal, "Goal")]
    }

    /// The forecast as of [asOf], using only days up to and including asOf - 1 for averages (today is incomplete).
    static func forecast(_ p: Profile, _ days: [DayLog], _ trend: [TrendPoint], _ asOf: LocalDate) -> Forecast {
        let sortedTrend = trend.filter { !$0.date.isAfter(asOf) }.sorted { $0.date < $1.date }
        let current = sortedTrend.last?.trend ?? p.startKg
        let yesterday = asOf.minusDays(1)

        // One log per date (last wins), only complete days.
        var byDate: [LocalDate: DayLog] = [:]
        for d in days where d.date.isBefore(asOf) { byDate[d.date] = d }
        let balances = byDate.values.filter { $0.balance != nil }.sorted { $0.date > $1.date }

        var avgDeficit: Int? = nil
        var learnedBurn: Int? = nil
        let rate: Double
        let source: RateSource

        if balances.isEmpty {
            rate = startingGuessKgPerWeek
            source = .startingGuess
        } else {
            let avg = balances.prefix(avgDays).map { $0.balance! }.average
            avgDeficit = roundToInt(avg)
            let deficitRate = avg / Energy.kcalPerKg * 7.0

            let past = sortedTrend.filter { !$0.date.isAfter(yesterday) }
            let span = past.isEmpty ? 0 : LocalDate.daysBetween(past.first!.date, past.last!.date)
            if span >= minDays {
                let end = past.last!
                let window = min(span, scaleWindowDays)
                let windowStart = end.date.minusDays(window)
                let startTrend = trendOn(past, windowStart)!
                let drop = startTrend - end.trend
                let eaten = byDate.values.filter { $0.eaten != nil && $0.date.isAfter(windowStart) && !$0.date.isAfter(end.date) }
                if !eaten.isEmpty {
                    learnedBurn = roundToInt(eaten.map { $0.eaten! }.average + drop * Energy.kcalPerKg / Double(window))
                }
            }
            rate = deficitRate
            source = .deficits
        }

        let stalled = rate <= 0.0
        let finalRate = stalled ? 0.0 : min(rate, maxKgPerWeek)
        let finalSource: RateSource = stalled ? .stalled : source

        let milestones = targets(p).map { t -> Milestone in
            let reached = sortedTrend.first { $0.trend <= t.kg + eps }?.date
            var projected: LocalDate? = nil
            if reached == nil && !stalled {
                let daysNeeded = max(1, Int(((current - t.kg) / (finalRate / 7.0) - eps).rounded(.up)))
                projected = asOf.plusDays(daysNeeded)
            }
            return Milestone(kg: t.kg, label: t.label, reachedOn: reached, projected: projected, isGoal: t.label == "Goal")
        }
        return Forecast(asOf: asOf, currentTrendKg: current, rateKgPerWeek: finalRate, source: finalSource,
                        avgDeficit: avgDeficit, learnedBurn: learnedBurn, milestones: milestones)
    }

    /// Days the next unreached milestone moved: positive = earlier than [previous] projected. Nil if not comparable.
    static func shiftDays(_ current: Forecast, _ previous: Forecast) -> Int? {
        guard let next = current.next, let now = next.projected,
              let before = previous.milestones.first(where: { abs($0.kg - next.kg) < 1e-6 })?.projected else { return nil }
        return LocalDate.daysBetween(now, before)
    }

    /// whatIf for every milestone not reached yet, heaviest first: (milestone, date at [deficitPerDay]).
    static func whatIfAll(_ f: Forecast, _ trendKg: Double, _ from: LocalDate, _ deficitPerDay: Int) -> [(Milestone, LocalDate?)] {
        f.milestones.filter { $0.reachedOn == nil }.map { m in
            if deficitPerDay <= 0 { return (m, nil) }
            if trendKg <= m.kg { return (m, from) }
            return (m, from.plusDays(Int(((trendKg - m.kg) / (Double(deficitPerDay) / Energy.kcalPerKg) - eps).rounded(.up))))
        }
    }

    /// Goal date if Bhavik averaged [deficitPerDay] kcal burned more than eaten from [from] at [trendKg].
    /// Nil if deficit <= 0. Returns [from] if already at or below goal.
    static func whatIf(_ p: Profile, _ trendKg: Double, _ from: LocalDate, _ deficitPerDay: Int) -> LocalDate? {
        if deficitPerDay <= 0 { return nil }
        let toLose = trendKg - p.goalKg
        if toLose <= 0 { return from }
        let perDay = Double(deficitPerDay) / Energy.kcalPerKg
        return from.plusDays(Int((toLose / perDay - eps).rounded(.up)))
    }

    /// The trend value on [date]: the last point on or before it, or nil. [points] must be sorted by date.
    static func trendOn(_ points: [TrendPoint], _ date: LocalDate) -> Double? {
        points.last { !$0.date.isAfter(date) }?.trend
    }
}
