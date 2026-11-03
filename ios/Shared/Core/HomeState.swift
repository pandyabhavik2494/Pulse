import Foundation

/// Everything the screens, widgets and the watch draw, built in one place from the profile and stored days.
/// Never computed inside view code.
struct HomeState: Sendable {
    let now: Date
    let today: LocalDate
    let time: LocalTime
    let profile: Profile
    let lastSync: Date?

    // Today
    let day: DayLog
    let steps: Int
    let burned: Int?
    let active: Int?
    let eaten: Int?
    let balance: Int?
    let budget: Budget.Day

    // Weight and journey
    let trend: [TrendPoint]
    let trendKg: Double?
    let forecast: Forecast
    let shiftDays: Int?
    let journeyLine: String?

    // Motivation
    let streak: Int
    let avg7: Int?
    let recap: WeeklyRecap?

    /// Newest first, up to 90 days, today included.
    let history: [DayLog]

    var eatenMore: Bool { (balance ?? 0) < 0 }

    /// Builds the state from stored days. A day with active energy but no resting energy gets the resting
    /// part estimated from the body (so far today, a full day before that).
    static func build(profile p: Profile, days stored: [DayLog], now: Date = Date(), lastSync: Date? = nil, calendar: Calendar = .current) -> HomeState {
        let today = LocalDate(now, calendar: calendar)
        let time = LocalTime(now, calendar: calendar)
        let seconds = Double(time.secondOfDay)

        var byDate: [LocalDate: DayLog] = [:]
        for d in stored where !d.date.isAfter(today) { byDate[d.date] = d }
        let firstTrend = Trend.of(Array(byDate.values), from: p.startDate, to: today).last?.trend
        let weightForEstimate = firstTrend ?? p.startKg
        for (k, var d) in byDate where d.burnedTotal == nil {
            guard let a = d.burnedActive else { continue }
            let resting = k == today ? Energy.restingSoFar(p, weightForEstimate, secondOfDay: seconds) : Energy.bmr(p, weightForEstimate)
            d.burnedTotal = a + resting
            byDate[k] = d
        }
        let days = byDate.values.sorted { $0.date < $1.date }
        let todayLog = byDate[today] ?? DayLog(date: today)

        let trend = Trend.of(days, from: min(p.startDate, today), to: today)
        let trendKg = trend.last?.trend
        let forecast = Forecaster.forecast(p, days, trend, today)
        let yesterday = Forecaster.forecast(p, days.filter { $0.date.isBefore(today) }, trend.filter { $0.date.isBefore(today) }, today.minusDays(1))
        let shift = Forecaster.shiftDays(forecast, yesterday)

        return HomeState(
            now: now, today: today, time: time, profile: p, lastSync: lastSync,
            day: todayLog,
            steps: todayLog.steps ?? 0,
            burned: todayLog.burnedTotal,
            active: todayLog.burnedActive,
            eaten: todayLog.eaten,
            balance: todayLog.balance,
            budget: Budget.today(p, days, today, trendKg),
            trend: trend,
            trendKg: trendKg,
            forecast: forecast,
            shiftDays: shift,
            journeyLine: Motivation.journeyLine(forecast.next, shift, today),
            streak: Motivation.streak(days, today),
            avg7: Motivation.avg7(days, today),
            recap: Motivation.weeklyRecap(p, days, trend, today),
            history: Array(days.reversed().prefix(90))
        )
    }

    /// The newest value of [pick] in the last week (today first), with its date.
    func latest<T>(_ pick: (DayLog) -> T?) -> (T, LocalDate)? {
        for d in history.prefix(7) { if let v = pick(d) { return (v, d.date) } }
        return nil
    }

    /// Today's statuses for the glance (watch, widgets): balance, left to eat, steps, burned, eaten.
    var glanceStatuses: [Status] {
        [Moods.balance(balance), Moods.left(budget.left, budget.budget), Moods.steps(steps, profile.stepGoal, time),
         Moods.burned(burned, budget.burn, time), Moods.eaten(eaten, budget.budget)]
    }
}

/// The verdict on top of Today: a kind headline, one win, one small next step.
struct Verdict: Sendable {
    let headline: String
    let line: String
    let moods: [Mood]
    let anyGrow: Bool

    init(_ shown: [Status], _ t: LocalTime) {
        moods = shown.map(\.mood)
        let judged = moods.filter { $0 != .wait }
        headline = judged.isEmpty ? "Your day is filling in" : Moods.headline(judged, t)
        anyGrow = moods.contains(.grow)
        let win = shown.first { $0.mood == .good && $0.win != nil }?.win
        let tip = shown.first { $0.mood == .grow && $0.tip != nil }?.tip ?? shown.first { $0.mood == .steady && $0.tip != nil }?.tip
        let parts = [win, tip.map { "One small thing: " + $0.prefix(1).lowercased() + $0.dropFirst() }].compactMap { $0 }
        line = parts.isEmpty ? "Your numbers fill in as the day goes." : parts.joined(separator: " ")
    }

    func count(_ m: Mood) -> Int { moods.filter { $0 == m }.count }
}
