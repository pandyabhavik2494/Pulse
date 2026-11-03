import Foundation

enum Energy {
    static let kcalPerKg = 7700.0

    /// Mifflin-St Jeor basal metabolic rate, kcal/day:
    /// 10 * weight(kg) + 6.25 * height(cm) - 5 * age + 5 (men) or - 161 (women), rounded.
    static func bmr(_ p: Profile, _ weightKg: Double) -> Int {
        let base = 10.0 * weightKg + 6.25 * p.heightCm - 5.0 * Double(p.age)
        return roundToInt(base + (p.male ? 5.0 : -161.0))
    }

    /// Resting kcal burned so far today: bmr * fraction of the day elapsed at [secondOfDay].
    static func restingSoFar(_ p: Profile, _ weightKg: Double, secondOfDay: Double) -> Int {
        roundToInt(Double(bmr(p, weightKg)) * (secondOfDay / 86_400.0))
    }

    /// Splits a day's burn into (resting, active). Uses total and active when both exist
    /// (resting = total - active, min 0); if only total exists, active = total - restingEstimate (min 0)
    /// and resting is the rest of the total; if only active, resting = restingEstimate. Nil when neither exists.
    static func split(_ total: Int?, _ active: Int?, _ restingEstimate: Int) -> (Int, Int)? {
        switch (total, active) {
        case let (t?, a?): return (max(0, t - a), a)
        case let (t?, nil):
            let a = max(0, t - restingEstimate)
            return (t - a, a)
        case let (nil, a?): return (restingEstimate, a)
        default: return nil
        }
    }
}

enum Trend {
    /// Smoothing factor per weigh-in day (exponential moving average).
    static let alpha = 0.1

    /// One point per calendar day from the first weigh-in (or [from], whichever is later) to [to].
    /// Days without a weigh-in carry the trend forward. If the list holds the same date twice, the last
    /// non-nil weight wins. Seeded with the first weigh-in. Empty if there are no weigh-ins.
    /// The average always starts from the very first weigh-in on record, so a narrower window shows the same values.
    static func of(_ days: [DayLog], from: LocalDate, to: LocalDate) -> [TrendPoint] {
        var weights: [LocalDate: Double] = [:]
        for d in days {
            guard let w = d.weightKg, !w.isNaN, w > 0, !d.date.isAfter(to) else { continue }
            weights[d.date] = w
        }
        guard let first = weights.keys.min() else { return [] }
        let start = from.isAfter(first) ? from : first
        var out: [TrendPoint] = []
        var trend = weights[first]!
        var day = first
        while !day.isAfter(to) {
            let raw = weights[day]
            if let raw { trend += alpha * (raw - trend) }
            if !day.isBefore(start) { out.append(TrendPoint(date: day, raw: raw, trend: trend)) }
            day = day.plusDays(1)
        }
        return out
    }
}

/// Today's food budget: how much Bhavik can eat today and still lose at the plan's pace
/// (0.75 kg a week = 825 kcal a day burned more than eaten).
///
/// The burn it starts from is his usual full-day burn (average of the last 7 complete days), or today's burn
/// so far if that's already higher, or an estimate from his body when there's no data yet.
enum Budget {
    static let paceKcal: Int = roundToInt(Forecaster.startingGuessKgPerWeek * Energy.kcalPerKg / 7.0)

    /// Lightly active: what a desk job plus some walking burns, on top of BMR.
    static let estimateFactor = 1.3

    struct Day: Equatable, Sendable {
        /// The full-day burn the budget assumes.
        let burn: Int
        /// burn - paceKcal.
        let budget: Int
        let eaten: Int
        /// budget - eaten; negative = past the budget.
        let left: Int
        /// Complete days behind [burn]; 0 = estimated from height, weight and age.
        let burnDays: Int
    }

    static func today(_ p: Profile, _ days: [DayLog], _ today: LocalDate, _ weightKg: Double?) -> Day {
        let past = days.filter { $0.date.isBefore(today) && !$0.date.isBefore(today.minusDays(14)) && $0.burnedTotal != nil }
            .sorted { $0.date > $1.date }.prefix(7)
        let todayLog = days.first { $0.date == today }
        let usual = past.isEmpty ? nil : roundToInt(past.map { $0.burnedTotal! }.average)
        let estimate = roundToInt(Double(Energy.bmr(p, weightKg ?? p.startKg)) * estimateFactor)
        let burn = max(usual ?? estimate, todayLog?.burnedTotal ?? 0)
        let budget = burn - paceKcal
        let eaten = todayLog?.eaten ?? 0
        return Day(burn: burn, budget: budget, eaten: eaten, left: budget - eaten, burnDays: past.count)
    }
}
