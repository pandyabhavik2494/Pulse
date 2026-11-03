import XCTest
@testable import Pulse

final class ForecasterTests: XCTestCase {
    private let bhavik = Fixtures.bhavik
    private let start = Fixtures.start

    private func profile(start s: Double? = nil, goal: Double? = nil) -> Profile {
        var p = bhavik
        if let s { p.startKg = s }
        if let goal { p.goalKg = goal }
        return p
    }

    // ---- targets ----

    func testTargetsFor120To85() {
        let expected: [(Double, String)] = [
            (115.0, "First 5 kg"), (114.0, "5% down"), (110.0, "10 kg down"), (108.0, "10% down"),
            (105.0, "15 kg down"), (102.0, "15% down"),
            (100.0, "Under 100 kg"),   // merged with "20 kg down"
            (96.0, "20% down"), (95.0, "25 kg down"),
            (90.0, "25% down"),        // merged with "30 kg down"
            (85.0, "Goal"),            // "35 kg down" is the goal itself
        ]
        let actual = Forecaster.targets(bhavik)
        XCTAssertEqual(expected.map(\.1), actual.map(\.label))
        for (e, a) in zip(expected, actual) { XCTAssertEqual(e.0, a.kg, accuracy: 1e-9) }
    }

    func testTargetsMergeWithinHalfKgAndFoldIntoGoal() {
        let t = Forecaster.targets(profile(start: 100.4, goal: 80.0))
        XCTAssertEqual(["Under 100 kg", "5% down", "10% down", "15% down", "Goal"], t.map(\.label))
        XCTAssertEqual([100.0, 95.4, 90.4, 85.3, 80.0], t.map(\.kg))
    }

    func testTargetsSortedHeaviestFirstAndGoalLast() {
        for s in [87.0, 93.3, 101.0, 120.0, 150.0] {
            let t = Forecaster.targets(profile(start: s, goal: 80.0))
            XCTAssertEqual(t.map(\.kg), t.map(\.kg).sorted(by: >))
            XCTAssertEqual("Goal", t.last!.label)
            XCTAssertEqual(80.0, t.last!.kg)
            for (a, b) in zip(t, t.dropFirst()) { XCTAssertGreaterThanOrEqual(a.kg - b.kg, 0.5 - 1e-9, "\(a) vs \(b)") }
            XCTAssertTrue(t.dropLast().allSatisfy { $0.kg < s && $0.kg > 80.0 })
        }
    }

    func testNoUnder100WhenJourneyDoesNotCross() {
        XCTAssertFalse(Forecaster.targets(profile(start: 95.0, goal: 80.0)).contains { $0.label == "Under 100 kg" })
        XCTAssertFalse(Forecaster.targets(profile(start: 130.0, goal: 100.0)).contains { $0.label == "Under 100 kg" })
    }

    func testSmallJourneyIsJustTheGoal() {
        for s in [86.0, 84.0] {
            let t = Forecaster.targets(profile(start: s))
            XCTAssertEqual(1, t.count)
            XCTAssertEqual(85.0, t[0].kg)
            XCTAssertEqual("Goal", t[0].label)
        }
    }

    // ---- forecast ----

    func testStartingGuessOnDayOne() {
        let f = Forecaster.forecast(bhavik, [], [], start)
        XCTAssertEqual(.startingGuess, f.source)
        XCTAssertEqual(0.75, f.rateKgPerWeek, accuracy: 1e-9)
        XCTAssertEqual(120.0, f.currentTrendKg, accuracy: 1e-9)
        XCTAssertNil(f.avgDeficit)
        XCTAssertNil(f.learnedBurn)
        XCTAssertEqual(11, f.milestones.count)
        // 5 kg at 0.75/7 kg a day = 46.7 -> 47 days
        XCTAssertEqual(start.plusDays(47), f.next!.projected)
        XCTAssertEqual(start.plusDays(327), f.goal.projected)
        XCTAssertTrue(f.goal.isGoal)
        XCTAssertEqual(1, f.milestones.filter(\.isGoal).count)
        XCTAssertTrue(f.milestones.allSatisfy { $0.reachedOn == nil })
    }

    func testOneCompleteDayAlreadyUsesTheAverage() {
        let f = Forecaster.forecast(bhavik, [Fixtures.day(start, 1100)], [], start.plusDays(1))
        XCTAssertEqual(.deficits, f.source)
        XCTAssertEqual(1100, f.avgDeficit)
        XCTAssertEqual(1.0, f.rateKgPerWeek, accuracy: 1e-9)
    }

    func testTodayIsExcludedFromTheAverage() {
        let f = Forecaster.forecast(bhavik, [Fixtures.day(start, -2000)], [], start)
        XCTAssertEqual(.startingGuess, f.source)
    }

    func testOnlyTheLatestSevenDaysCount() {
        let asOf = start.plusDays(15)
        let days = (1...7).map { Fixtures.day(start.plusDays($0), -500) } + (8...14).map { Fixtures.day(start.plusDays($0), 1100) }
        let f = Forecaster.forecast(bhavik, days, [], asOf)
        XCTAssertEqual(1100, f.avgDeficit)
        XCTAssertEqual(1.0, f.rateKgPerWeek, accuracy: 1e-9)
    }

    func testFourteenDaysUsesDeficits() {
        let days = Fixtures.bhavikDays(14)
        let asOf = start.plusDays(14)
        let f = Forecaster.forecast(bhavik, days, Trend.of(days, from: start, to: asOf), asOf)
        XCTAssertEqual(.deficits, f.source)   // trend spans only 13 days
        XCTAssertEqual(825, f.avgDeficit)
        XCTAssertEqual(0.75, f.rateKgPerWeek, accuracy: 1e-9)
        XCTAssertNil(f.learnedBurn)
    }

    func testBhavikAfter24DaysFollowsTheWeekAndLearnsBurn() {
        let days = Fixtures.bhavikDays(24)
        let asOf = start.plusDays(24)
        let trend = Trend.of(days, from: start, to: asOf)
        let f = Forecaster.forecast(bhavik, days, trend, asOf)
        XCTAssertEqual(.deficits, f.source)
        XCTAssertEqual(825, f.avgDeficit)
        XCTAssertEqual(0.75, f.rateKgPerWeek, accuracy: 1e-9)
        let learned = f.learnedBurn!
        XCTAssertTrue((2100...2800).contains(learned), "\(learned)")
        XCTAssertEqual(trend.last!.trend, f.currentTrendKg, accuracy: 1e-9)

        // learnedBurn = avg eaten + trend drop * 7700 / window (23 days here)
        let end = trend.first { $0.date == asOf.minusDays(1) }!
        let drop = trend.first!.trend - end.trend
        let eaten = days.dropFirst().map { $0.eaten! }.average
        XCTAssertEqual(roundToInt(eaten + drop * 7700 / 23), learned)
    }

    func testLearnedBurnWindowCapsAt28Days() {
        let days = Fixtures.bhavikDays(60)
        let asOf = start.plusDays(60)
        let trend = Trend.of(days, from: start, to: asOf)
        let f = Forecaster.forecast(bhavik, days, trend, asOf)
        let end = trend.first { $0.date == asOf.minusDays(1) }!.trend
        let s = trend.first { $0.date == asOf.minusDays(29) }!.trend
        let eaten = days.filter { $0.date.isAfter(asOf.minusDays(29)) && $0.date.isBefore(asOf) }.map { $0.eaten! }.average
        XCTAssertEqual(roundToInt(eaten + (s - end) * 7700 / 28), f.learnedBurn)
        XCTAssertEqual(0.75, f.rateKgPerWeek, accuracy: 1e-9)
    }

    func testDeficitsUseLast7DaysWithBalanceSkippingGaps() {
        let asOf = LocalDate(2026, 12, 1)
        var days: [DayLog] = []
        for i in stride(from: 40, through: 21, by: -1) { days.append(Fixtures.day(asOf.minusDays(i), 500)) }
        var d = asOf.minusDays(1)
        var n = 0
        while n < 14 {
            if d.dayOfMonth % 3 != 0 { days.append(Fixtures.day(d, 1000)); n += 1 }
            d = d.minusDays(1)
        }
        days.append(Fixtures.day(asOf, -3000)) // today: ignored
        let f = Forecaster.forecast(bhavik, days, [], asOf)
        XCTAssertEqual(.deficits, f.source)
        XCTAssertEqual(1000, f.avgDeficit)
        XCTAssertEqual(1000.0 / 7700 * 7, f.rateKgPerWeek, accuracy: 1e-9)
    }

    func testRateIsClampedTo1Point5() {
        let days = (1...14).map { Fixtures.day(start.plusDays($0), 2500) }
        let f = Forecaster.forecast(bhavik, days, [], start.plusDays(15))
        XCTAssertEqual(.deficits, f.source)
        XCTAssertEqual(1.5, f.rateKgPerWeek, accuracy: 1e-9)
        XCTAssertEqual(start.plusDays(15).plusDays(24), f.next!.projected) // 5 / (1.5/7) = 23.3 -> 24
    }

    func testStalledWhenNotUnder() {
        let days = (1...14).map { Fixtures.day(start.plusDays($0), $0 % 2 == 0 ? -200 : 100) }
        let f = Forecaster.forecast(bhavik, days, [], start.plusDays(15))
        XCTAssertEqual(.stalled, f.source)
        XCTAssertEqual(0.0, f.rateKgPerWeek, accuracy: 1e-9)
        XCTAssertEqual(-71, f.avgDeficit) // last 7: four at -200, three at +100
        XCTAssertTrue(f.milestones.allSatisfy { $0.projected == nil })
        XCTAssertNotNil(f.next)
    }

    func testStalledWhenExactlyEven() {
        let days = (1...14).map { Fixtures.day(start.plusDays($0), 0) }
        XCTAssertEqual(.stalled, Forecaster.forecast(bhavik, days, [], start.plusDays(15)).source)
    }

    func testReachedMilestonesUseFirstTrendDayAtOrBelow() {
        let trend = [
            TrendPoint(date: start, raw: 120.0, trend: 120.0),
            TrendPoint(date: start.plusDays(30), raw: nil, trend: 115.4),
            TrendPoint(date: start.plusDays(31), raw: 114.0, trend: 115.0),   // First 5 kg reached exactly at 115
            TrendPoint(date: start.plusDays(32), raw: 113.0, trend: 114.2),
            TrendPoint(date: start.plusDays(33), raw: 116.0, trend: 114.5),   // bounced back: still reached on day 31
        ]
        let asOf = start.plusDays(34)
        let f = Forecaster.forecast(bhavik, [], trend, asOf)
        let first = f.milestones[0]
        XCTAssertEqual("First 5 kg", first.label)
        XCTAssertEqual(start.plusDays(31), first.reachedOn)
        XCTAssertNil(first.projected)
        XCTAssertNil(f.milestones[1].reachedOn)
        XCTAssertEqual("5% down", f.next!.label)
        XCTAssertEqual(114.5, f.currentTrendKg, accuracy: 1e-9)
        XCTAssertEqual(asOf.plusDays(5), f.next!.projected) // 0.5 kg at 0.75/7 a day = 4.67 -> 5 days
    }

    func testTrendPointsAfterAsOfAreIgnored() {
        let trend = [TrendPoint(date: start, raw: 120.0, trend: 120.0), TrendPoint(date: start.plusDays(5), raw: 100.0, trend: 110.0)]
        let f = Forecaster.forecast(bhavik, [], trend, start.plusDays(2))
        XCTAssertEqual(120.0, f.currentTrendKg, accuracy: 1e-9)
        XCTAssertTrue(f.milestones.allSatisfy { $0.reachedOn == nil })
    }

    func testAllReachedMeansNoNext() {
        let trend = [TrendPoint(date: start, raw: 120.0, trend: 120.0), TrendPoint(date: start.plusDays(300), raw: 84.0, trend: 84.9)]
        let f = Forecaster.forecast(bhavik, [], trend, start.plusDays(301))
        XCTAssertNil(f.next)
        XCTAssertEqual(start.plusDays(300), f.goal.reachedOn)
    }

    // ---- shiftDays ----

    private func forecastWithNext(_ projected: LocalDate?, kg: Double = 115.0) -> Forecast {
        Forecast(asOf: start, currentTrendKg: 117.0, rateKgPerWeek: 0.75, source: .deficits, avgDeficit: 825, learnedBurn: nil, milestones: [
            Milestone(kg: 118.0, label: "x", reachedOn: start, projected: nil),
            Milestone(kg: kg, label: "First 5 kg", reachedOn: nil, projected: projected),
            Milestone(kg: 85.0, label: "Goal", reachedOn: nil, projected: projected?.plusDays(300), isGoal: true),
        ])
    }

    func testShiftEarlierIsPositive() {
        let prev = forecastWithNext(LocalDate(2026, 12, 20))
        let cur = forecastWithNext(LocalDate(2026, 12, 17))
        XCTAssertEqual(3, Forecaster.shiftDays(cur, prev))
        XCTAssertEqual(-3, Forecaster.shiftDays(prev, cur))
        XCTAssertEqual(0, Forecaster.shiftDays(cur, cur))
    }

    func testShiftNilWhenNotComparable() {
        let ok = forecastWithNext(LocalDate(2026, 12, 17))
        XCTAssertNil(Forecaster.shiftDays(forecastWithNext(nil), ok))
        XCTAssertNil(Forecaster.shiftDays(ok, forecastWithNext(nil)))
        XCTAssertNil(Forecaster.shiftDays(ok, forecastWithNext(LocalDate(2026, 12, 17), kg: 114.0)))
        var done = ok
        done.milestones = ok.milestones.map { var m = $0; m.reachedOn = start; m.projected = nil; return m }
        XCTAssertNil(Forecaster.shiftDays(done, ok))
    }

    func testShiftBetweenRealForecasts() {
        let slow = (1...14).map { Fixtures.day(start.plusDays($0), 500) }
        let fast = (1...14).map { Fixtures.day(start.plusDays($0), 1000) }
        let asOf = start.plusDays(15)
        let prev = Forecaster.forecast(bhavik, slow, [], asOf)
        let cur = Forecaster.forecast(bhavik, fast, [], asOf)
        let s = Forecaster.shiftDays(cur, prev)!
        XCTAssertGreaterThan(s, 0)
        XCTAssertEqual(LocalDate.daysBetween(cur.next!.projected!, prev.next!.projected!), s)
    }

    // ---- whatIf ----

    func testWhatIfConstantDeficit() {
        // 770 kcal/day = 0.1 kg/day; 35 kg -> 350 days
        XCTAssertEqual(start.plusDays(350), Forecaster.whatIf(bhavik, 120.0, start, 770))
        // 32.6 kg at 1,000/day: 32.6 / 0.12987 = 251.02 -> 252
        XCTAssertEqual(start.plusDays(252), Forecaster.whatIf(bhavik, 117.6, start, 1000))
    }

    func testWhatIfMoreDeficitIsSooner() {
        let a = Forecaster.whatIf(bhavik, 117.6, start, 500)!
        let b = Forecaster.whatIf(bhavik, 117.6, start, 1500)!
        XCTAssertTrue(b.isBefore(a))
    }

    func testWhatIfNoDeficitIsNil() {
        XCTAssertNil(Forecaster.whatIf(bhavik, 117.6, start, 0))
        XCTAssertNil(Forecaster.whatIf(bhavik, 117.6, start, -300))
    }

    func testWhatIfAllMovesEveryMilestone() {
        let f = Forecaster.forecast(bhavik, [], [], start)
        let at770 = Forecaster.whatIfAll(f, 120.0, start, 770)
        XCTAssertEqual(11, at770.count)
        XCTAssertEqual(start.plusDays(50), at770.first!.1)   // 5 kg at 0.1 kg a day
        XCTAssertEqual(start.plusDays(350), at770.last!.1)   // goal, same as whatIf
        XCTAssertEqual(Forecaster.whatIf(bhavik, 120.0, start, 770), at770.last!.1)
        let at1540 = Forecaster.whatIfAll(f, 120.0, start, 1540)
        XCTAssertTrue(zip(at1540, at770).allSatisfy { $0.1!.isBefore($1.1!) })
        XCTAssertTrue(Forecaster.whatIfAll(f, 120.0, start, 0).allSatisfy { $0.1 == nil })
    }

    func testWhatIfAllSkipsReachedMilestones() {
        let trend = [TrendPoint(date: start, raw: 120.0, trend: 120.0), TrendPoint(date: start.plusDays(40), raw: 114.5, trend: 114.8)]
        let f = Forecaster.forecast(bhavik, [], trend, start.plusDays(41))
        let all = Forecaster.whatIfAll(f, 114.8, start.plusDays(41), 770)
        XCTAssertEqual("5% down", all.first!.0.label)
        XCTAssertEqual(10, all.count)
    }

    func testWhatIfAtGoalIsToday() {
        XCTAssertEqual(start, Forecaster.whatIf(bhavik, 85.0, start, 500))
        XCTAssertEqual(start, Forecaster.whatIf(bhavik, 84.0, start, 500))
    }
}
