import SwiftUI

/// A Today section opened from a tile: its icon and title, the big number, then the details.
struct SectionView: View {
    let area: Area
    let s: HomeState

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: area.icon)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(area.color)
                    .frame(width: 34, height: 34)
                    .background(area.color.opacity(0.15), in: Circle())
                    .shadow(color: area.color.opacity(0.5), radius: 8)
                Text(area.title).font(.system(size: 20, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
                Spacer()
            }
            .padding(.top, 8)
            switch area {
            case .calories: CaloriesSection(s: s)
            case .activity: ActivitySection(s: s)
            case .body: BodySection(s: s)
            case .heart: HeartSection(s: s)
            case .sleep: SleepSection(s: s)
            }
        }
        .accessibilityIdentifier("section_\(area.rawValue)")
    }
}

/// The section's main number with its unit beside it.
private struct BigValue: View {
    let value: String
    let unit: String?
    var color: Color = C.ink

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 8) {
            Text(value).font(.metric(44)).foregroundStyle(color)
                .shadow(color: color == C.ink ? .clear : color.opacity(0.45), radius: 14)
                .lineLimit(1).minimumScaleFactor(0.6)
            if let unit {
                Text(unit).font(.system(size: 16, weight: .bold)).foregroundStyle(color == C.ink ? C.mute : color).lineLimit(1)
            }
        }
        .padding(.top, 14).padding(.bottom, 6)
    }
}

private struct EmptyLine: View {
    let title: String
    let detail: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 19, weight: .bold, design: .rounded)).foregroundStyle(C.ink)
            Text(detail).font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute)
        }
        .padding(.top, 14).padding(.bottom, 6)
    }
}

/// A soft inner block: caps title, a big coloured number with words, and one explaining line.
private struct InfoBlock: View {
    let title: String
    var badge: String? = nil
    let value: String
    let words: String
    let color: Color
    let line: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title).font(.system(size: 12, weight: .heavy)).tracking(1).foregroundStyle(C.mute)
                Spacer()
                if let badge { Text(badge).font(.system(size: 11, weight: .heavy)).tracking(1).foregroundStyle(C.food) }
            }
            HStack(alignment: .lastTextBaseline, spacing: 8) {
                Text(value).font(.metric(26)).foregroundStyle(color).lineLimit(1)
                Text(words).font(.system(size: 15, weight: .semibold)).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.8)
            }
            Text(line).font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute).fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(C.s2, in: RoundedRectangle(cornerRadius: 16))
        .padding(.vertical, 8)
    }
}

private func avgOrNil(_ v: [Int?]) -> Int? {
    let x = v.compactMap { $0 }
    return x.isEmpty ? nil : roundToInt(x.average)
}

private func whenNote(_ d: LocalDate, _ today: LocalDate) -> String? { d == today ? nil : "(\(Format.date(d, today)))" }

// MARK: - Calories

private struct CaloriesSection: View {
    let s: HomeState

    var body: some View {
        let d = s.day
        let color = s.eatenMore ? C.grow : C.green
        if let b = s.balance {
            BigValue(value: Format.int(abs(b)), unit: Words.balance(eatenMore: s.eatenMore), color: color)
            let burned = s.burned ?? 0
            GlowBar(fraction: burned > 0 ? Double(s.eaten ?? 0) / Double(burned) : 0, colors: [s.eatenMore ? C.grow : C.food], height: 8)
            Text(s.eatenMore ? Motivation.spillLine(-b, s.avg7) : "Eaten as a share of burned. The empty part is what you burned more than you ate.")
                .font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute).padding(.top, 6)
        } else {
            EmptyLine(title: s.burned == nil ? "Waiting for Apple Health" : "No food logged yet",
                      detail: s.burned == nil ? "Burned calories appear when your watch syncs to Health." : "Your balance shows once MyFitnessPal logs a meal.")
        }
        let budget = s.budget
        let past = budget.left < 0
        InfoBlock(
            title: "LEFT TO EAT TODAY",
            value: Format.int(abs(budget.left)),
            words: past ? "kcal past today’s plan" : "kcal to stay on pace",
            color: past ? C.grow : C.green,
            line: "Plan \(Format.int(budget.budget)) kcal: " +
                (budget.burnDays > 0 ? "you usually burn \(Format.int(budget.burn)) kcal a day" : "you burn about \(Format.int(budget.burn)) kcal a day (estimated until Health has a full day)") +
                ", minus \(Format.int(Budget.paceKcal)) to lose 0.75 kg a week. Eaten \(Format.int(budget.eaten)).")
        FieldRow(label: "Burned", value: s.burned.map { "\(Format.int($0)) kcal" } ?? "—", note: s.active.map { "(\(Format.int($0)) active)" })
        let meals = mealRows(d)
        FieldRow(label: "Eaten", value: s.eaten.map { "\(Format.int($0)) kcal" } ?? "nothing yet", valueColor: s.eaten == nil ? C.mute : C.ink,
                 note: s.eaten != nil && !meals.isEmpty ? "(\(meals.count) meal\(meals.count == 1 ? "" : "s"))" : nil)
        if d.carbsG != nil || d.fatG != nil || d.proteinG != nil {
            HStack(spacing: 8) {
                macro(d.carbsG, "Carbs", C.food)
                macro(d.fatG, "Fat", C.wt2)
                macro(d.proteinG, "Protein", C.steps)
            }
            .padding(.vertical, 8)
        }
        ForEach(meals, id: \.0) { name, kcal in FieldRow(label: name, value: "\(Format.int(kcal)) kcal", dot: C.food) }
        if let v = d.fiberG { FieldRow(label: "Fibre", value: "\(v) g") }
        if let v = d.sugarG { FieldRow(label: "Sugar", value: "\(v) g") }
        if let v = d.sodiumMg { FieldRow(label: "Sodium", value: "\(Format.int(v)) mg") }
        if let v = d.waterMl { FieldRow(label: "Water", value: v >= 1000 ? String(format: "%.1f L", Double(v) / 1000) : "\(v) ml", valueColor: C.steps) }
        FieldRow(label: "7-day average", value: s.avg7.map { "\(Words.balanceShort($0)) a day" } ?? "—",
                 valueColor: s.avg7 == nil ? C.mute : s.avg7! >= 0 ? C.green : C.grow)
        FieldRow(label: "Burned more than eaten",
                 value: s.streak == 0 ? "Start a streak today" : s.streak == 1 ? "1 day" : "\(s.streak) days in a row",
                 valueColor: s.streak > 0 ? C.hot : C.mute, divider: false)
    }

    private func mealRows(_ d: DayLog) -> [(String, Int)] {
        [("Breakfast", d.meals.breakfast), ("Lunch", d.meals.lunch), ("Snacks", d.meals.snacks), ("Dinner", d.meals.dinner)].filter { $0.1 > 0 }
    }

    private func macro(_ g: Int?, _ label: String, _ c: Color) -> some View {
        VStack(spacing: 2) {
            Text(g.map { "\($0) g" } ?? "—").font(.metric(19)).foregroundStyle(c)
            Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(C.mute)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10)
        .background(C.s2, in: RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Activity

private struct ActivitySection: View {
    let s: HomeState

    var body: some View {
        let goal = s.profile.stepGoal
        let d = s.day
        BigValue(value: Format.int(s.steps), unit: "steps of \(Format.int(goal))")
        GlowBar(fraction: Double(s.steps) / Double(max(goal, 1)), colors: [C.steps, C.green], height: 8, glow: true)
            .padding(.bottom, 6)
        let left = goal - s.steps
        FieldRow(label: "To go", value: left > 0 ? "\(Format.int(left)) steps" : "Goal done", valueColor: left > 0 ? C.ink : C.green)
        FieldRow(label: "Distance", value: d.distanceM.map { String(format: "%.2f km", Double($0) / 1000) } ?? "—",
                 valueColor: d.distanceM == nil ? C.mute : C.ink, note: d.distanceM.map { String(format: "(%.1f mi)", Double($0) / 1609.344) })
        if let f = d.floors { FieldRow(label: "Floors climbed", value: "\(f)") }
        if let e = d.exerciseMin { FieldRow(label: "Exercise", value: e >= 60 ? "\(e / 60)h \(e % 60)m" : "\(e) min") }
        if let a = s.active { FieldRow(label: "Active calories", value: "\(Format.int(a)) kcal") }
        let avg = avgOrNil(s.history.dropFirst().prefix(7).map(\.steps))
        FieldRow(label: "7-day average", value: avg.map { "\(Format.int($0)) steps" } ?? "—", valueColor: avg == nil ? C.mute : C.ink, divider: false)
    }
}

// MARK: - Body

private struct BodySection: View {
    let s: HomeState

    var body: some View {
        let p = s.profile
        let last = s.history.first { $0.weightKg != nil }
        if let last, let kg = last.weightKg {
            BigValue(value: Format.kg(kg), unit: "kg \(last.date == s.today ? "today" : Format.date(last.date, s.today))", color: C.wt)
            if let t = s.trendKg {
                Text("Trend \(Format.kg(t)) kg").font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute)
            }
            let change = kg - p.startKg
            Text("\(Words.signedKg(change)) kg since \(Format.date(p.startDate, s.today))")
                .font(.system(size: 13, weight: .semibold)).foregroundStyle(change <= 0 ? C.green : C.mute)
                .padding(.top, 2).padding(.bottom, 8)
            GlowBar(fraction: (p.startKg - kg) / max(0.1, p.startKg - p.goalKg), colors: [C.wt, C.wt2], height: 8, glow: true)
            HStack {
                Text("\(Words.kgShort(p.startKg)) kg")
                Spacer()
                Text("Goal \(Words.kgShort(p.goalKg)) kg")
            }
            .font(.system(size: 12, weight: .semibold)).foregroundStyle(C.mute).padding(.top, 5)
        } else {
            EmptyLine(title: "No weigh-in yet", detail: "Step on your VeSync scale and your weight shows here.")
        }
        TwoWeekBlock(s: s)
        if let kg = last?.weightKg, p.heightCm > 0 {
            let m = p.heightCm / 100
            FieldRow(label: "BMI", value: String(format: "%.1f", kg / (m * m)))
        }
        if let (v, d) = s.latest({ $0.bodyFatPct }) { FieldRow(label: "Body fat", value: String(format: "%.1f%%", v), note: whenNote(d, s.today)) }
        if let (v, d) = s.latest({ $0.leanKg }) { FieldRow(label: "Lean mass", value: String(format: "%.1f kg", v), note: whenNote(d, s.today)) }
        let f = s.forecast
        FieldRow(label: "Next milestone",
                 value: f.next.map { "\(Words.kgShort($0.kg)) kg · \($0.projected.map { Format.date($0, s.today) } ?? "no date yet")" } ?? "Goal reached",
                 valueColor: C.wt)
        FieldRow(label: "Goal \(Words.kgShort(p.goalKg)) kg",
                 value: f.next == nil ? "Reached" : f.goal.projected.map { Format.date($0, s.today) } ?? "no date yet",
                 divider: false)
    }
}

/// Where the last 7 days of eating take the weight in two weeks, then what the trend did in each recent stretch.
private struct TwoWeekBlock: View {
    let s: HomeState

    var body: some View {
        let o = TwoWeeks.outlook(s.profile, s.history, s.today)
        let gain = o.changeKg > 0.05
        let avg = o.avgBalance
        let why: String = {
            switch o.basis {
            case .foodAndScale, .food:
                let eating = (avg ?? 0) >= 0 ? "burning \(Format.int(avg ?? 0)) kcal a day more than you eat" : "eating \(Format.int(-(avg ?? 0))) kcal a day more than you burn"
                let days = o.foodDays == 1 ? "yesterday" : "the last \(o.foodDays) days"
                return "If you keep \(eating), like \(days)." + (o.basis == .foodAndScale ? " Checked against your scale." : " Gets sharper with each day of food logged.")
            case .scale: return "From your weigh-ins so far. Log food in MyFitnessPal and it follows what you eat."
            case .today: return "From today so far. Tomorrow it uses a full day of food."
            case .startingGuess: return "A starting estimate at the plan's pace (0.75 kg a week). It switches to your real eating after a full day of food."
            }
        }()
        InfoBlock(
            title: "YOUR WEIGHT IN 2 WEEKS",
            badge: o.early ? "EARLY ESTIMATE" : nil,
            value: "\(Format.kg(o.toKg)) kg",
            words: o.changeKg <= -0.05 ? "\(Format.kg(-o.changeKg)) kg lighter" : gain ? "\(Format.kg(o.changeKg)) kg heavier" : "about the same",
            color: gain ? C.grow : C.green,
            line: why)
        let periods = TwoWeeks.periods(s.history, s.today)
        if periods.isEmpty {
            FieldRow(label: "Last 2 weeks", value: "after 2 weigh-ins", valueColor: C.mute)
        }
        ForEach(Array(periods.enumerated()), id: \.offset) { i, per in
            let label = i == 0 && per.days < TwoWeeks.days ? "Last \(Format.days(per.days))" : i == 0 ? "Last 2 weeks" : "\(Format.date(per.start, s.today)) to \(Format.date(per.end, s.today))"
            FieldRow(label: label,
                     value: per.changeKg <= -0.05 ? "\(Format.kg(-per.changeKg)) kg lost" : per.changeKg >= 0.05 ? "\(Format.kg(per.changeKg)) kg gained" : "steady",
                     valueColor: per.changeKg >= 0.05 ? C.grow : C.green)
        }
    }
}

// MARK: - Heart

private struct HeartSection: View {
    let s: HomeState

    var body: some View {
        let d = s.day
        let resting = s.history.prefix(3).first { $0.restingHr != nil }
        if let l = d.hrLatest {
            BigValue(value: "\(l)", unit: "bpm at \(d.hrLatestAt ?? "—")")
        } else if let r = resting?.restingHr {
            BigValue(value: "\(r)", unit: "bpm resting")
        } else if let a = d.hrAvg {
            BigValue(value: "\(a)", unit: "bpm average")
        } else {
            EmptyLine(title: "No heart data yet", detail: "It appears here when your Apple Watch syncs to Health.")
        }
        if let st = Moods.resting(resting?.restingHr, Array(s.history.compactMap(\.restingHr).dropFirst().prefix(30))) {
            HStack(spacing: 8) {
                StatusTag(status: st)
                Text(st.why).font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute)
            }
            .padding(.bottom, 6)
        }
        FieldRow(label: "Resting", value: resting?.restingHr.map { "\($0) bpm" } ?? "—", valueColor: resting == nil ? C.mute : C.ink,
                 note: resting.flatMap { whenNote($0.date, s.today) })
        FieldRow(label: "Average today", value: d.hrAvg.map { "\($0) bpm" } ?? "—", valueColor: d.hrAvg == nil ? C.mute : C.ink)
        FieldRow(label: "Range today", value: d.hrMin != nil && d.hrMax != nil ? "\(d.hrMin!)–\(d.hrMax!) bpm" : "—", valueColor: d.hrMin == nil ? C.mute : C.ink)
        if let (v, w) = s.latest({ $0.hrvMs }) { FieldRow(label: "HRV", value: "\(v) ms", note: whenNote(w, s.today)) }
        if let (v, w) = s.latest({ $0.spo2Pct }) { FieldRow(label: "Blood oxygen", value: String(format: "%.0f%%", v), note: whenNote(w, s.today)) }
        if let (v, w) = s.latest({ $0.breathingRate }) { FieldRow(label: "Breathing rate", value: String(format: "%.1f /min", v), note: whenNote(w, s.today)) }
        if let (v, w) = s.latest({ $0.vo2Max }) { FieldRow(label: "VO2 max", value: String(format: "%.1f", v), note: whenNote(w, s.today), divider: false) }
    }
}

// MARK: - Sleep

private struct SleepSection: View {
    let s: HomeState

    var body: some View {
        let d = s.day
        if let night = d.sleepMinutes {
            BigValue(value: Words.hm(night), unit: "last night")
            if let b = d.bedTime, let w = d.wakeTime {
                Text("\(b) to \(w)").font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute).padding(.bottom, 8)
            }
            let deep = d.sleepDeepMin ?? 0, rem = d.sleepRemMin ?? 0, light = d.sleepLightMin ?? 0, awake = d.sleepAwakeMin ?? 0
            if deep + rem + light > 0 {
                StageBar(parts: [(deep, C.wt), (rem, C.steps), (light, C.sleep), (awake, C.dim)])
                    .padding(.bottom, 4)
                FieldRow(label: "Deep", value: Words.hm(deep), dot: C.wt)
                FieldRow(label: "REM", value: Words.hm(rem), dot: C.steps)
                FieldRow(label: "Light", value: Words.hm(light), dot: C.sleep)
                FieldRow(label: "Awake", value: Words.hm(awake), dot: C.dim)
            } else {
                GlowBar(fraction: Double(night) / 480, colors: [C.sleep], height: 8)
            }
        } else {
            EmptyLine(title: "No sleep recorded last night", detail: "Wear your Apple Watch to bed and it shows here in the morning.")
        }
        let avg = avgOrNil(s.history.prefix(7).map(\.sleepMinutes))
        FieldRow(label: "7-night average", value: avg.map(Words.hm) ?? "—", valueColor: avg == nil ? C.mute : C.ink, divider: false)
    }
}

private struct StageBar: View {
    let parts: [(Int, Color)]
    var body: some View {
        let total = max(1, parts.reduce(0) { $0 + $1.0 })
        GeometryReader { g in
            HStack(spacing: 2) {
                ForEach(Array(parts.enumerated()), id: \.offset) { _, p in
                    if p.0 > 0 { Rectangle().fill(p.1).frame(width: max(2, (g.size.width - 6) * Double(p.0) / Double(total))) }
                }
            }
        }
        .frame(height: 12)
        .clipShape(Capsule())
    }
}
