import XCTest
@testable import Pulse

final class MoodTests: XCTestCase {
    private let seven = LocalTime(6, 58)
    private let noon = LocalTime(12, 0)
    private let eight = LocalTime(20, 5)

    func testEarlyStepsAreNeverJudged() {
        let s = Moods.steps(60, 10_000, seven)
        XCTAssertEqual(.wait, s.mood)
        XCTAssertEqual("Early", s.word)
    }

    func testStepsFollowThePaceOfTheDay() {
        XCTAssertEqual(.good, Moods.steps(3_200, 10_000, noon).mood)
        XCTAssertEqual(.steady, Moods.steps(2_400, 10_000, noon).mood)
        XCTAssertEqual(.grow, Moods.steps(900, 10_000, noon).mood)
        XCTAssertEqual(.good, Moods.steps(10_500, 10_000, seven).mood)
        XCTAssertEqual(.steady, Moods.steps(6_240, 10_000, eight).mood)
    }

    func testBurnedAgainstTheUsualByNow() {
        XCTAssertEqual(.good, Moods.burned(634, 2_450, seven).mood)
        XCTAssertEqual(.wait, Moods.burned(nil, 2_450, seven).mood)
        XCTAssertEqual(.wait, Moods.burned(100, 2_450, LocalTime(1, 0)).mood)
        XCTAssertEqual(.grow, Moods.burned(300, 2_450, noon).mood)
    }

    func testFoodStates() {
        XCTAssertEqual(.wait, Moods.eaten(nil, 1_600).mood)
        XCTAssertEqual(.good, Moods.eaten(1_500, 1_600).mood)
        XCTAssertEqual(.steady, Moods.eaten(1_750, 1_600).mood)
        let over = Moods.eaten(2_610, 2_310)
        XCTAssertEqual(.grow, over.mood)
        XCTAssertTrue(over.why.contains("300 above"))
        XCTAssertEqual(.good, Moods.balance(520).mood)
        XCTAssertEqual(.steady, Moods.balance(-150).mood)
        XCTAssertEqual(.grow, Moods.balance(-500).mood)
        XCTAssertEqual(.good, Moods.left(1_599, 1_599).mood)
        XCTAssertEqual(.steady, Moods.left(120, 1_599).mood)
        XCTAssertEqual(.grow, Moods.left(-170, 1_599).mood)
    }

    func testWeightIsNeverWorseThanSteady() {
        XCTAssertEqual(.good, Moods.weight(119.8, 120.1)!.mood)
        XCTAssertEqual(.steady, Moods.weight(120.2, 120.1)!.mood)
        XCTAssertEqual(.steady, Moods.weight(122.5, 120.1)!.mood)
        XCTAssertNil(Moods.weight(nil, 120.0))
    }

    func testTrendNeedsAWeek() {
        let today = LocalDate(2026, 11, 2)
        XCTAssertEqual(.wait, Moods.trend([TrendPoint(date: today, raw: 120.2, trend: 120.1)], today).mood)
        let down = (0...10).map { TrendPoint(date: today.minusDays(10 - $0), raw: 120.0 - Double($0) * 0.1, trend: 120.0 - Double($0) * 0.08) }
        XCTAssertEqual(.good, Moods.trend(down, today).mood)
        let up = (0...10).map { TrendPoint(date: today.minusDays(10 - $0), raw: 118.0 + Double($0) * 0.1, trend: 118.0 + Double($0) * 0.08) }
        XCTAssertEqual(.grow, Moods.trend(up, today).mood)
    }

    func testRestingAgainstOwnUsual() {
        XCTAssertEqual(.steady, Moods.resting(84, [])!.mood)
        let usual = Array(repeating: 80, count: 10)
        XCTAssertEqual(.good, Moods.resting(79, usual)!.mood)
        XCTAssertEqual(.steady, Moods.resting(83, usual)!.mood)
        XCTAssertEqual(.grow, Moods.resting(88, usual)!.mood)
    }

    func testSleepAndDeep() {
        XCTAssertEqual(.good, Moods.sleep(425)!.mood)
        XCTAssertEqual("You slept 7h 5m, nice.", Moods.sleep(425)!.win)
        XCTAssertEqual(.steady, Moods.sleep(390)!.mood)
        XCTAssertEqual(.grow, Moods.sleep(300)!.mood)
        XCTAssertEqual(.steady, Moods.deep(56)!.mood)
        XCTAssertEqual(.good, Moods.deep(70)!.mood)
        XCTAssertEqual(.grow, Moods.deep(30)!.mood)
    }

    func testHeadlinesStayKind() {
        XCTAssertEqual("Good start", Moods.headline([.good, .good, .steady, .wait], seven))
        XCTAssertEqual("Going well", Moods.headline([.good], eight))
        XCTAssertEqual("Close to plan", Moods.headline([.grow, .grow, .good], eight))
        let words = ["Good start", "Going well", "Steady day", "Mostly on track", "Close to plan"]
        XCTAssertTrue(words.allSatisfy { !$0.lowercased().contains("over") && !$0.lowercased().contains("bad") })
        XCTAssertFalse(Mood.allCases.contains { $0.rawValue == "bad" })
    }

    /// Every word a status can show stays kind: no "over", "under", "warning", "bad" or "needs work".
    func testNoScaryWordsAnywhere() {
        var all: [Status] = []
        for t in [seven, noon, eight] {
            for s in [0, 900, 3_000, 12_000] { all.append(Moods.steps(s, 10_000, t)) }
            for b in [nil, 100, 900, 3_000] as [Int?] { all.append(Moods.burned(b, 2_450, t)) }
        }
        for e in [nil, 1_000, 2_400, 4_000] as [Int?] { all.append(Moods.eaten(e, 2_300)) }
        for b in [nil, -900, -100, 400] as [Int?] { all.append(Moods.balance(b)) }
        for l in [-500, 100, 900] { all.append(Moods.left(l, 1_600)) }
        all += [Moods.weight(120, 119), Moods.resting(95, []), Moods.sleep(200), Moods.deep(10)].compactMap { $0 }
        for s in all {
            let text = [s.word, s.why, s.win ?? "", s.tip ?? ""].joined(separator: " ").lowercased()
            for bad in ["over budget", "needs work", "warning", "bad", " under", "over "] {
                XCTAssertFalse(text.contains(bad), "'\(bad)' in: \(text)")
            }
        }
    }
}

final class MotivationTests: XCTestCase {
    private let bhavik = Fixtures.bhavik
    private let start = Fixtures.start
    private let today = LocalDate(2026, 11, 20)
    private func ago(_ n: Int) -> LocalDate { today.minusDays(n) }
    private func day(_ d: LocalDate, _ b: Int?) -> DayLog { Fixtures.day(d, b) }

    // ---- streak ----

    func testStreakEmpty() { XCTAssertEqual(0, Motivation.streak([], today)) }

    func testStreakCountsUnderDaysEndingYesterday() {
        let days = (1...5).map { day(ago($0), 500) } + [day(ago(6), -800)]
        XCTAssertEqual(5, Motivation.streak(days, today))
    }

    func testStreakIncludesTodayWhenAlreadyUnder() {
        XCTAssertEqual(6, Motivation.streak((1...5).map { day(ago($0), 500) } + [day(today, 300)], today))
    }

    func testOverTodayDoesNotBreakIt() {
        XCTAssertEqual(5, Motivation.streak((1...5).map { day(ago($0), 500) } + [day(today, -900)], today))
    }

    func testYesterdayOverAndNotForgivenIsZero() {
        XCTAssertEqual(0, Motivation.streak([day(ago(1), -500), day(ago(2), 300)], today))
    }

    func testNoFoodDaysAreSkipped() {
        let days = [day(ago(1), 500), day(ago(2), nil), day(ago(3), 400), day(ago(5), 600), day(ago(6), -900)]
        XCTAssertEqual(3, Motivation.streak(days, today))
    }

    func testOverDayForgivenWhenSevenDayAverageStillUnder() {
        let days = [
            day(ago(1), 600), day(ago(2), 500), day(ago(3), -300),
            day(ago(4), 700), day(ago(5), 800), day(ago(6), 600), day(ago(7), 900),
            day(ago(11), -2000), day(ago(12), -2000),
        ]
        XCTAssertEqual(6, Motivation.streak(days, today))
    }

    func testOverDayNotForgivenWhenAverageIsOver() {
        XCTAssertEqual(1, Motivation.streak([day(ago(1), 600), day(ago(2), -3000), day(ago(3), 500), day(ago(4), 500)], today))
    }

    func testEvenDayCountsAsOverButCanBeForgiven() {
        XCTAssertEqual(2, Motivation.streak([day(ago(1), 600), day(ago(2), 0), day(ago(3), 500)], today))
        XCTAssertEqual(1, Motivation.streak([day(ago(1), 600), day(ago(2), 0)], today))
    }

    func testStreakForBhavikFixture() {
        let days = Fixtures.bhavikDays(24)
        XCTAssertEqual(24, Motivation.streak(days, start.plusDays(24)))
        XCTAssertEqual(20, Motivation.streak(days, start.plusDays(19)))
    }

    // ---- avg7 ----

    func testAvg7Nil() {
        XCTAssertNil(Motivation.avg7([], today))
        XCTAssertNil(Motivation.avg7([day(today, 500), day(ago(8), 500), day(ago(1), nil)], today))
    }

    func testAvg7UsesSevenDaysBeforeToday() {
        let days = [day(today, -5000), day(ago(1), 400), day(ago(2), 420), day(ago(4), 410), day(ago(7), 410), day(ago(8), -9000)]
        XCTAssertEqual(410, Motivation.avg7(days, today))
    }

    func testAvg7Fixture() { XCTAssertEqual(825, Motivation.avg7(Fixtures.bhavikDays(24), start.plusDays(24))) }

    // ---- wins ----

    func testWins() {
        XCTAssertEqual(35, Motivation.winsTotal(bhavik))
        XCTAssertEqual(0, Motivation.winsDone(bhavik, 120.0))
        XCTAssertEqual(0, Motivation.winsDone(bhavik, 121.5))
        XCTAssertEqual(2, Motivation.winsDone(bhavik, 117.6))
        XCTAssertEqual(3, Motivation.winsDone(bhavik, 117.0))
        XCTAssertEqual(35, Motivation.winsDone(bhavik, 84.0))
        var p = bhavik; p.goalKg = 85.5
        XCTAssertEqual(34, Motivation.winsTotal(p))
        p.goalKg = 125.0
        XCTAssertEqual(0, Motivation.winsTotal(p))
    }

    func testToNextWin() {
        XCTAssertEqual(0.6, Motivation.toNextWin(bhavik, 117.6), accuracy: 1e-9)
        XCTAssertEqual(1.0, Motivation.toNextWin(bhavik, 120.0), accuracy: 1e-9)
        XCTAssertEqual(1.0, Motivation.toNextWin(bhavik, 117.0), accuracy: 1e-9)
        XCTAssertEqual(0.1, Motivation.toNextWin(bhavik, 117.01), accuracy: 1e-9)
        XCTAssertEqual(2.5, Motivation.toNextWin(bhavik, 121.5), accuracy: 1e-9)
        XCTAssertEqual(0.0, Motivation.toNextWin(bhavik, 85.0), accuracy: 1e-9)
        XCTAssertEqual(0.0, Motivation.toNextWin(bhavik, 80.0), accuracy: 1e-9)
    }

    // ---- tangible ----

    func testTangibleTable() {
        XCTAssertEqual("a tub of butter", Motivation.tangible(0.5))
        XCTAssertEqual("a tub of butter", Motivation.tangible(0.9))
        XCTAssertEqual("a bag of sugar", Motivation.tangible(1.0))
        XCTAssertEqual("two big water bottles", Motivation.tangible(2.0))
        XCTAssertEqual("two big water bottles", Motivation.tangible(2.9))
        XCTAssertEqual("a house cat", Motivation.tangible(3.0))
        XCTAssertEqual("a bowling ball", Motivation.tangible(5.0))
        XCTAssertEqual("a car tyre", Motivation.tangible(10.0))
        XCTAssertEqual("a full suitcase", Motivation.tangible(15.0))
        XCTAssertEqual("a sack of potatoes and a car tyre", Motivation.tangible(35.0))
        XCTAssertEqual("two sacks of potatoes", Motivation.tangible(120.0))
    }

    func testTangibleNeverEmptyFromHalfAKilo() {
        var kg = 0.5
        while kg < 60 { XCTAssertFalse(Motivation.tangible(kg).isEmpty, "\(kg)"); kg += 0.1 }
        XCTAssertEqual("", Motivation.tangible(0.1))
        XCTAssertEqual("", Motivation.tangible(0.0))
    }

    func testTangibleHandlesFloatingNoise() {
        XCTAssertEqual("a bag of sugar", Motivation.tangible(120.0 - 119.0))
        XCTAssertEqual("two big water bottles", Motivation.tangible(0.1 * 20))
    }

    // ---- spill ----

    func testSpillLine() {
        XCTAssertEqual(
            "One day of eating more than you burned doesn't move the trend much. Over the last 7 days you still burned 410 kcal a day more than you ate.",
            Motivation.spillLine(300, 410))
        XCTAssertEqual(
            "One day of eating more than you burned doesn't move the trend much. Over the last 7 days you still burned 1,140 kcal a day more than you ate.",
            Motivation.spillLine(300, 1140))
        XCTAssertEqual("One day of eating more than you burned doesn't move the trend much. Tomorrow is a fresh start.", Motivation.spillLine(300, nil))
        XCTAssertTrue(Motivation.spillLine(300, -120).contains("back to burning more"))
        XCTAssertTrue(Motivation.spillLine(300, 0).contains("even"))
        for a in [nil, -500, 0, 500] as [Int?] { assertNoGuilt(Motivation.spillLine(800, a)) }
    }

    // ---- nudges ----

    private let m115 = Milestone(kg: 115.0, label: "First 5 kg", reachedOn: nil, projected: LocalDate(2026, 12, 17))

    func testMorningNudgeOnTrack() {
        var n = Motivation.morningNudge(-0.2, 117.6, m115, 0, today)
        XCTAssertEqual("Down 0.2 kg on trend", n.0)
        XCTAssertEqual("117.6 kg. 115 kg is still on track for 17 Dec.", n.1)
        n = Motivation.morningNudge(-0.23, 117.64, m115, nil, today)
        XCTAssertEqual("Down 0.2 kg on trend", n.0)
        XCTAssertEqual("117.6 kg. 115 kg is still on track for 17 Dec.", n.1)
    }

    func testMorningNudgeShifted() {
        XCTAssertEqual("117.6 kg. 115 kg moved 3 days earlier, to 17 Dec.", Motivation.morningNudge(-0.2, 117.6, m115, 3, today).1)
        XCTAssertEqual("117.6 kg. 115 kg moved 1 day earlier, to 17 Dec.", Motivation.morningNudge(-0.2, 117.6, m115, 1, today).1)
        XCTAssertEqual("117.6 kg. 115 kg is now set for 17 Dec.", Motivation.morningNudge(-0.1, 117.6, m115, -2, today).1)
    }

    func testMorningNudgeUpAndSteady() {
        let up = Motivation.morningNudge(0.1, 117.7, m115, -1, today)
        XCTAssertEqual("Up 0.1 kg on trend", up.0)
        XCTAssertTrue(up.1.hasPrefix("117.7 kg. Normal day-to-day swing."))
        assertNoGuilt(up.0 + up.1)
        XCTAssertEqual("Steady on trend", Motivation.morningNudge(0.02, 117.6, m115, 0, today).0)
        XCTAssertEqual("Steady on trend", Motivation.morningNudge(-0.04, 117.6, m115, 0, today).0)
    }

    func testMorningNudgeNoDateOrNoNext() {
        var stalled = m115; stalled.projected = nil
        XCTAssertEqual("117.6 kg. Keep logging and the date for 115 kg will come back.", Motivation.morningNudge(0.0, 117.6, stalled, nil, today).1)
        XCTAssertTrue(Motivation.morningNudge(-0.1, 84.9, nil, nil, today).1.contains("goal"))
    }

    func testMorningNudgeYearWhenNotThisYear() {
        let m = Milestone(kg: 110.0, label: "10 kg down", reachedOn: nil, projected: LocalDate(2027, 1, 31))
        XCTAssertEqual("112.4 kg. 110 kg is still on track for 31 Jan 2027.", Motivation.morningNudge(-0.1, 112.4, m, 0, today).1)
        XCTAssertEqual("112.4 kg. 110 kg is still on track for 31 Jan.", Motivation.morningNudge(-0.1, 112.4, m, 0, LocalDate(2027, 1, 2)).1)
    }

    func testEveningNudgeUnder() {
        let n = Motivation.eveningNudge(520, 5)
        XCTAssertEqual("Burned 520 kcal more than eaten, day 5 of your streak", n.0)
        XCTAssertEqual("You have room for a 300 kcal snack and still keep a solid deficit.", n.1)
        XCTAssertEqual("Burned 1,140 kcal more than eaten so far", Motivation.eveningNudge(1140, 1).0)
        XCTAssertEqual("You have room for a 900 kcal snack and still keep a solid deficit.", Motivation.eveningNudge(1140, 1).1)
        XCTAssertEqual("You've burned more than you've eaten today. A calm evening keeps it that way.", Motivation.eveningNudge(250, 0).1)
    }

    func testEveningNudgeOverAndEvenAreKind() {
        let over = Motivation.eveningNudge(-300, 4)
        XCTAssertEqual("Eaten 300 kcal more than burned so far", over.0)
        XCTAssertTrue(over.1.contains("streak is still alive"))
        XCTAssertEqual("Right at even so far", Motivation.eveningNudge(0, 0).0)
        XCTAssertEqual("Eaten 1,250 kcal more than burned so far", Motivation.eveningNudge(-1250, 0).0)
        for b in [-1250, -300, 0] { for s in [0, 4] { let n = Motivation.eveningNudge(b, s); assertNoGuilt(n.0 + n.1) } }
    }

    func testEveningNudgeNoFood() {
        let n = Motivation.eveningNudge(nil, 3)
        XCTAssertEqual("Log today's food?", n.0)
        XCTAssertTrue(n.1.contains("MyFitnessPal"))
        assertNoGuilt(n.0 + n.1)
    }

    // ---- journey line ----

    func testJourneyLine() {
        XCTAssertEqual("A strong week moved 115 kg 3 days earlier, to 17 Dec.", Motivation.journeyLine(m115, 3, today))
        XCTAssertEqual("Recent days moved 115 kg 1 day earlier, to 17 Dec.", Motivation.journeyLine(m115, 1, today))
        XCTAssertEqual("115 kg is now set for 17 Dec, 2 days later. A steady week pulls it back.", Motivation.journeyLine(m115, -2, today))
        let goal = Milestone(kg: 85.0, label: "Goal", reachedOn: nil, projected: LocalDate(2027, 9, 24), isGoal: true)
        XCTAssertEqual("A strong week moved your 85 kg goal 5 days earlier, to 24 Sep 2027.", Motivation.journeyLine(goal, 5, today))
        XCTAssertEqual("Your 85 kg goal is now set for 24 Sep 2027, 1 day later. A steady week pulls it back.", Motivation.journeyLine(goal, -1, today))
    }

    func testJourneyLineNothingToSay() {
        XCTAssertNil(Motivation.journeyLine(nil, 3, today))
        XCTAssertNil(Motivation.journeyLine(m115, nil, today))
        XCTAssertNil(Motivation.journeyLine(m115, 0, today))
        var noDate = m115; noDate.projected = nil
        XCTAssertNil(Motivation.journeyLine(noDate, 3, today))
    }

    // ---- weekly recap ----

    private let monday23 = LocalDate(2026, 11, 23)

    private func recap(_ days: [DayLog], _ on: LocalDate) -> WeeklyRecap? {
        Motivation.weeklyRecap(bhavik, days, Trend.of(days, from: start, to: on), on)
    }

    func testRecapOnlyOnMondayAndTuesday() {
        let days = Fixtures.bhavikDays(30)
        XCTAssertEqual(1, monday23.dayOfWeek)
        XCTAssertNotNil(recap(days, monday23))
        XCTAssertNotNil(recap(days, monday23.plusDays(1)))
        for i in 2...6 { XCTAssertNil(recap(days, monday23.plusDays(i))) }
        XCTAssertEqual(recap(days, monday23), recap(days, monday23.plusDays(1)))
    }

    func testRecapForBhavikWeek() {
        let days = Fixtures.bhavikDays(24)
        let r = recap(days, monday23)!
        // 1 Nov 2026 is a Sunday, so week 1 is 26 Oct - 1 Nov and 16-22 Nov is week 4.
        XCTAssertEqual(4, r.weekNumber)
        XCTAssertEqual(LocalDate(2026, 11, 16), r.weekStart)
        XCTAssertEqual(7, r.daysUnder)
        XCTAssertEqual(7, r.daysLogged)
        XCTAssertEqual(7, r.focusDaysUnder)
        let week = days.filter { !$0.date.isBefore(r.weekStart) && !$0.date.isAfter(r.weekStart.plusDays(6)) }
        XCTAssertEqual(week.reduce(0) { $0 + $1.steps! }, r.totalSteps)
        let best = week.max { $0.balance! < $1.balance! }!
        XCTAssertEqual(best.date, r.bestDay)
        XCTAssertEqual(best.balance, r.bestDayBalance)
        XCTAssertLessThan(r.trendChangeKg, 0)
        let trend = Trend.of(days, from: start, to: monday23)
        let expected = trend.first { $0.date == LocalDate(2026, 11, 22) }!.trend - trend.first { $0.date == LocalDate(2026, 11, 15) }!.trend
        XCTAssertEqual(Format.round1(expected), r.trendChangeKg, accuracy: 1e-9)
        XCTAssertEqual("Your best week yet, and three weeks in a row of losing.", r.headline)
        XCTAssertTrue(r.focusTip.hasPrefix("Your steps averaged"), r.focusTip)
        XCTAssertTrue(r.focusTip.contains("10,000"))
    }

    func testRecapFirstWeek() {
        let r = recap(Fixtures.bhavikDays(1), LocalDate(2026, 11, 2))!
        XCTAssertEqual(1, r.weekNumber)
        XCTAssertEqual(LocalDate(2026, 10, 26), r.weekStart)
        XCTAssertEqual(1, r.daysLogged)
        XCTAssertEqual(1, r.daysUnder)
        XCTAssertEqual(2, r.focusDaysUnder)
        XCTAssertEqual(0.0, r.trendChangeKg, accuracy: 1e-9)
        XCTAssertEqual("A steady week. The trend held, and that's fine.", r.headline)
    }

    func testRecapNilBeforeStartOrWithoutData() {
        let days = Fixtures.bhavikDays(24)
        XCTAssertNil(recap(days, LocalDate(2026, 10, 26)))
        XCTAssertNil(recap(days, LocalDate(2026, 12, 7)))
        XCTAssertNil(recap([], monday23))
    }

    func testRecapFocusTipFromRepeatedOverWeekday() {
        let saturdays = [LocalDate(2026, 11, 7), LocalDate(2026, 11, 14), LocalDate(2026, 11, 21)]
        let days = Fixtures.bhavikDays(24).map { d -> DayLog in
            guard saturdays.contains(d.date) else { return d }
            var x = d; x.eaten = 3400; x.meals = Meals(breakfast: 400, lunch: 600, dinner: 2000, snacks: 400); return x
        }
        let r = recap(days, monday23)!
        XCTAssertEqual("Saturdays were the days you ate more than you burned three times. Plan Saturday dinner by Friday night.", r.focusTip)
        XCTAssertEqual(6, r.daysUnder)
        XCTAssertEqual(7, r.focusDaysUnder)
        let snacky = Fixtures.bhavikDays(24).map { d -> DayLog in
            guard saturdays.prefix(2).contains(d.date) else { return d }
            var x = d; x.eaten = 3400; x.meals = Meals(breakfast: 300, lunch: 500, dinner: 600, snacks: 2000); return x
        }
        XCTAssertEqual("Saturdays were the days you ate more than you burned twice. Plan Saturday snacks by Friday night.", recap(snacky, monday23)!.focusTip)
    }

    func testRecapSingleOverDayFallsBackToProteinWhenStepsAreGood() {
        let days = Fixtures.bhavikDays(24, steps: 11_000).map { d -> DayLog in
            guard d.date == LocalDate(2026, 11, 18) else { return d }
            var x = d; x.eaten = 3400; return x
        }
        let r = recap(days, monday23)!
        XCTAssertTrue(r.focusTip.contains("protein"), r.focusTip)
        XCTAssertEqual(6, r.daysUnder)
    }

    func testRecapUpWeekIsKind() {
        let days = (0..<24).map { DayLog(date: start.plusDays($0), steps: 5000, burnedTotal: 2400, eaten: 2600, weightKg: 120.0 + Double($0) * 0.1) }
        let r = recap(days, monday23)!
        XCTAssertGreaterThan(r.trendChangeKg, 0)
        XCTAssertEqual(0, r.daysUnder)
        XCTAssertNil(r.bestDay)
        XCTAssertNil(r.bestDayBalance)
        XCTAssertEqual(1, r.focusDaysUnder)
        XCTAssertTrue(r.headline.contains("fresh start"), r.headline)
        assertNoGuilt(r.headline + r.focusTip)
    }

    // ---- ring style ----

    func testRingStyleCycles() {
        XCTAssertEqual(.mint, Motivation.ringStyle(0))
        XCTAssertEqual(.aurora, Motivation.ringStyle(1))
        XCTAssertEqual(.sunrise, Motivation.ringStyle(2))
        XCTAssertEqual(.prism, Motivation.ringStyle(7))
        XCTAssertEqual(.mint, Motivation.ringStyle(8))
        XCTAssertEqual(.aurora, Motivation.ringStyle(9))
        XCTAssertEqual(.mint, Motivation.ringStyle(-3))
    }

    // ---- celebration ----

    func testCelebrationLine() {
        XCTAssertEqual(
            "2 kg gone in 18 days. That's two big water bottles you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(bhavik, Milestone(kg: 118.0, label: "2 kg", reachedOn: start.plusDays(18), projected: nil), 18))
        XCTAssertEqual(
            "5 kg gone in 1 day. That's a bowling ball you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(bhavik, Milestone(kg: 115.0, label: "First 5 kg", reachedOn: start, projected: nil), 1))
        XCTAssertEqual(
            "You reached your goal. 35 kg gone in 330 days. That's a sack of potatoes and a car tyre you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(bhavik, Milestone(kg: 85.0, label: "Goal", reachedOn: start, projected: nil, isGoal: true), 330))
        XCTAssertEqual(
            "6 kg gone in 40 days. That's a bowling ball you no longer carry up every flight of stairs.",
            Motivation.celebrationLine(bhavik, Milestone(kg: 114.0, label: "5% down", reachedOn: start, projected: nil), 40))
    }
}
