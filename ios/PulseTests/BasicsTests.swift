import XCTest
@testable import Pulse

final class BudgetTests: XCTestCase {
    private let p = Fixtures.bhavik
    private let today = LocalDate(2026, 11, 29)

    func testPaceIs825ADay() { XCTAssertEqual(825, Budget.paceKcal) }

    func testUsesTheUsualBurn() {
        let days = (1...7).map { DayLog(date: today.minusDays($0), burnedTotal: 3000) } +
            [DayLog(date: today, burnedTotal: 1200, eaten: 1500)]
        let b = Budget.today(p, days, today, 118.0)
        XCTAssertEqual(3000, b.burn)
        XCTAssertEqual(2175, b.budget)
        XCTAssertEqual(675, b.left)
        XCTAssertEqual(7, b.burnDays)
    }

    func testABigDayRaisesTheBudget() {
        let days = [DayLog(date: today.minusDays(1), burnedTotal: 2800), DayLog(date: today, burnedTotal: 3400, eaten: 3000)]
        let b = Budget.today(p, days, today, 118.0)
        XCTAssertEqual(3400, b.burn)
        XCTAssertEqual(3400 - 825 - 3000, b.left)
    }

    func testNoDataStillGivesABudget() {
        let b = Budget.today(p, [], today, nil)
        XCTAssertEqual(0, b.burnDays)
        XCTAssertEqual(Double(Int(Double(Energy.bmr(p, 120.0)) * 1.3)), Double(b.burn), accuracy: 1)
        XCTAssertEqual(b.budget, b.left)
    }
}

final class EnergyTests: XCTestCase {
    private let bhavik = Fixtures.bhavik

    func testBmrMale() {
        // 10*120 + 6.25*175.3 - 5*32 + 5 = 2140.625
        XCTAssertEqual(2141, Energy.bmr(bhavik, 120.0))
    }

    func testBmrFemale() {
        var f = bhavik; f.male = false
        XCTAssertEqual(1975, Energy.bmr(f, 120.0))
    }

    func testBmrDropsWithWeight() { XCTAssertEqual(2141 - 350, Energy.bmr(bhavik, 85.0)) }

    func testRestingSoFar() {
        XCTAssertEqual(0, Energy.restingSoFar(bhavik, 120.0, secondOfDay: 0))
        XCTAssertEqual(1071, Energy.restingSoFar(bhavik, 120.0, secondOfDay: 12 * 3600))
        XCTAssertEqual(1784, Energy.restingSoFar(bhavik, 120.0, secondOfDay: 20 * 3600))
        XCTAssertEqual(2141, Energy.restingSoFar(bhavik, 120.0, secondOfDay: 86_399.999_999_999))
    }

    func testSplitBoth() { XCTAssertTrue(Energy.split(2480, 660, 2000)! == (1820, 660)) }
    func testSplitActiveLargerThanTotalClampsResting() { XCTAssertTrue(Energy.split(500, 700, 2000)! == (0, 700)) }
    func testSplitTotalOnly() {
        XCTAssertTrue(Energy.split(2480, nil, 2000)! == (2000, 480))
        XCTAssertTrue(Energy.split(1500, nil, 2000)! == (1500, 0))
    }
    func testSplitActiveOnly() { XCTAssertTrue(Energy.split(nil, 300, 2000)! == (2000, 300)) }
    func testSplitNeither() { XCTAssertNil(Energy.split(nil, nil, 2000)) }
}

final class FormatTests: XCTestCase {
    func testDates() {
        let today = LocalDate(2026, 11, 20)
        XCTAssertEqual("17 Dec", Format.date(LocalDate(2026, 12, 17), today))
        XCTAssertEqual("1 Nov", Format.date(LocalDate(2026, 11, 1), today))
        XCTAssertEqual("31 Jan 2027", Format.date(LocalDate(2027, 1, 31), today))
        XCTAssertEqual("7 Sep 2027", Format.date(LocalDate(2027, 9, 7), today))
    }

    func testInts() {
        XCTAssertEqual("520", Format.int(520))
        XCTAssertEqual("1,140", Format.int(1140))
        XCTAssertEqual("10,000", Format.int(10_000))
        XCTAssertEqual("-2,300", Format.int(-2300))
    }

    func testKg() {
        XCTAssertEqual("115", Format.kg(115.0))
        XCTAssertEqual("117.6", Format.kg(117.64))
        XCTAssertEqual("2", Format.kg(120.0 - 118.0))
        XCTAssertEqual("0.2", Format.kg(0.2))
        XCTAssertEqual("96", Format.kg(95.999999))
    }

    func testDays() {
        XCTAssertEqual("1 day", Format.days(1))
        XCTAssertEqual("0 days", Format.days(0))
        XCTAssertEqual("3 days", Format.days(3))
    }

    func testWords() {
        XCTAssertEqual("three", Format.word(3))
        XCTAssertEqual("15", Format.word(15))
        XCTAssertEqual("Three", Format.capitalized(Format.word(3)))
    }

    func testLocalDate() {
        XCTAssertEqual(7, LocalDate(2026, 11, 1).dayOfWeek)   // a Sunday
        XCTAssertEqual(1, LocalDate(2026, 11, 23).dayOfWeek)  // a Monday
        XCTAssertEqual(LocalDate(2026, 10, 26), LocalDate(2026, 11, 1).mondayOnOrBefore)
        XCTAssertEqual(LocalDate(2027, 3, 1), LocalDate(2027, 2, 28).plusDays(1))
        XCTAssertEqual(LocalDate(2028, 2, 29), LocalDate(2028, 3, 1).minusDays(1))
        XCTAssertEqual("2026-11-01", LocalDate(2026, 11, 1).description)
    }
}

final class TrendTests: XCTestCase {
    private let start = Fixtures.start
    private func w(_ i: Int, _ kg: Double?) -> DayLog { DayLog(date: start.plusDays(i), weightKg: kg) }

    func testEmptyWithoutWeighIns() {
        XCTAssertTrue(Trend.of([], from: start, to: start.plusDays(5)).isEmpty)
        XCTAssertTrue(Trend.of([DayLog(date: start, steps: 100)], from: start, to: start.plusDays(5)).isEmpty)
    }

    func testSeededWithFirstWeighIn() {
        let t = Trend.of([w(0, 120.0)], from: start, to: start)
        XCTAssertEqual(1, t.count)
        XCTAssertEqual(120.0, t[0].trend, accuracy: 1e-9)
        XCTAssertEqual(120.0, t[0].raw!, accuracy: 1e-9)
    }

    func testEmaStep() {
        let t = Trend.of([w(0, 120.0), w(1, 119.0), w(2, 118.0)], from: start, to: start.plusDays(2))
        XCTAssertEqual(119.9, t[1].trend, accuracy: 1e-9)
        XCTAssertEqual(119.9 + 0.1 * (118.0 - 119.9), t[2].trend, accuracy: 1e-9)
    }

    func testGapsCarryForward() {
        let t = Trend.of([w(0, 120.0), w(3, 110.0)], from: start, to: start.plusDays(5))
        XCTAssertEqual(6, t.count)
        XCTAssertEqual([120.0, 120.0, 120.0, 119.0, 119.0, 119.0], t.map(\.trend))
        XCTAssertNil(t[1].raw)
        XCTAssertEqual(110.0, t[3].raw!, accuracy: 1e-9)
        XCTAssertEqual((0...5).map { start.plusDays($0) }, t.map(\.date))
    }

    func testStartsAtFirstWeighInWhenFromIsEarlier() {
        let t = Trend.of([w(2, 120.0)], from: start.minusDays(10), to: start.plusDays(3))
        XCTAssertEqual(start.plusDays(2), t.first!.date)
        XCTAssertEqual(2, t.count)
    }

    func testLaterFromKeepsTheSameValues() {
        let days = Fixtures.bhavikDays(24)
        let full = Trend.of(days, from: start, to: start.plusDays(23))
        let window = Trend.of(days, from: start.plusDays(10), to: start.plusDays(23))
        XCTAssertEqual(14, window.count)
        XCTAssertEqual(Array(full.dropFirst(10)), window)
    }

    func testToBeforeFirstWeighInIsEmpty() {
        XCTAssertTrue(Trend.of([w(5, 120.0)], from: start, to: start.plusDays(2)).isEmpty)
    }

    func testWeighInsAfterToAreIgnored() {
        let t = Trend.of([w(0, 120.0), w(3, 100.0)], from: start, to: start.plusDays(2))
        XCTAssertEqual(3, t.count)
        XCTAssertTrue(t.allSatisfy { $0.trend == 120.0 })
    }

    func testDuplicateDateLastNonNullWins() {
        let t = Trend.of([w(0, 120.0), w(1, 118.0), w(1, 119.0), w(1, nil)], from: start, to: start.plusDays(1))
        XCTAssertEqual(119.0, t[1].raw!, accuracy: 1e-9)
        XCTAssertEqual(119.9, t[1].trend, accuracy: 1e-9)
    }

    func testUnsortedInput() {
        let t = Trend.of([w(1, 119.0), w(0, 120.0)], from: start, to: start.plusDays(1))
        XCTAssertEqual(120.0, t[0].trend, accuracy: 1e-9)
        XCTAssertEqual(119.9, t[1].trend, accuracy: 1e-9)
    }

    func testFixtureTrendFalls() {
        let t = Trend.of(Fixtures.bhavikDays(24), from: start, to: start.plusDays(23))
        XCTAssertEqual(24, t.count)
        XCTAssertLessThan(t.last!.trend, 119.0)
        XCTAssertGreaterThan(t.last!.trend, 117.0)
    }
}

final class TwoWeeksTests: XCTestCase {
    private let p = Fixtures.bhavik
    private let today = LocalDate(2026, 11, 29)

    private func day(_ back: Int, burned: Int? = nil, eaten: Int? = nil, kg: Double? = nil) -> DayLog {
        DayLog(date: today.minusDays(back), burnedTotal: burned, eaten: eaten, weightKg: kg)
    }

    func testChangeFollowsTheBalance() {
        XCTAssertEqual(-1.0, TwoWeeks.changeFor(550.0), accuracy: 1e-9)
        XCTAssertEqual(0.5, TwoWeeks.changeFor(-275.0), accuracy: 1e-9)
    }

    func testNoDataStillGivesAStartingEstimate() {
        let o = TwoWeeks.outlook(p, [], today)
        XCTAssertEqual(.startingGuess, o.basis)
        XCTAssertEqual(120.0, o.fromKg, accuracy: 1e-9)
        XCTAssertEqual(-1.5, o.changeKg, accuracy: 1e-9)
        XCTAssertTrue(o.early)
    }

    func testTodayAloneIsUsedWhenThereIsNothingElse() {
        let o = TwoWeeks.outlook(p, [day(0, burned: 2000, eaten: 2550)], today)
        XCTAssertEqual(.today, o.basis)
        XCTAssertEqual(1.0, o.changeKg, accuracy: 1e-9)
    }

    func testOneDayOfFoodIsAnEarlyEstimateAndAWeekIsNot() {
        let one = TwoWeeks.outlook(p, [day(1, burned: 2800, eaten: 2250)], today)
        XCTAssertEqual(.food, one.basis)
        XCTAssertEqual(1, one.foodDays)
        XCTAssertEqual(-1.0, one.changeKg, accuracy: 1e-9)
        XCTAssertTrue(one.early)

        let week = (1...7).map { day($0, burned: 2800, eaten: 2250) }
        let w = TwoWeeks.outlook(p, week, today)
        XCTAssertEqual(7, w.foodDays)
        XCTAssertFalse(w.early)
    }

    func testEatingALotShowsAGain() {
        let o = TwoWeeks.outlook(p, (1...7).map { day($0, burned: 2500, eaten: 3600) }, today)
        XCTAssertGreaterThan(o.changeKg, 0)
        XCTAssertEqual(2.0, o.changeKg, accuracy: 1e-9)
        XCTAssertEqual(-1100, o.avgBalance)
    }

    func testOnlyTheLatestSevenFoodDaysCount() {
        let old = (8...14).map { day($0, burned: 2500, eaten: 3600) }
        let recent = (1...7).map { day($0, burned: 2800, eaten: 2250) }
        XCTAssertEqual(550, TwoWeeks.outlook(p, old + recent, today).avgBalance)
    }

    func testWeighInsAloneGiveAScaleOutlook() {
        let days = (0...10).map { day($0, kg: 110.0 + Double($0) * 0.1) }
        let o = TwoWeeks.outlook(p, days, today)
        XCTAssertEqual(.scale, o.basis)
        XCTAssertEqual(-1.4, o.changeKg, accuracy: 1e-6)
    }

    func testFoodAndScaleBlendWithFoodLeading() {
        var byDate: [LocalDate: DayLog] = [:]
        for d in (0...10).map({ day($0, kg: 110.0 + Double($0) * 0.1) }) { byDate[d.date] = d }
        for b in 1...7 { byDate[today.minusDays(b)] = day(b, burned: 2800, eaten: 2250, kg: 110.0 + Double(b) * 0.1) }
        let o = TwoWeeks.outlook(p, Array(byDate.values), today)
        XCTAssertEqual(.foodAndScale, o.basis)
        XCTAssertEqual((2 * -1.0 + -1.4) / 3, o.changeKg, accuracy: 1e-6)
    }

    func testPeriodsWalkBackInTwoWeekSteps() {
        let days = (0...40).map { day($0, kg: 110.0 + Double($0) * 0.1) }
        let ps = TwoWeeks.periods(days, today)
        XCTAssertEqual(2, ps.count)
        XCTAssertEqual(today, ps[0].end)
        XCTAssertEqual(14, ps[0].days)
        XCTAssertEqual(ps[0].start, ps[1].end)
        XCTAssertTrue(ps.allSatisfy { $0.changeKg < 0 })
    }

    func testAShortFirstStretchWhenThereIsLessThanTwoWeeks() {
        let ps = TwoWeeks.periods([day(5, kg: 112.0), day(0, kg: 111.0)], today)
        XCTAssertEqual(1, ps.count)
        XCTAssertEqual(5, ps[0].days)
        XCTAssertLessThan(ps[0].changeKg, 0)
    }

    func testNoWeighInsNoPeriods() {
        XCTAssertTrue(TwoWeeks.periods([], today).isEmpty)
        XCTAssertTrue(TwoWeeks.periods([day(0, kg: 100.0)], today).isEmpty)
    }
}
