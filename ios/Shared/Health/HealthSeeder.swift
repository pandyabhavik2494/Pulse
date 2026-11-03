#if DEBUG
import Foundation
import HealthKit

/// Debug builds only: writes DemoData into the simulator's Health store so screenshots show real Health
/// reads. Run the app with the `-seedHealth` launch argument. Never compiled into release builds.
enum HealthSeeder {
    static let shareTypes: Set<HKSampleType> = {
        let ids: [HKQuantityTypeIdentifier] = [
            .stepCount, .distanceWalkingRunning, .flightsClimbed, .activeEnergyBurned, .basalEnergyBurned,
            .dietaryEnergyConsumed, .dietaryProtein, .dietaryCarbohydrates, .dietaryFatTotal, .dietaryFiber, .dietarySugar,
            .dietarySodium, .dietaryWater, .heartRate, .restingHeartRate, .heartRateVariabilitySDNN, .oxygenSaturation,
            .respiratoryRate, .vo2Max, .bodyMass, .height,
        ]
        var s: Set<HKSampleType> = Set(ids.map { HKQuantityType($0) })
        s.insert(HKCategoryType(.sleepAnalysis))
        return s
    }()

    static func seed(_ store: HKHealthStore, calendar: Calendar = .current) async throws {
        try await store.requestAuthorization(toShare: shareTypes, read: HealthReader.readTypes)
        // Idempotent: clear what an earlier seed wrote before writing again.
        for type in shareTypes {
            _ = try? await store.deleteObjects(of: type, predicate: HKQuery.predicateForObjects(from: .default()))
        }
        let now = Date()
        let bpm = HKUnit.count().unitDivided(by: .minute())
        var samples: [HKSample] = []
        func at(_ d: LocalDate, _ h: Int, _ m: Int = 0) -> Date {
            min(calendar.date(byAdding: DateComponents(hour: h, minute: m), to: d.startDate(calendar: calendar))!, now.addingTimeInterval(-60))
        }
        func q(_ id: HKQuantityTypeIdentifier, _ unit: HKUnit, _ v: Double?, _ s: Date, _ e: Date? = nil, meta: [String: Any]? = nil) {
            guard let v else { return }
            let end = max(e ?? s, s)
            samples.append(HKQuantitySample(type: HKQuantityType(id), quantity: HKQuantity(unit: unit, doubleValue: v), start: s, end: end, metadata: meta))
        }
        func dbl(_ i: Int?) -> Double? { i.map(Double.init) }

        let profile = DemoData.profile
        q(.height, .meterUnit(with: .centi), profile.heightCm, at(profile.startDate, 7))
        for d in DemoData.days() {
            let isToday = d.date == LocalDate(now, calendar: calendar)
            let dayStart = d.date.startDate(calendar: calendar)
            let dayEnd = isToday ? now.addingTimeInterval(-60) : dayStart.addingTimeInterval(86_399)
            q(.stepCount, .count(), dbl(d.steps), at(d.date, 8), at(d.date, 20))
            q(.distanceWalkingRunning, .meter(), dbl(d.distanceM), at(d.date, 8), at(d.date, 20))
            q(.flightsClimbed, .count(), dbl(d.floors), at(d.date, 9), at(d.date, 18))
            if let a = d.burnedActive, let t = d.burnedTotal {
                q(.activeEnergyBurned, .kilocalorie(), Double(a), at(d.date, 8), at(d.date, 20))
                q(.basalEnergyBurned, .kilocalorie(), Double(t - a), dayStart, dayEnd)
            }
            let meals: [(String, Int, Int)] = [("Breakfast", d.meals.breakfast, 8), ("Lunch", d.meals.lunch, 13), ("Snacks", d.meals.snacks, 16), ("Dinner", d.meals.dinner, 19)]
            for (name, kcal, hour) in meals where kcal > 0 {
                q(.dietaryEnergyConsumed, .kilocalorie(), Double(kcal), at(d.date, hour), meta: ["Meal": name])
            }
            q(.dietaryProtein, .gram(), dbl(d.proteinG), at(d.date, 12))
            q(.dietaryCarbohydrates, .gram(), dbl(d.carbsG), at(d.date, 12))
            q(.dietaryFatTotal, .gram(), dbl(d.fatG), at(d.date, 12))
            q(.dietaryFiber, .gram(), dbl(d.fiberG), at(d.date, 12))
            q(.dietarySugar, .gram(), dbl(d.sugarG), at(d.date, 12))
            q(.dietarySodium, .gramUnit(with: .milli), dbl(d.sodiumMg), at(d.date, 12))
            q(.dietaryWater, .literUnit(with: .milli), dbl(d.waterMl), at(d.date, 12))
            q(.heartRate, bpm, dbl(d.hrMin), at(d.date, 4))
            q(.heartRate, bpm, dbl(d.hrAvg), at(d.date, 12))
            q(.heartRate, bpm, dbl(d.hrMax), at(d.date, 17, 30))
            q(.heartRate, bpm, dbl(d.hrLatest ?? d.hrAvg), at(d.date, 18, 12))
            q(.restingHeartRate, bpm, dbl(d.restingHr), at(d.date, 7, 30))
            q(.heartRateVariabilitySDNN, .secondUnit(with: .milli), dbl(d.hrvMs), at(d.date, 7, 30))
            q(.oxygenSaturation, .percent(), d.spo2Pct.map { $0 / 100 }, at(d.date, 7, 30))
            q(.respiratoryRate, bpm, d.breathingRate, at(d.date, 7, 30))
            q(.vo2Max, HKUnit(from: "ml/kg*min"), d.vo2Max, at(d.date, 7, 30))
            q(.bodyMass, .gramUnit(with: .kilo), d.weightKg, at(d.date, 7, 5))
            if let total = d.sleepMinutes, let deep = d.sleepDeepMin, let rem = d.sleepRemMin {
                samples += night(ending: at(d.date, 7, 5), asleep: total, deep: deep, rem: rem, awake: d.sleepAwakeMin ?? 0)
            }
        }
        try await store.save(samples)
    }

    /// A night of stages ending at [end]: light, deep, light, REM, a little awake, light.
    private static func night(ending end: Date, asleep: Int, deep: Int, rem: Int, awake: Int) -> [HKCategorySample] {
        let light = asleep - deep - rem
        let parts: [(HKCategoryValueSleepAnalysis, Int)] = [
            (.asleepCore, light / 3), (.asleepDeep, deep), (.asleepCore, light / 3), (.asleepREM, rem),
            (.awake, awake), (.asleepCore, light - 2 * (light / 3)),
        ]
        var t = end.addingTimeInterval(-Double(asleep + awake) * 60)
        return parts.filter { $0.1 > 0 }.map { v, mins in
            let s = HKCategorySample(type: HKCategoryType(.sleepAnalysis), value: v.rawValue, start: t, end: t.addingTimeInterval(Double(mins) * 60))
            t = s.endDate
            return s
        }
    }
}
#endif
