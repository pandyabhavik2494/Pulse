import SwiftUI

/// The approved palette: OLED black with glowing colour. No red anywhere (Bhavik: encouraging, never scary).
enum C {
    static let bg = Color(hex: 0x050608)
    static let s1 = Color(hex: 0x0C0E13)
    static let s2 = Color(hex: 0x14171F)
    static let s3 = Color(hex: 0x1C202A)
    static let line = Color(hex: 0x232836)
    static let ink = Color(hex: 0xF3F4F8)
    static let soft = Color(hex: 0xC9CDD9)
    static let mute = Color(hex: 0x8D93A6)
    static let dim = Color(hex: 0x5A6072)
    static let track = Color(hex: 0x161A23)
    static let restingBar = Color(hex: 0x3A4256)

    static let green = Color(hex: 0x2EF2A8)
    static let steps = Color(hex: 0x4DA8FF)
    static let wt = Color(hex: 0xB48CFF)
    static let wt2 = Color(hex: 0xFF7AD9)
    static let sleep = Color(hex: 0x7C8CFF)
    static let food = Color(hex: 0xFFB86B)
    static let hot = Color(hex: 0xFF8A5C)
    static let heart = Color(hex: 0xFF7AD9)

    /// Calm status colours. On track is [green].
    static let steady = Color(hex: 0x8FB8FF)
    static let grow = Color(hex: 0xFFA3C7)

    static let weight = LinearGradient(colors: [wt, wt2], startPoint: .leading, endPoint: .trailing)
    static let go = LinearGradient(colors: [green, steps], startPoint: .leading, endPoint: .trailing)

    static func mood(_ m: Mood) -> Color {
        switch m {
        case .good: green
        case .steady: steady
        case .grow: grow
        case .wait: mute
        }
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

extension Font {
    /// Big numbers: rounded, heavy, monospaced digits so they don't jiggle as they change.
    static func metric(_ size: CGFloat, _ weight: Font.Weight = .heavy) -> Font {
        .system(size: size, weight: weight, design: .rounded).monospacedDigit()
    }
}

/// Plain words used across the app and its glances.
enum Words {
    /// "burned more than eaten" / "eaten more than burned". Never under or over.
    static func balance(eatenMore: Bool) -> String { eatenMore ? "eaten more than burned" : "burned more than eaten" }

    /// "520 more burned" / "180 more eaten".
    static func balanceShort(_ b: Int) -> String { b >= 0 ? "\(Format.int(b)) more burned" : "\(Format.int(-b)) more eaten" }

    /// Milestone weights: whole numbers without a decimal ("115"), others with one ("113.9").
    static func kgShort(_ v: Double) -> String {
        abs(v - v.rounded()) < 0.05 ? String(Int(v.rounded())) : String(format: "%.1f", v)
    }

    static func kg1(_ v: Double) -> String { String(format: "%.1f", v) }

    /// "−2.4" / "+0.3".
    static func signedKg(_ v: Double) -> String { (v < 0 ? "−" : "+") + kg1(abs(v)) }

    static func hm(_ min: Int) -> String { "\(min / 60)h \(min % 60)m" }

    /// "Morning", "Afternoon" or "Evening".
    static func partOfDay(_ t: LocalTime) -> String {
        switch t.hour {
        case 4...11: "Morning"
        case 12...16: "Afternoon"
        default: "Evening"
        }
    }

    /// "Tue, 24 Nov".
    static func dayShort(_ d: LocalDate) -> String { "\(Weekday.short[d.dayOfWeek - 1]), \(d.dayOfMonth) \(Format.months[d.month - 1])" }

    /// "6 weeks sooner", "4 days later".
    static func sooner(_ new: LocalDate, _ old: LocalDate) -> String {
        let d = LocalDate.daysBetween(new, old)
        if d == 0 { return "same as now" }
        let a = abs(d)
        let unit = a >= 14 ? "\(Int((Double(a) / 7).rounded())) weeks" : (a == 1 ? "1 day" : "\(a) days")
        return d > 0 ? "\(unit) sooner" : "\(unit) later"
    }

    /// "1 day earlier" / "3 days later".
    static func shift(_ days: Int) -> String {
        switch days {
        case 1: "1 day earlier"
        case 2...: "\(days) days earlier"
        case -1: "1 day later"
        default: "\(-days) days later"
        }
    }

    static func syncAgo(_ last: Date?, _ now: Date) -> String {
        guard let last else { return "Not synced yet" }
        let m = max(0, Int(now.timeIntervalSince(last) / 60))
        let ago = m < 1 ? "just now" : m < 60 ? "\(m) min ago" : m < 1440 ? "\(m / 60) h ago" : "\(m / 1440) d ago"
        return "Health · \(ago)"
    }
}
