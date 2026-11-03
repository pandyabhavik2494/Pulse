import Foundation

/// Weight in two-week steps: where the last 7 days of eating take the weight in two weeks (gain or loss),
/// and what the scale trend actually did in the last few two-week stretches.
///
/// There's always an answer. With little data it's an early estimate and says so.
enum TwoWeeks {
    static let days = 14

    /// How many complete days of food the outlook averages (the latest ones).
    static let foodDays = 7

    /// How far back to look for food days and weigh-ins.
    static let lookbackDays = 28

    /// What the outlook is based on, from most to least reliable.
    enum Basis: Sendable {
        case foodAndScale, food, scale, today, startingGuess
    }

    struct Outlook: Equatable, Sendable {
        let fromKg: Double
        /// Negative = loss, positive = gain.
        let changeKg: Double
        let basis: Basis
        /// Complete days of food behind it (0..7).
        let foodDays: Int
        /// Average balance of those days, kcal a day (positive = burned more than eaten).
        let avgBalance: Int?

        var toKg: Double { fromKg + changeKg }

        /// Fewer than a full week of food: say it's early.
        var early: Bool { basis != .foodAndScale && !(basis == .food && foodDays >= TwoWeeks.foodDays) }
    }

    /// Kg change over two weeks at [balance] kcal a day (positive = burned more than eaten).
    static func changeFor(_ balance: Double) -> Double { -balance * Double(days) / Energy.kcalPerKg }

    /// The outlook from every stored day. [p] gives the starting weight when there's no weigh-in at all.
    static func outlook(_ p: Profile, _ all: [DayLog], _ today: LocalDate) -> Outlook {
        let past = all.filter { $0.date.isBefore(today) && !$0.date.isBefore(today.minusDays(lookbackDays)) }
        let food = Array(past.filter { $0.balance != nil }.sorted { $0.date > $1.date }.prefix(foodDays))
        let trend = Trend.of(all, from: all.map(\.date).min() ?? today, to: today)
        let fromKg = trend.last?.trend
            ?? all.filter { $0.weightKg != nil }.max { $0.date < $1.date }?.weightKg
            ?? p.startKg

        let foodAvg = food.isEmpty ? nil : food.map { $0.balance! }.average
        let foodChange = foodAvg.map(changeFor)
        let scaleChange = scaleRatePerDay(all, today).map { $0 * Double(days) }
        let avg = foodAvg.map(roundToInt)

        if let foodChange, let scaleChange, food.count >= 3 {
            // Eating moves first, so it leads; the scale keeps it honest.
            return Outlook(fromKg: fromKg, changeKg: (2 * foodChange + scaleChange) / 3, basis: .foodAndScale, foodDays: food.count, avgBalance: avg)
        }
        if let foodChange { return Outlook(fromKg: fromKg, changeKg: foodChange, basis: .food, foodDays: food.count, avgBalance: avg) }
        if let scaleChange { return Outlook(fromKg: fromKg, changeKg: scaleChange, basis: .scale, foodDays: 0, avgBalance: nil) }
        if let t = all.first(where: { $0.date == today })?.balance {
            return Outlook(fromKg: fromKg, changeKg: changeFor(Double(t)), basis: .today, foodDays: 0, avgBalance: t)
        }
        return Outlook(fromKg: fromKg, changeKg: -Forecaster.startingGuessKgPerWeek * Double(days) / 7.0, basis: .startingGuess, foodDays: 0, avgBalance: nil)
    }

    /// Kg a day from the raw weigh-ins of the last 4 weeks (least-squares line), or nil with fewer than
    /// 3 weigh-ins or under a week between the first and last.
    static func scaleRatePerDay(_ all: [DayLog], _ today: LocalDate) -> Double? {
        let from = today.minusDays(lookbackDays)
        let pts = all.filter { $0.weightKg != nil && !$0.date.isBefore(from) && !$0.date.isAfter(today) }
            .map { (Double(LocalDate.daysBetween(from, $0.date)), $0.weightKg!) }
        if pts.count < 3 { return nil }
        if pts.map(\.0).max()! - pts.map(\.0).min()! < 7 { return nil }
        let mx = pts.map(\.0).average
        let my = pts.map(\.1).average
        let sxx = pts.reduce(0.0) { $0 + ($1.0 - mx) * ($1.0 - mx) }
        if sxx == 0 { return nil }
        return pts.reduce(0.0) { $0 + ($1.0 - mx) * ($1.1 - my) } / sxx
    }

    /// One stretch of the trend. [changeKg] negative = lost.
    struct Period: Equatable, Sendable {
        let start: LocalDate
        let end: LocalDate
        let changeKg: Double
        var days: Int { LocalDate.daysBetween(start, end) }
    }

    /// The last [count] two-week stretches ending today, newest first, on the smoothed trend of every weigh-in.
    /// When the weigh-ins don't reach back two weeks yet, one shorter stretch from the first weigh-in is given.
    static func periods(_ all: [DayLog], _ today: LocalDate, count: Int = 3) -> [Period] {
        guard let first = all.filter({ $0.weightKg != nil }).map(\.date).min() else { return [] }
        let trend = Trend.of(all, from: first, to: today)
        if trend.isEmpty { return [] }
        func at(_ d: LocalDate) -> Double? { trend.last { !$0.date.isAfter(d) }?.trend }
        var out: [Period] = []
        var end = today
        for _ in 0..<count {
            let start = end.minusDays(days)
            if start.isBefore(first) {
                // A shorter first stretch, from the first weigh-in, when nothing longer fits yet.
                if out.isEmpty && end.isAfter(first) && trend.filter({ $0.raw != nil }).count >= 2 {
                    out.append(Period(start: first, end: end, changeKg: at(end)! - at(first)!))
                }
                return out
            }
            out.append(Period(start: start, end: end, changeKg: at(end)! - at(start)!))
            end = start
        }
        return out
    }
}
