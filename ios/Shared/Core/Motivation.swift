import Foundation

/// Streaks, wins and all motivational copy. Plain English, no emoji, never guilt.
enum Motivation {
    private static let eps = 1e-9

    /// By 8 PM a few hours of resting burn are still to come (about 300 kcal), so keeping this much
    /// burned-more-than-eaten now still ends the day with a solid deficit of roughly 500.
    static let eveningKeepKcal = 200

    private static func index(_ days: [DayLog]) -> [LocalDate: DayLog] {
        var m: [LocalDate: DayLog] = [:]
        for d in days { m[d.date] = d }
        return m
    }

    /// Forgiving streak of days that burned more than eaten, ending yesterday (and including today if today already did).
    /// A day that ate more does NOT break it if the 7-day average balance ending that day still burned more.
    /// A day with no food logged is skipped. Forgiven days don't add. A balance of exactly 0 doesn't count.
    /// Today never breaks the streak: it isn't finished.
    static func streak(_ days: [DayLog], _ today: LocalDate) -> Int {
        let byDate = index(days)
        guard let earliest = byDate.keys.min() else { return 0 }
        var count = 0
        if let t = byDate[today]?.balance, t > 0 { count += 1 }
        var d = today.minusDays(1)
        while !d.isBefore(earliest) {
            if let b = byDate[d]?.balance {
                if b > 0 {
                    count += 1
                } else {
                    let avg = avgBalance(byDate, d, 7)
                    if avg == nil || avg! <= 0 { break }
                }
            }
            d = d.minusDays(1)
        }
        return count
    }

    /// Average daily balance over the 7 days before [today] that have a balance; nil if none.
    static func avg7(_ days: [DayLog], _ today: LocalDate) -> Int? {
        avgBalance(index(days), today.minusDays(1), 7).map(roundToInt)
    }

    /// Mean balance of the [n] calendar days ending at [end] (inclusive) that have one.
    private static func avgBalance(_ byDate: [LocalDate: DayLog], _ end: LocalDate, _ n: Int) -> Double? {
        let values = (0..<n).compactMap { byDate[end.minusDays($0)]?.balance }
        return values.isEmpty ? nil : values.average
    }

    /// A small win every 1 kg below the start weight, measured on trend.
    static func winsDone(_ p: Profile, _ trendKg: Double) -> Int {
        Int((p.startKg - trendKg + eps).rounded(.down)).clamped(0, winsTotal(p))
    }

    static func winsTotal(_ p: Profile) -> Int { max(0, Int((p.startKg - p.goalKg + eps).rounded(.down))) }

    /// kg left to the next 1 kg win (e.g. 0.6), rounded to 0.1 and at least 0.1. 0 once every win is done.
    static func toNextWin(_ p: Profile, _ trendKg: Double) -> Double {
        let done = winsDone(p, trendKg)
        if done >= winsTotal(p) { return 0 }
        let left = Double(done + 1) - (p.startKg - trendKg)
        return max(Format.round1(left), 0.1)
    }

    /// kg -> comparison, heaviest last.
    static let tangibles: [(Double, String)] = [
        (0.25, "a block of butter"),
        (0.5, "a tub of butter"),
        (1.0, "a bag of sugar"),
        (2.0, "two big water bottles"),
        (3.0, "a house cat"),
        (3.5, "a newborn baby"),
        (5.0, "a bowling ball"),
        (7.0, "a big watermelon"),
        (10.0, "a car tyre"),
        (15.0, "a full suitcase"),
        (20.0, "a big bag of dog food"),
        (25.0, "a sack of potatoes"),
        (30.0, "a golden retriever"),
        (35.0, "a sack of potatoes and a car tyre"),
        (45.0, "two sacks of potatoes"),
    ]

    /// A physical comparison for [kgLost], e.g. 2.0 -> "two big water bottles". Empty below 0.25 kg.
    static func tangible(_ kgLost: Double) -> String {
        tangibles.last { $0.0 <= kgLost + eps }?.1 ?? ""
    }

    /// The calm line for a day that ate more than it burned.
    static func spillLine(_ overBy: Int, _ avg7: Int?) -> String {
        let first = "One day of eating more than you burned doesn't move the trend much."
        guard let avg7 else { return "\(first) Tomorrow is a fresh start." }
        if avg7 > 0 { return "\(first) Over the last 7 days you still burned \(Format.int(avg7)) kcal a day more than you ate." }
        if avg7 == 0 { return "\(first) Your last 7 days are right at even, so one lighter day tips it back." }
        return "\(first) A couple of lighter days will bring your last 7 days back to burning more than you eat."
    }

    /// Morning notification after the weigh-in syncs: (title, body). [today] only decides whether dates show a year.
    static func morningNudge(_ trendChangeKg: Double, _ trendKg: Double, _ next: Milestone?, _ shift: Int?, _ today: LocalDate) -> (String, String) {
        let change = Format.round1(trendChangeKg)
        let title: String
        if change < 0 { title = "Down \(Format.kg(-change)) kg on trend" }
        else if change > 0 { title = "Up \(Format.kg(change)) kg on trend" }
        else { title = "Steady on trend" }
        var sb = "\(Format.kg(trendKg)) kg."
        if change > 0 { sb += " Normal day-to-day swing." }
        if let next {
            if let projected = next.projected {
                if let shift, shift > 0 {
                    sb += " \(Format.kg(next.kg)) kg moved \(Format.days(shift)) earlier, to \(Format.date(projected, today))."
                } else if let shift, shift < 0 {
                    sb += " \(Format.kg(next.kg)) kg is now set for \(Format.date(projected, today))."
                } else {
                    sb += " \(Format.kg(next.kg)) kg is still on track for \(Format.date(projected, today))."
                }
            } else {
                sb += " Keep logging and the date for \(Format.kg(next.kg)) kg will come back."
            }
        } else {
            sb += " You're at your goal. Every milestone is done."
        }
        return (title, sb)
    }

    /// Evening (8 PM) notification: (title, body). balance = so far today (positive = burned more).
    /// Snack room = balance - eveningKeepKcal, rounded down to 50 kcal, shown when at least 100.
    static func eveningNudge(_ balance: Int?, _ streak: Int) -> (String, String) {
        guard let balance else {
            return ("Log today's food?", "Nothing in MyFitnessPal yet today. Log when you can and Pulse will show your balance.")
        }
        if balance > 0 {
            let title = streak >= 2 ? "Burned \(Format.int(balance)) kcal more than eaten, day \(streak) of your streak"
                : "Burned \(Format.int(balance)) kcal more than eaten so far"
            let room = (balance - eveningKeepKcal) / 50 * 50
            let body = room >= 100 ? "You have room for a \(Format.int(room)) kcal snack and still keep a solid deficit."
                : "You've burned more than you've eaten today. A calm evening keeps it that way."
            return (title, body)
        }
        let title = balance == 0 ? "Right at even so far" : "Eaten \(Format.int(-balance)) kcal more than burned so far"
        let body = streak >= 1 ? "One day doesn't move the trend much, and your streak is still alive. Tomorrow is a fresh start."
            : "One day doesn't move the trend much. Tomorrow is a fresh start."
        return (title, body)
    }

    /// One sentence for Journey, e.g. "A strong week moved 115 kg 3 days earlier, to 17 Dec." Nil if nothing to say.
    static func journeyLine(_ next: Milestone?, _ shift: Int?, _ today: LocalDate) -> String? {
        guard let next, let shift, shift != 0, let projected = next.projected else { return nil }
        let what = next.isGoal ? "your \(Format.kg(next.kg)) kg goal" : "\(Format.kg(next.kg)) kg"
        let date = Format.date(projected, today)
        if shift >= 3 { return "A strong week moved \(what) \(Format.days(shift)) earlier, to \(date)." }
        if shift > 0 { return "Recent days moved \(what) \(Format.days(shift)) earlier, to \(date)." }
        return Format.capitalized("\(what) is now set for \(date), \(Format.days(-shift)) later. A steady week pulls it back.")
    }

    /// The Monday recap of last week (Monday..Sunday), or nil if [today] isn't a Monday or Tuesday,
    /// last week ended before the profile's start week, or there's no data for last week.
    static func weeklyRecap(_ p: Profile, _ days: [DayLog], _ trend: [TrendPoint], _ today: LocalDate) -> WeeklyRecap? {
        if today.dayOfWeek != 1 && today.dayOfWeek != 2 { return nil }
        let weekStart = today.mondayOnOrBefore.minusWeeks(1)
        let weekEnd = weekStart.plusDays(6)
        let firstMonday = p.startDate.mondayOnOrBefore
        let weekNumber = LocalDate.daysBetween(firstMonday, weekStart) / 7 + 1
        if weekStart.isBefore(firstMonday) { return nil }

        let byDate = index(days)
        let week = (0...6).compactMap { byDate[weekStart.plusDays($0)] }
        let hasData = week.contains { $0.steps != nil || $0.eaten != nil || $0.burnedTotal != nil || $0.weightKg != nil }
        if !hasData { return nil }

        let sortedTrend = trend.sorted { $0.date < $1.date }
        let change = weekChange(sortedTrend, weekStart)
        let logged = week.filter { $0.eaten != nil }
        let withBalance = week.filter { $0.balance != nil }
        let daysUnder = withBalance.filter { $0.balance! > 0 }.count
        let best = withBalance.filter { $0.balance! > 0 }.max { $0.balance! < $1.balance! }
        let totalSteps = week.reduce(0) { $0 + ($1.steps ?? 0) }

        return WeeklyRecap(
            weekNumber: weekNumber,
            weekStart: weekStart,
            trendChangeKg: Format.round1(change ?? 0),
            daysUnder: daysUnder,
            daysLogged: logged.count,
            totalSteps: totalSteps,
            bestDay: best?.date,
            bestDayBalance: best?.balance,
            focusDaysUnder: min(7, daysUnder + 1),
            focusTip: focusTip(p, byDate, week, weekEnd, firstMonday),
            headline: headline(sortedTrend, change, weekNumber, firstMonday)
        )
    }

    /// Trend at the end of the week minus trend just before it (or the week's first trend point).
    private static func weekChange(_ trend: [TrendPoint], _ weekStart: LocalDate) -> Double? {
        let weekEnd = weekStart.plusDays(6)
        guard let end = Forecaster.trendOn(trend, weekEnd) else { return nil }
        guard let before = Forecaster.trendOn(trend, weekStart.minusDays(1))
            ?? trend.first(where: { !$0.date.isBefore(weekStart) && !$0.date.isAfter(weekEnd) })?.trend else { return nil }
        return end - before
    }

    private static func headline(_ trend: [TrendPoint], _ change: Double?, _ weekNumber: Int, _ firstMonday: LocalDate) -> String {
        let c = change ?? 0
        if c > -0.05 && c < 0.05 { return "A steady week. The trend held, and that's fine." }
        if c >= 0.05 { return "The trend rose \(Format.kg(c)) kg. One week is noise, and this one is a fresh start." }
        let earlier = weekNumber > 1 ? (1..<weekNumber).map { weekChange(trend, firstMonday.plusWeeks($0 - 1)) } : []
        var run = 1
        for w in earlier.reversed() {
            if let w, w <= -0.05 { run += 1 } else { break }
        }
        let best = earlier.contains { $0 != nil && $0! <= -0.05 } && earlier.allSatisfy { $0 == nil || c < $0! - eps }
        if best && run >= 2 { return "Your best week yet, and \(Format.word(run)) weeks in a row of losing." }
        if best { return "Your best week yet: down \(Format.kg(-c)) kg on trend." }
        if run >= 2 { return "\(Format.capitalized(Format.word(run))) weeks in a row of losing. Steady wins this." }
        return "Down \(Format.kg(-c)) kg on trend. A good week."
    }

    private static func focusTip(_ p: Profile, _ byDate: [LocalDate: DayLog], _ week: [DayLog], _ weekEnd: LocalDate, _ firstMonday: LocalDate) -> String {
        let overDays = (0..<28).map { weekEnd.minusDays($0) }
            .filter { !$0.isBefore(firstMonday) }
            .compactMap { byDate[$0] }
            .filter { ($0.balance ?? 0) < 0 }
        let groups = Dictionary(grouping: overDays) { $0.date.dayOfWeek }
        let worst = groups.max { a, b in
            if a.value.count != b.value.count { return a.value.count < b.value.count }
            return a.value.map(\.date).max()! < b.value.map(\.date).max()!
        }
        if let worst, worst.value.count >= 2 {
            let day = Weekday.full[worst.key - 1]
            let before = Weekday.full[(worst.key + 5) % 7]
            let times = worst.value.count == 2 ? "twice" : "\(Format.word(worst.value.count)) times"
            let meals = worst.value.map(\.meals)
            let meal = [
                ("breakfast", meals.reduce(0) { $0 + $1.breakfast }),
                ("lunch", meals.reduce(0) { $0 + $1.lunch }),
                ("dinner", meals.reduce(0) { $0 + $1.dinner }),
                ("snacks", meals.reduce(0) { $0 + $1.snacks }),
            ].filter { $0.1 > 0 }.max { $0.1 < $1.1 }?.0 ?? "dinner"
            return "\(day)s were the days you ate more than you burned \(times). Plan \(day) \(meal) by \(before) night."
        }
        let steps = week.compactMap(\.steps)
        if !steps.isEmpty && steps.average < Double(p.stepGoal) {
            return "Your steps averaged \(Format.int(roundToInt(steps.average))) a day. " +
                "A 15-minute walk after dinner closes a good part of the gap to \(Format.int(p.stepGoal))."
        }
        return "Put protein in every meal this week. It keeps you full on fewer calories."
    }

    /// Ring style unlocked after [milestonesReached] milestones (0 = mint); cycles after the last style.
    static func ringStyle(_ milestonesReached: Int) -> RingStyle {
        let all = RingStyle.allCases
        return milestonesReached <= 0 ? all[0] : all[milestonesReached % all.count]
    }

    /// Celebration copy for a reached milestone.
    static func celebrationLine(_ p: Profile, _ m: Milestone, _ daysTaken: Int) -> String {
        let lost = p.startKg - m.kg
        var sb = ""
        if m.isGoal { sb += "You reached your goal. " }
        sb += "\(Format.kg(lost)) kg gone in \(Format.days(max(0, daysTaken)))."
        let thing = tangible(lost)
        if !thing.isEmpty { sb += " That's \(thing) you no longer carry up every flight of stairs." }
        return sb
    }
}
