import Foundation

/// Text formatting shared by all copy. Plain English, fixed US-style digits so output never depends on the phone locale.
enum Format {
    static let months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

    /// "17 Dec", or "31 Jan 2027" when [date] is not in the same year as [today].
    static func date(_ date: LocalDate, _ today: LocalDate) -> String {
        let base = "\(date.dayOfMonth) \(months[date.month - 1])"
        return date.year == today.year ? base : "\(base) \(date.year)"
    }

    /// 1140 -> "1,140", -2300 -> "-2,300".
    static func int(_ n: Int) -> String {
        let digits = String(abs(n))
        var out = ""
        for (i, ch) in digits.enumerated() {
            if i > 0 && (digits.count - i) % 3 == 0 { out.append(",") }
            out.append(ch)
        }
        return n < 0 ? "-" + out : out
    }

    /// 115.0 -> "115", 117.64 -> "117.6", 0.04 -> "0". At most one decimal, no trailing ".0".
    static func kg(_ kg: Double) -> String {
        let tenths = roundToInt(kg * 10)
        return tenths % 10 == 0 ? String(tenths / 10) : String(format: "%.1f", Double(tenths) / 10.0)
    }

    /// "1 day" / "3 days".
    static func days(_ n: Int) -> String { abs(n) == 1 ? "\(n) day" : "\(n) days" }

    static func round1(_ x: Double) -> Double { Double(roundToInt(x * 10)) / 10.0 }

    private static let words = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"]

    /// 3 -> "three"; numbers past twelve stay as digits.
    static func word(_ n: Int) -> String { words.indices.contains(n) ? words[n] : int(n) }

    static func capitalized(_ s: String) -> String { s.prefix(1).uppercased() + s.dropFirst() }
}
