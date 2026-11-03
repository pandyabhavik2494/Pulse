import Foundation
import HealthKit

/// Reads Apple Health (read-only) into one DayLog per calendar day.
///
/// Weight comes from the VeSync scale (its samples win whenever it wrote any), food from MyFitnessPal,
/// energy, steps, heart and sleep from the Apple Watch or iPhone. Daily totals use statistics collection
/// queries, so Health's own source priority removes duplicates.
final class HealthReader: @unchecked Sendable {
    static let shared = HealthReader()
    let store = HKHealthStore()

    static var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    private static let bpm = HKUnit.count().unitDivided(by: .minute())

    static let readTypes: Set<HKObjectType> = {
        let ids: [HKQuantityTypeIdentifier] = [
            .stepCount, .distanceWalkingRunning, .flightsClimbed, .appleExerciseTime,
            .activeEnergyBurned, .basalEnergyBurned,
            .dietaryEnergyConsumed, .dietaryProtein, .dietaryCarbohydrates, .dietaryFatTotal,
            .dietaryFiber, .dietarySugar, .dietarySodium, .dietaryWater,
            .heartRate, .restingHeartRate, .heartRateVariabilitySDNN, .oxygenSaturation, .respiratoryRate, .vo2Max,
            .bodyMass, .bodyFatPercentage, .leanBodyMass, .height,
        ]
        var set: Set<HKObjectType> = Set(ids.map { HKQuantityType($0) })
        set.insert(HKCategoryType(.sleepAnalysis))
        set.insert(HKCharacteristicType(.dateOfBirth))
        set.insert(HKCharacteristicType(.biologicalSex))
        return set
    }()

    /// Types whose changes should trigger a refresh (observer queries + background delivery).
    static let watchedTypes: [HKSampleType] = [
        HKQuantityType(.stepCount), HKQuantityType(.activeEnergyBurned), HKQuantityType(.dietaryEnergyConsumed),
        HKQuantityType(.bodyMass), HKCategoryType(.sleepAnalysis),
    ]

    func requestAuthorization() async throws {
        try await store.requestAuthorization(toShare: [], read: Self.readTypes)
    }

    /// True once the read-permission sheet has been shown (Health never says what was granted for reads).
    func hasAsked() async -> Bool {
        let status = try? await store.statusForAuthorizationRequest(toShare: [], read: Self.readTypes)
        return status == .unnecessary
    }

    // MARK: - Profile hints from Health

    struct Body { var heightCm: Double?; var age: Int?; var male: Bool?; var weightKg: Double? }

    func body() async -> Body {
        var b = Body()
        if let dob = try? store.dateOfBirthComponents().date {
            b.age = Calendar.current.dateComponents([.year], from: dob, to: Date()).year
        }
        if let sex = try? store.biologicalSex().biologicalSex, sex != .notSet {
            b.male = sex == .male
        }
        b.heightCm = await latest(.height, unit: .meterUnit(with: .centi))
        b.weightKg = await latest(.bodyMass, unit: .gramUnit(with: .kilo))
        return b
    }

    private func latest(_ id: HKQuantityTypeIdentifier, unit: HKUnit) async -> Double? {
        let d = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: HKQuantityType(id))],
            sortDescriptors: [SortDescriptor(\.endDate, order: .reverse)], limit: 1)
        return try? await d.result(for: store).first?.quantity.doubleValue(for: unit)
    }

    // MARK: - Days

    /// One DayLog per day from [from] through [to] (inclusive), in the phone's calendar.
    /// burnedTotal is active + resting energy when Health has both; with active only, burnedTotal stays nil.
    func days(from: LocalDate, to: LocalDate, calendar: Calendar = .current) async -> [DayLog] {
        let start = from.startDate(calendar: calendar)
        let end = to.plusDays(1).startDate(calendar: calendar)
        var logs: [LocalDate: DayLog] = [:]
        var d = from
        while !d.isAfter(to) { logs[d] = DayLog(date: d); d = d.plusDays(1) }

        @Sendable func sums(_ id: HKQuantityTypeIdentifier, _ unit: HKUnit) async -> [LocalDate: Double] {
            await stats(id, .cumulativeSum, start, end, calendar) { $0.sumQuantity()?.doubleValue(for: unit) }
        }
        func apply<T>(_ values: [LocalDate: T], _ set: (inout DayLog, T) -> Void) {
            for (k, v) in values where logs[k] != nil { set(&logs[k]!, v) }
        }

        async let steps = sums(.stepCount, .count())
        async let distance = sums(.distanceWalkingRunning, .meter())
        async let floors = sums(.flightsClimbed, .count())
        async let exercise = sums(.appleExerciseTime, .minute())
        async let active = sums(.activeEnergyBurned, .kilocalorie())
        async let basal = sums(.basalEnergyBurned, .kilocalorie())
        async let eaten = sums(.dietaryEnergyConsumed, .kilocalorie())
        async let protein = sums(.dietaryProtein, .gram())
        async let carbs = sums(.dietaryCarbohydrates, .gram())
        async let fat = sums(.dietaryFatTotal, .gram())
        async let fiber = sums(.dietaryFiber, .gram())
        async let sugar = sums(.dietarySugar, .gram())
        async let sodium = sums(.dietarySodium, .gramUnit(with: .milli))
        async let water = sums(.dietaryWater, .literUnit(with: .milli))
        async let hr = stats(.heartRate, [.discreteAverage, .discreteMin, .discreteMax], start, end, calendar) { s -> (Int, Int, Int)? in
            guard let a = s.averageQuantity(), let lo = s.minimumQuantity(), let hi = s.maximumQuantity() else { return nil }
            return (roundToInt(a.doubleValue(for: Self.bpm)), roundToInt(lo.doubleValue(for: Self.bpm)), roundToInt(hi.doubleValue(for: Self.bpm)))
        }
        async let resting = avg(.restingHeartRate, Self.bpm, start, end, calendar)
        async let hrv = avg(.heartRateVariabilitySDNN, .secondUnit(with: .milli), start, end, calendar)
        async let spo2 = avg(.oxygenSaturation, .percent(), start, end, calendar)
        async let breathing = avg(.respiratoryRate, Self.bpm, start, end, calendar)
        async let vo2 = stats(.vo2Max, .mostRecent, start, end, calendar) {
            $0.mostRecentQuantity()?.doubleValue(for: HKUnit(from: "ml/kg*min"))
        }
        async let bodyFat = stats(.bodyFatPercentage, .mostRecent, start, end, calendar) { $0.mostRecentQuantity()?.doubleValue(for: .percent()) }
        async let lean = stats(.leanBodyMass, .mostRecent, start, end, calendar) { $0.mostRecentQuantity()?.doubleValue(for: .gramUnit(with: .kilo)) }
        async let weights = weighIns(start, end, calendar)
        async let meals = meals(start, end, calendar)
        async let sleep = sleep(from: start, end: end, calendar: calendar)
        async let latest = latestHeart(to, calendar)

        apply(await steps) { $0.steps = roundToInt($1) }
        apply(await distance) { $0.distanceM = roundToInt($1) }
        apply(await floors) { $0.floors = roundToInt($1) }
        apply(await exercise) { $0.exerciseMin = roundToInt($1) }
        let basalByDay = await basal
        apply(await active) { log, a in
            log.burnedActive = roundToInt(a)
            if let b = basalByDay[log.date] { log.burnedTotal = roundToInt(a + b) }
        }
        apply(basalByDay) { log, b in if log.burnedActive == nil { log.burnedTotal = roundToInt(b) } }
        apply(await eaten) { $0.eaten = roundToInt($1) }
        apply(await protein) { $0.proteinG = roundToInt($1) }
        apply(await carbs) { $0.carbsG = roundToInt($1) }
        apply(await fat) { $0.fatG = roundToInt($1) }
        apply(await fiber) { $0.fiberG = roundToInt($1) }
        apply(await sugar) { $0.sugarG = roundToInt($1) }
        apply(await sodium) { $0.sodiumMg = roundToInt($1) }
        apply(await water) { $0.waterMl = roundToInt($1) }
        apply(await hr) { $0.hrAvg = $1.0; $0.hrMin = $1.1; $0.hrMax = $1.2 }
        apply(await resting) { $0.restingHr = roundToInt($1) }
        apply(await hrv) { $0.hrvMs = roundToInt($1) }
        apply(await spo2) { $0.spo2Pct = $1 * 100 }
        apply(await breathing) { $0.breathingRate = $1 }
        apply(await vo2) { $0.vo2Max = $1 }
        apply(await bodyFat) { $0.bodyFatPct = $1 * 100 }
        apply(await lean) { $0.leanKg = $1 }
        apply(await weights) { $0.weightKg = $1 }
        apply(await meals) { $0.meals = $1 }
        apply(await sleep) { log, n in
            log.sleepMinutes = n.asleep
            log.sleepDeepMin = n.deep > 0 ? n.deep : nil
            log.sleepRemMin = n.rem > 0 ? n.rem : nil
            log.sleepLightMin = n.light > 0 ? n.light : nil
            log.sleepAwakeMin = n.awake > 0 ? n.awake : nil
            log.bedTime = n.bed
            log.wakeTime = n.wake
        }
        if let (v, at) = await latest { logs[to]?.hrLatest = v; logs[to]?.hrLatestAt = at }

        return logs.values.sorted { $0.date < $1.date }
    }

    // MARK: - Queries

    private func stats<T>(
        _ id: HKQuantityTypeIdentifier, _ options: HKStatisticsOptions, _ start: Date, _ end: Date, _ calendar: Calendar,
        _ pick: @escaping (HKStatistics) -> T?
    ) async -> [LocalDate: T] {
        let type = HKQuantityType(id)
        let predicate = HKSamplePredicate.quantitySample(type: type, predicate: HKQuery.predicateForSamples(withStart: start, end: end))
        let query = HKStatisticsCollectionQueryDescriptor(predicate: predicate, options: options, anchorDate: start, intervalComponents: DateComponents(day: 1))
        guard let collection = try? await query.result(for: store) else { return [:] }
        var out: [LocalDate: T] = [:]
        collection.enumerateStatistics(from: start, to: end) { s, _ in
            if let v = pick(s) { out[LocalDate(s.startDate, calendar: calendar)] = v }
        }
        return out
    }

    private func avg(_ id: HKQuantityTypeIdentifier, _ unit: HKUnit, _ start: Date, _ end: Date, _ calendar: Calendar) async -> [LocalDate: Double] {
        await stats(id, .discreteAverage, start, end, calendar) { $0.averageQuantity()?.doubleValue(for: unit) }
    }

    private func samples(_ type: HKSampleType, _ start: Date, _ end: Date) async -> [HKSample] {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end)
        return await withCheckedContinuation { cont in
            let q = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit,
                                  sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: true)]) { _, s, _ in
                cont.resume(returning: s ?? [])
            }
            store.execute(q)
        }
    }

    static func isScale(_ source: HKSource) -> Bool {
        source.bundleIdentifier.localizedCaseInsensitiveContains("vesync") || source.name.localizedCaseInsensitiveContains("vesync")
    }

    /// The latest weigh-in of each day. When the VeSync scale wrote any, only its own samples are used.
    private func weighIns(_ start: Date, _ end: Date, _ calendar: Calendar) async -> [LocalDate: Double] {
        let all = await samples(HKQuantityType(.bodyMass), start, end).compactMap { $0 as? HKQuantitySample }
        let scale = all.filter { Self.isScale($0.sourceRevision.source) }
        var out: [LocalDate: Double] = [:]
        for s in (scale.isEmpty ? all : scale) {   // ascending, so the day's latest wins
            out[LocalDate(s.endDate, calendar: calendar)] = s.quantity.doubleValue(for: .gramUnit(with: .kilo))
        }
        return out
    }

    /// Food energy split by meal, from the meal name MyFitnessPal puts in each sample's metadata.
    private func meals(_ start: Date, _ end: Date, _ calendar: Calendar) async -> [LocalDate: Meals] {
        let food = await samples(HKQuantityType(.dietaryEnergyConsumed), start, end).compactMap { $0 as? HKQuantitySample }
        var out: [LocalDate: Meals] = [:]
        for s in food {
            let kcal = roundToInt(s.quantity.doubleValue(for: .kilocalorie()))
            let words = (s.metadata ?? [:]).values.compactMap { $0 as? String }.joined(separator: " ").lowercased()
            let day = LocalDate(s.startDate, calendar: calendar)
            var m = out[day] ?? Meals()
            if words.contains("breakfast") { m.breakfast += kcal }
            else if words.contains("lunch") { m.lunch += kcal }
            else if words.contains("dinner") { m.dinner += kcal }
            else { m.snacks += kcal }
            out[day] = m
        }
        return out
    }

    struct Night { var asleep = 0; var deep = 0; var rem = 0; var light = 0; var awake = 0; var bed: String?; var wake: String?; var staged = false }

    /// Sleep by the day the night ends on (Monday night's sleep is Tuesday's). Watch and phone can both
    /// record a night, so each night uses one source: the one with sleep stages, then the most minutes.
    private func sleep(from start: Date, end: Date, calendar: Calendar) async -> [LocalDate: Night] {
        let all = await samples(HKCategoryType(.sleepAnalysis), start.addingTimeInterval(-86_400), end).compactMap { $0 as? HKCategorySample }
        var bySourceDay: [LocalDate: [String: [HKCategorySample]]] = [:]
        for s in all {
            bySourceDay[LocalDate(s.endDate, calendar: calendar), default: [:]][s.sourceRevision.source.bundleIdentifier, default: []].append(s)
        }
        let hm = DateFormatter()
        hm.dateFormat = "HH:mm"
        hm.calendar = calendar
        hm.timeZone = calendar.timeZone
        var out: [LocalDate: Night] = [:]
        for (day, sources) in bySourceDay {
            let nights = sources.values.map { list -> Night in
                var n = Night()
                var first: Date?
                var last: Date?
                for s in list {
                    let mins = Int(s.endDate.timeIntervalSince(s.startDate) / 60)
                    guard let v = HKCategoryValueSleepAnalysis(rawValue: s.value) else { continue }
                    switch v {
                    case .asleepDeep: n.deep += mins; n.asleep += mins; n.staged = true
                    case .asleepREM: n.rem += mins; n.asleep += mins; n.staged = true
                    case .asleepCore: n.light += mins; n.asleep += mins; n.staged = true
                    case .asleepUnspecified: n.asleep += mins
                    case .awake: n.awake += mins
                    default: break
                    }
                    if v != .awake {
                        first = min(first ?? s.startDate, s.startDate)
                        last = max(last ?? s.endDate, s.endDate)
                    }
                }
                n.bed = first.map(hm.string)
                n.wake = last.map(hm.string)
                return n
            }
            let best = nights.filter { $0.asleep > 0 }.max { a, b in
                if a.staged != b.staged { return !a.staged }
                return a.asleep < b.asleep
            }
            if let best { out[day] = best }
        }
        return out
    }

    /// The newest heart-rate reading on [day] and its time.
    private func latestHeart(_ day: LocalDate, _ calendar: Calendar) async -> (Int, String)? {
        let start = day.startDate(calendar: calendar)
        let d = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: HKQuantityType(.heartRate), predicate: HKQuery.predicateForSamples(withStart: start, end: start.addingTimeInterval(86_400)))],
            sortDescriptors: [SortDescriptor(\.endDate, order: .reverse)], limit: 1)
        guard let s = try? await d.result(for: store).first else { return nil }
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return (roundToInt(s.quantity.doubleValue(for: Self.bpm)), f.string(from: s.endDate))
    }

    // MARK: - Updates

    private var observers: [HKObserverQuery] = []

    /// Calls [onChange] whenever Health gets new steps, energy, food, weight or sleep, including in the
    /// background (hourly at most) once background delivery is on.
    func observe(_ onChange: @escaping @Sendable () async -> Void) {
        guard observers.isEmpty else { return }
        for type in Self.watchedTypes {
            let q = HKObserverQuery(sampleType: type, predicate: nil) { _, completion, error in
                guard error == nil else { completion(); return }
                nonisolated(unsafe) let done = completion
                Task {
                    await onChange()
                    done()
                }
            }
            store.execute(q)
            observers.append(q)
            #if os(iOS)
            store.enableBackgroundDelivery(for: type, frequency: .hourly) { _, _ in }
            #endif
        }
    }
}
