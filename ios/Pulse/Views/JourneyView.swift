import SwiftUI

/// The page's one question: "what if I burned [kcal] more than I eat a day?". Starts at the 7-day average
/// ([pace]); the hero and every milestone follow it. Only answers; the plan never changes.
private struct Pace {
    var kcal: Int
    let pace: Int?
    var changed: Bool { pace == nil || kcal != pace }
    var faster: Bool { pace != nil && kcal > (pace ?? 0) }
    var color: Color { !changed ? C.wt : (pace == nil || faster) ? C.green : C.hot }
}

/// Each unreached milestone: the date at the chosen pace and at the 7-day pace.
private struct MRow { let m: Milestone; let at: LocalDate?; let now: LocalDate? }

struct JourneyView: View {
    let s: HomeState
    @State private var kcal: Int?

    private var stalled: Bool { s.forecast.source == .stalled }
    private var myPace: Int? { s.forecast.avgDeficit.flatMap { $0 > 0 && !stalled ? $0 : nil } }

    var body: some View {
        let pace = Pace(kcal: kcal ?? (myPace.map { min(max($0, 0), 1500) } ?? 500), pace: myPace)
        let rows = rowsFor(pace)
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Header(small: "Journey", big: "\(Words.kgShort(s.profile.startKg)) → \(Words.kgShort(s.profile.goalKg)) kg") { progress }
                hero(pace, rows)
                paceCard(pace)
                timeline(pace, rows)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .pulsePage()
    }

    private func rowsFor(_ p: Pace) -> [MRow] {
        let trend = s.trendKg ?? s.profile.startKg
        let at = Forecaster.whatIfAll(s.forecast, trend, s.today, p.kcal)
        let now = p.pace.map { Forecaster.whatIfAll(s.forecast, trend, s.today, $0) }
        return at.enumerated().map { i, pair in MRow(m: pair.0, at: pair.1, now: now?[i].1) }
    }

    @ViewBuilder private var progress: some View {
        if let t = s.trendKg {
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(Words.kgShort(max(0, s.profile.startKg - t))) kg").font(.system(size: 20, weight: .heavy, design: .rounded)).foregroundStyle(C.wt)
                Text("down · \(Words.kgShort(max(0, t - s.profile.goalKg))) to go").font(.system(size: 12, weight: .semibold)).foregroundStyle(C.mute)
            }
        }
    }

    // MARK: Goal hero

    private func hero(_ p: Pace, _ rows: [MRow]) -> some View {
        let goal = rows.last { $0.m.isGoal }
        let reached = s.forecast.goal.reachedOn
        let big: String
        if let reached { big = "Reached \(Format.date(reached, s.today))" }
        else if let at = goal?.at { big = Format.date(at, s.today) }
        else { big = "Pick a number above 0 to see dates" }
        return Card(tint: C.wt, border: C.wt.opacity(0.25)) {
            Lbl(text: p.changed && p.pace != nil ? "What if" : "At your current pace", icon: "flag.fill", color: C.wt2)
            Text("Goal \(Words.kgShort(s.profile.goalKg)) kg on")
                .font(.system(size: 15, weight: .semibold)).foregroundStyle(C.soft).padding(.top, 10)
            Text(big)
                .font(.metric(goal?.at != nil || reached != nil ? 42 : 20))
                .foregroundStyle(C.weight)
                .shadow(color: C.wt.opacity(0.45), radius: 16)
                .contentTransition(.numericText())
                .animation(.snappy, value: big)
                .accessibilityIdentifier("whatif_result")
            if let at = goal?.at, let now = goal?.now, at != now {
                HStack(spacing: 8) {
                    Text(Format.date(now, s.today)).strikethrough().font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute)
                    DeltaPill(text: Words.sooner(at, now), sooner: at.isBefore(now))
                }
                .padding(.top, 4)
            }
            Text(heroLine(p))
                .font(.system(size: 13.5, weight: .medium)).foregroundStyle(C.mute)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 10)
            if let line = s.journeyLine, !p.changed {
                Text(line).font(.system(size: 13.5, weight: .semibold)).foregroundStyle(C.green).padding(.top, 6)
            }
        }
    }

    private func heroLine(_ p: Pace) -> String {
        let week = { (k: Int) in String(format: "%.2f", Double(k) / Energy.kcalPerKg * 7) }
        if s.trendKg == nil { return "Weigh in a few mornings and your dates sharpen. Until then they use your start weight." }
        if stalled && !p.changed { return "Burn more than you eat for a few days to put dates on the calendar." }
        if stalled { return "Burn more than you eat for a few days to put dates on the calendar. Meanwhile: \(Format.int(p.kcal)) kcal a day more burned is \(week(p.kcal)) kg a week." }
        guard let pace = p.pace else { return "No full day of food yet. \(Format.int(p.kcal)) kcal a day more burned than eaten is \(week(p.kcal)) kg a week." }
        if p.changed { return "Burning \(Format.int(p.kcal)) kcal a day more than you eat is \(week(p.kcal)) kg a week. Your plan doesn't change." }
        return "Your last 7 days: \(Format.int(pace)) kcal a day burned more than eaten, about \(week(pace)) kg a week."
    }

    // MARK: Pace control

    private func paceCard(_ p: Pace) -> some View {
        Card {
            Lbl(text: "Try a different pace", icon: "slider.horizontal.3", color: C.mute)
            Text("If I burn \(Text("\(Format.int(p.kcal)) kcal more").foregroundStyle(p.changed ? p.color : C.wt).fontWeight(.heavy)) than I eat a day")
                .font(.system(size: 16, weight: .semibold)).foregroundStyle(C.ink)
                .padding(.top, 10)
            Slider(
                value: Binding(get: { Double(p.kcal) }, set: { kcal = Int(($0 / 50).rounded()) * 50 }),
                in: 0...1500, step: 50
            )
            .tint(p.changed ? p.color : C.wt)
            .padding(.top, 6)
            .accessibilityIdentifier("whatif_slider")
            HStack {
                Text("Slower"); Spacer(); Text("Faster")
            }
            .font(.system(size: 12, weight: .semibold)).foregroundStyle(C.mute)
            let chips: [(Int, String)] = [(500, "500")] + (p.pace.map { [($0, "My pace")] } ?? []) + [(1000, "1,000"), (1250, "1,250")]
            HStack(spacing: 8) {
                ForEach(chips, id: \.1) { v, label in
                    let on = p.kcal == v
                    Button { withAnimation(.snappy) { kcal = v } } label: {
                        Text(label)
                            .font(.system(size: 13, weight: .heavy))
                            .foregroundStyle(on ? C.bg : C.soft)
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .background(on ? C.ink : C.s2, in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(on ? C.ink : C.line))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 12)
            Text(p.pace.map { "Your 7-day average: \(Format.int($0)) kcal a day more burned than eaten" } ?? "Your 7-day average appears after a full day of food")
                .font(.system(size: 12, weight: .medium)).foregroundStyle(C.dim).padding(.top, 10)
        }
    }

    // MARK: Milestone timeline

    private func timeline(_ p: Pace, _ rows: [MRow]) -> some View {
        let f = s.forecast
        let trend = s.trendKg ?? s.profile.startKg
        var aheadIndex = 0
        var items: [(Int, Milestone, MRow?)] = []
        for (i, m) in f.milestones.enumerated() {
            if m.reachedOn != nil { items.append((i, m, nil)) } else { items.append((i, m, rows.indices.contains(aheadIndex) ? rows[aheadIndex] : nil)); aheadIndex += 1 }
        }
        return Card {
            HStack {
                Lbl(text: "Milestones", icon: "chart.line.downtrend.xyaxis", color: C.wt)
                Spacer()
                Text(p.changed && p.pace != nil ? "vs your current pace" : "at your 7-day pace")
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(C.mute)
            }
            .padding(.bottom, 10)
            VStack(spacing: 0) {
                ForEach(items, id: \.0) { i, m, row in
                    let prevKg = i > 0 ? f.milestones[i - 1].kg : s.profile.startKg
                    TimelineRow(
                        m: m, row: row, isNext: m == f.next,
                        first: i == 0, last: i == f.milestones.count - 1,
                        prevDone: i > 0 && f.milestones[i - 1].reachedOn != nil,
                        progress: m == f.next ? ((prevKg - trend) / (prevKg - m.kg)).clamped(0, 1) : 0,
                        toGo: max(0, trend - m.kg),
                        shift: m == f.next && !p.changed ? s.shiftDays : nil,
                        paused: stalled && !p.changed,
                        today: s.today)
                }
            }
            .accessibilityIdentifier("milestone_list")
        }
    }
}

/// One milestone with the line and dot on the left. Each row draws its own segments of one continuous line.
private struct TimelineRow: View {
    let m: Milestone
    let row: MRow?
    let isNext: Bool
    let first: Bool
    let last: Bool
    let prevDone: Bool
    let progress: Double
    let toGo: Double
    let shift: Int?
    let paused: Bool
    let today: LocalDate

    private let dotY: CGFloat = 22

    var body: some View {
        let done = m.reachedOn != nil
        let big = isNext || m.isGoal
        let at = row?.at
        let now = row?.now
        let moved = at != nil && now != nil && at != now
        let sooner = at != nil && now != nil && at!.isBefore(now!)
        HStack(alignment: .top, spacing: 14) {
            // The line: top half joins the row above, bottom half the row below, so it never breaks.
            ZStack(alignment: .top) {
                VStack(spacing: 0) {
                    Rectangle().fill(first ? .clear : (done || prevDone ? C.green : C.line)).frame(width: 2, height: dotY)
                    Rectangle().fill(last ? .clear : (done ? C.green : C.line)).frame(width: 2).frame(maxHeight: .infinity)
                }
                dot(done: done)
                    .frame(height: 0)
                    .offset(y: dotY)
            }
            .frame(width: 24)

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(m.label)
                        .font(.system(size: big ? 17 : 15, weight: big ? .heavy : .semibold, design: .rounded))
                        .foregroundStyle(done ? C.mute : C.ink).lineLimit(1)
                    Spacer(minLength: 8)
                    dateText(done: done, at: at, moved: moved, sooner: sooner)
                }
                HStack(spacing: 6) {
                    Text("\(Words.kgShort(m.kg)) kg").font(.system(size: 12, weight: .semibold)).foregroundStyle(C.dim)
                    Spacer()
                    if moved, let at, let now, !paused {
                        Text(Format.date(now, today)).strikethrough().font(.system(size: 12, weight: .medium)).foregroundStyle(C.dim)
                        DeltaPill(text: Words.sooner(at, now), sooner: sooner)
                    }
                }
                if isNext && !done {
                    GlowBar(fraction: max(progress, 0.03), colors: [C.wt, C.wt2], height: 6, glow: true).padding(.top, 8)
                    Text("Next up · \(Words.kgShort(toGo)) kg to go" + (shift.flatMap { $0 == 0 ? nil : " · \(Words.shift($0))" } ?? ""))
                        .font(.system(size: 12.5, weight: .semibold)).foregroundStyle(C.soft).padding(.top, 5)
                }
            }
            .padding(.top, 12).padding(.bottom, 14)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    @ViewBuilder private func dot(done: Bool) -> some View {
        if done {
            Circle().fill(C.green).frame(width: 12, height: 12).shadow(color: C.green, radius: 6)
        } else if isNext {
            Circle().fill(.white).frame(width: 16, height: 16)
                .overlay(Circle().strokeBorder(C.wt.opacity(0.6), lineWidth: 3))
                .shadow(color: C.wt, radius: 10)
        } else if m.isGoal {
            Circle().fill(C.wt2).frame(width: 16, height: 16).shadow(color: C.wt2, radius: 9)
        } else {
            Circle().fill(C.restingBar).frame(width: 12, height: 12)
        }
    }

    private func dateText(done: Bool, at: LocalDate?, moved: Bool, sooner: Bool) -> some View {
        let (text, color): (String, Color) = {
            if done { return ("Reached \(Format.date(m.reachedOn!, today))", C.green) }
            guard let at, !paused else { return ("—", C.dim) }
            if moved { return (Format.date(at, today), sooner ? C.green : C.hot) }
            return (Format.date(at, today), m.isGoal ? C.wt2 : C.soft)
        }()
        return Text(text).font(.system(size: m.isGoal ? 17 : 14, weight: .heavy, design: .rounded)).foregroundStyle(color).lineLimit(1)
    }
}
