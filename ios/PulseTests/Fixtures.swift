import XCTest
@testable import Pulse

/// Bhavik's real numbers: 5 ft 9 in, 32, 120 kg -> 85 kg from Sunday 1 Nov 2026.
enum Fixtures {
    static let start = LocalDate(2026, 11, 1)
    static let bhavik = Profile(name: "Bhavik", heightCm: 175.3, age: 32, male: true, startKg: 120.0, goalKg: 85.0, startDate: start)

    private static let eatenWobble = [0, 120, -80, 60, -100, 40, -40]       // sums to 0
    private static let deficitWobble = [100, -60, 40, -120, 60, -20, 0]     // sums to 0
    private static let scaleNoise = [0.3, -0.2, 0.1, -0.3, 0.2, 0.0, -0.1]  // sums to 0

    /// [n] days from 1 Nov: ~0.75 kg a week on the scale, exactly 825 kcal average deficit per 7 days.
    static func bhavikDays(_ n: Int = 24, steps: Int = 8000) -> [DayLog] {
        (0..<n).map { i in
            let eaten = 1900 + eatenWobble[i % 7]
            let deficit = 825 + deficitWobble[i % 7]
            return DayLog(
                date: start.plusDays(i),
                steps: steps + (i % 5) * 500,
                burnedTotal: eaten + deficit,
                burnedActive: 600,
                eaten: eaten,
                proteinG: 120,
                meals: Meals(breakfast: 400, lunch: 600, dinner: eaten - 1200, snacks: 200),
                weightKg: 120.0 - 0.75 / 7 * Double(i) + scaleNoise[i % 7]
            )
        }
    }

    static func day(_ date: LocalDate, _ balance: Int?, eaten: Int = 2000) -> DayLog {
        guard let balance else { return DayLog(date: date, burnedTotal: 2600) }
        return DayLog(date: date, burnedTotal: eaten + balance, eaten: eaten)
    }
}

/// Words that must never appear in Pulse's copy.
func assertNoGuilt(_ s: String, file: StaticString = #filePath, line: UInt = #line) {
    let lower = s.lowercased()
    for bad in ["fail", "bad", "guilt", "cheat", "ruin", "blew", "should have", "!"] {
        XCTAssertFalse(lower.contains(bad), "'\(bad)' in: \(s)", file: file, line: line)
    }
}
