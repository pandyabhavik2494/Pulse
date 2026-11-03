import Foundation

/// Who Bhavik is and where he's heading. Weight in kg, height in cm.
struct Profile: Codable, Equatable, Sendable {
    var name: String
    var heightCm: Double
    var age: Int
    var male: Bool
    var startKg: Double
    var goalKg: Double
    var startDate: LocalDate
    var stepGoal: Int = 10_000
}

/// Food from MyFitnessPal for one day, kcal per meal. Food with no meal type counts as snacks.
struct Meals: Codable, Equatable, Hashable, Sendable {
    var breakfast: Int = 0
    var lunch: Int = 0
    var dinner: Int = 0
    var snacks: Int = 0

    var total: Int { breakfast + lunch + dinner + snacks }
}

/// Everything Apple Health said about one calendar day. Nil means "no data", not zero.
/// [burnedTotal] is active + resting energy. [eaten] is nil when no food at all was logged that day.
struct DayLog: Codable, Equatable, Sendable {
    var date: LocalDate
    var steps: Int? = nil
    var burnedTotal: Int? = nil
    var burnedActive: Int? = nil
    var eaten: Int? = nil
    var proteinG: Int? = nil
    var meals: Meals = Meals()
    var weightKg: Double? = nil
    var sleepMinutes: Int? = nil
    /// Resting heart rate (bpm) for the day.
    var restingHr: Int? = nil
    /// Average, lowest and highest heart rate (bpm) recorded that day.
    var hrAvg: Int? = nil
    var hrMin: Int? = nil
    var hrMax: Int? = nil
    /// The newest heart-rate reading of the day and its time ("18:32").
    var hrLatest: Int? = nil
    var hrLatestAt: String? = nil
    /// Distance walked or run, metres.
    var distanceM: Int? = nil
    /// Food details from MyFitnessPal: grams of carbs, fat, fibre and sugar, milligrams of sodium.
    var carbsG: Int? = nil
    var fatG: Int? = nil
    var fiberG: Int? = nil
    var sugarG: Int? = nil
    var sodiumMg: Int? = nil
    /// Water logged, ml.
    var waterMl: Int? = nil
    /// Floors climbed and minutes of exercise.
    var floors: Int? = nil
    var exerciseMin: Int? = nil
    /// From the scale: body fat % and lean mass, latest of the day.
    var bodyFatPct: Double? = nil
    var leanKg: Double? = nil
    /// Vitals: heart rate variability (ms), blood oxygen %, breaths a minute, VO2 max.
    var hrvMs: Int? = nil
    var spo2Pct: Double? = nil
    var breathingRate: Double? = nil
    var vo2Max: Double? = nil
    /// Last night's sleep stages in minutes, and bed and wake times ("23:41").
    var sleepDeepMin: Int? = nil
    var sleepRemMin: Int? = nil
    var sleepLightMin: Int? = nil
    var sleepAwakeMin: Int? = nil
    var bedTime: String? = nil
    var wakeTime: String? = nil

    /// Burned minus eaten. Positive = burned more than eaten. Nil if either side is missing.
    var balance: Int? {
        guard let b = burnedTotal, let e = eaten else { return nil }
        return b - e
    }
}

/// One day on the weight line: [raw] is that day's weigh-in if any, [trend] the smoothed weight.
struct TrendPoint: Equatable, Codable, Sendable {
    let date: LocalDate
    let raw: Double?
    let trend: Double
}

enum RateSource: String, Codable, Sendable {
    /// No complete day of food yet: 0.75 kg a week, the agreed starting guess.
    case startingGuess
    /// From the average balance of the last 7 complete days with food logged.
    case deficits
    /// Deficits, cross-checked against what the scale trend actually did.
    case deficitsAndScale
    /// Not losing at the moment (average balance <= 0): no dates can be projected.
    case stalled
}

struct Milestone: Equatable, Codable, Sendable {
    let kg: Double
    /// "First 5 kg", "5% down", "10 kg down", "Under 100 kg", "Goal" ...
    let label: String
    /// The first day the trend reached it, or nil.
    var reachedOn: LocalDate?
    /// Projected date while not reached (nil when stalled or reached).
    var projected: LocalDate?
    var isGoal: Bool = false
}

struct Forecast: Equatable, Sendable {
    let asOf: LocalDate
    let currentTrendKg: Double
    let rateKgPerWeek: Double
    let source: RateSource
    /// Average daily balance used (kcal, positive = burned more), nil if not enough data.
    let avgDeficit: Int?
    /// Learned true daily burn (kcal) once 14+ days of food and weigh-ins exist, else nil.
    let learnedBurn: Int?
    var milestones: [Milestone]

    var next: Milestone? { milestones.first { $0.reachedOn == nil } }
    var goal: Milestone { milestones.last! }
}

/// Monday recap of the previous Monday..Sunday week.
struct WeeklyRecap: Equatable, Sendable {
    let weekNumber: Int
    let weekStart: LocalDate
    let trendChangeKg: Double
    let daysUnder: Int
    let daysLogged: Int
    let totalSteps: Int
    let bestDay: LocalDate?
    let bestDayBalance: Int?
    let focusDaysUnder: Int
    let focusTip: String
    let headline: String
}

/// Ring colour styles. Each reached milestone unlocks the next one.
enum RingStyle: String, CaseIterable, Sendable {
    case mint = "Mint", aurora = "Aurora", sunrise = "Sunrise", ocean = "Ocean", neon = "Neon", ember = "Ember", glacier = "Glacier", prism = "Prism"
}
