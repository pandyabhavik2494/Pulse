import Foundation

/// Five weeks of realistic sample days for Bhavik, used to seed the simulator's Health store for
/// screenshots (and by `-demo` runs that skip Health entirely). Never used on a real device.
enum DemoData {
    static func profile(today: LocalDate = .today()) -> Profile {
        Profile(name: "Bhavik", heightCm: 175.3, age: 32, male: true, startKg: 120.0, goalKg: 85.0, startDate: today.minusDays(35))
    }

    static var profile: Profile { profile() }

    private static let eatenWobble = [0, 140, -90, 60, -120, 380, -40]
    private static let activeWobble = [0, 120, -80, 200, -60, 260, 40]
    private static let scaleNoise = [0.3, -0.2, 0.1, -0.3, 0.2, 0.0, -0.1]
    private static let sleepMins = [452, 418, 391, 476, 433, 402, 465]

    /// Complete past days, then today "so far" as of about 6 pm.
    static func days(today: LocalDate = .today()) -> [DayLog] {
        let start = today.minusDays(35)
        var out: [DayLog] = []
        for i in 0..<35 {
            let date = start.plusDays(i)
            let k = i % 7
            let active = 720 + activeWobble[k]
            let basal = 2_120 - i * 3
            let eaten = 1_950 + eatenWobble[k]
            let breakfast = 420
            let lunch = 640
            let snacks = 220 + (k == 5 ? 300 : 0)
            let sleep = sleepMins[k]
            let deep = 48 + (k * 7) % 25
            let rem = 88 + (k * 5) % 20
            out.append(DayLog(
                date: date,
                steps: 8_200 + (k * 1_337) % 4_100,
                burnedTotal: active + basal,
                burnedActive: active,
                eaten: eaten,
                proteinG: 120 + k * 4,
                meals: Meals(breakfast: breakfast, lunch: lunch, dinner: eaten - breakfast - lunch - snacks, snacks: snacks),
                weightKg: Format.round1(120.0 - 0.118 * Double(i) + scaleNoise[k]),
                sleepMinutes: sleep,
                restingHr: 71 - i / 6,
                hrAvg: 84 - i / 8,
                hrMin: 58,
                hrMax: 138 + k * 3,
                distanceM: 6_100 + (k * 917) % 2_800,
                carbsG: 190 + k * 6,
                fatG: 62 + k * 2,
                fiberG: 26,
                sugarG: 41,
                sodiumMg: 2_150,
                waterMl: 2_300,
                floors: 6 + k,
                exerciseMin: 34 + k * 3,
                hrvMs: 38 + (i % 9),
                spo2Pct: 97.0,
                breathingRate: 15.2,
                vo2Max: 33.4 + Double(i) * 0.03,
                sleepDeepMin: deep,
                sleepRemMin: rem,
                sleepLightMin: sleep - deep - rem,
                sleepAwakeMin: 24,
                bedTime: "23:\(10 + k * 5)",
                wakeTime: "07:0\(k)"
            ))
        }
        // Today so far.
        out.append(DayLog(
            date: today,
            steps: 8_412,
            burnedTotal: 720 + 1_760,
            burnedActive: 720,
            eaten: 1_960,
            proteinG: 112,
            meals: Meals(breakfast: 420, lunch: 650, dinner: 680, snacks: 210),
            weightKg: Format.round1(120.0 - 0.118 * 35 - 0.2),
            sleepMinutes: 410,
            restingHr: 62,
            hrAvg: 79,
            hrMin: 57,
            hrMax: 131,
            hrLatest: 76,
            hrLatestAt: "18:12",
            distanceM: 6_480,
            carbsG: 176,
            fatG: 58,
            fiberG: 24,
            sugarG: 38,
            sodiumMg: 1_840,
            waterMl: 1_900,
            floors: 9,
            exerciseMin: 41,
            hrvMs: 46,
            spo2Pct: 97.0,
            breathingRate: 14.8,
            vo2Max: 34.5,
            sleepDeepMin: 58,
            sleepRemMin: 88,
            sleepLightMin: 264,
            sleepAwakeMin: 20,
            bedTime: "23:18",
            wakeTime: "07:16"
        ))
        return out
    }
}
