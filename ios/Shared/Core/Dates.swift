import Foundation

/// A calendar day with no time zone, like java.time.LocalDate: stored as days since 1970-01-01.
/// All of Pulse's day maths runs on these so it never depends on clocks or locales.
struct LocalDate: Hashable, Comparable, Codable, CustomStringConvertible, Sendable {
    let epochDay: Int

    init(epochDay: Int) { self.epochDay = epochDay }

    /// Civil date to days (Howard Hinnant's days_from_civil).
    init(_ year: Int, _ month: Int, _ day: Int) {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        epochDay = era * 146_097 + doe - 719_468
    }

    /// The day [date] falls on in [calendar]'s time zone.
    init(_ date: Date, calendar: Calendar = .current) {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(c.year!, c.month!, c.day!)
    }

    private var civil: (year: Int, month: Int, day: Int) {
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        return (yoe + era * 400 + (m <= 2 ? 1 : 0), m, d)
    }

    var year: Int { civil.year }
    var month: Int { civil.month }
    var dayOfMonth: Int { civil.day }

    /// ISO weekday: 1 = Monday ... 7 = Sunday.
    var dayOfWeek: Int { ((epochDay % 7 + 7 + 3) % 7) + 1 }

    func plusDays(_ n: Int) -> LocalDate { LocalDate(epochDay: epochDay + n) }
    func minusDays(_ n: Int) -> LocalDate { LocalDate(epochDay: epochDay - n) }
    func plusWeeks(_ n: Int) -> LocalDate { plusDays(7 * n) }
    func minusWeeks(_ n: Int) -> LocalDate { minusDays(7 * n) }

    func isBefore(_ o: LocalDate) -> Bool { epochDay < o.epochDay }
    func isAfter(_ o: LocalDate) -> Bool { epochDay > o.epochDay }

    /// The Monday on or before this day.
    var mondayOnOrBefore: LocalDate { minusDays(dayOfWeek - 1) }

    /// Days from [a] to [b] (positive when b is later).
    static func daysBetween(_ a: LocalDate, _ b: LocalDate) -> Int { b.epochDay - a.epochDay }

    /// Midnight at the start of this day in [calendar]'s zone.
    func startDate(calendar: Calendar = .current) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: dayOfMonth))!
    }

    static func < (a: LocalDate, b: LocalDate) -> Bool { a.epochDay < b.epochDay }

    var description: String { String(format: "%04d-%02d-%02d", year, month, dayOfMonth) }

    static func today(calendar: Calendar = .current) -> LocalDate { LocalDate(Date(), calendar: calendar) }
}

/// A time of day, like java.time.LocalTime.
struct LocalTime: Hashable, Sendable {
    let hour: Int
    let minute: Int
    let second: Int

    init(_ hour: Int, _ minute: Int, _ second: Int = 0) {
        self.hour = hour; self.minute = minute; self.second = second
    }

    init(_ date: Date, calendar: Calendar = .current) {
        let c = calendar.dateComponents([.hour, .minute, .second], from: date)
        self.init(c.hour!, c.minute!, c.second!)
    }

    var secondOfDay: Int { hour * 3600 + minute * 60 + second }
}

enum Weekday {
    static let full = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    static let short = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
}

/// Kotlin's roundToInt: nearest integer, ties toward positive infinity.
@inline(__always) func roundToInt(_ x: Double) -> Int { Int((x + 0.5).rounded(.down)) }

extension Array where Element == Int {
    var average: Double { isEmpty ? .nan : Double(reduce(0, +)) / Double(count) }
}

extension Array where Element == Double {
    var average: Double { isEmpty ? .nan : reduce(0, +) / Double(count) }
}

extension Comparable {
    func clamped(_ lo: Self, _ hi: Self) -> Self { Swift.min(Swift.max(self, lo), hi) }
}
