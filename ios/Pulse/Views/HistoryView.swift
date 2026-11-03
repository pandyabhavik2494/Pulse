import SwiftUI

/// Past days, newest first, with the balance of each.
struct HistoryView: View {
    let s: HomeState
    @State private var open: DayLog?

    var body: some View {
        let days = s.history.prefix(60)
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Header(small: "History", big: "Past days") { EmptyView() }
                weekSummary
                VStack(spacing: 0) {
                    HStack {
                        Text("DAY").frame(width: 58, alignment: .leading)
                        Text("BALANCE")
                        Spacer()
                        Text("STEPS").frame(width: 64, alignment: .trailing)
                        Text("KG").frame(width: 52, alignment: .trailing)
                    }
                    .font(.system(size: 11, weight: .heavy)).tracking(1).foregroundStyle(C.dim)
                    .padding(.bottom, 8)
                    Rectangle().fill(C.line).frame(height: 1)
                    ForEach(Array(days), id: \.date) { d in
                        Button { open = d } label: { row(d) }.buttonStyle(.plain)
                        Rectangle().fill(C.line).frame(height: 1)
                    }
                }
                .accessibilityIdentifier("history_list")
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .pulsePage()
        .sheet(item: $open) { d in
            ScrollView { DayDetail(d: d, today: s.today).padding(20) }.pulseSheet()
        }
    }

    private var weekSummary: some View {
        let steps = s.history.prefix(7).compactMap(\.steps)
        return Card {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Lbl(text: "7-day average")
                    if let a = s.avg7 {
                        HStack(alignment: .lastTextBaseline, spacing: 6) {
                            Text(Format.int(abs(a))).font(.metric(30)).foregroundStyle(a >= 0 ? C.green : C.grow)
                                .shadow(color: (a >= 0 ? C.green : C.grow).opacity(0.5), radius: 10)
                            Text(a >= 0 ? "kcal a day more burned" : "kcal a day more eaten")
                                .font(.system(size: 13, weight: .semibold)).foregroundStyle(C.soft)
                        }
                    } else {
                        Text("Log food for a day to see it").font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 6) {
                    Text("STEPS").font(.system(size: 12, weight: .heavy)).tracking(1).foregroundStyle(C.mute)
                    Text(steps.isEmpty ? "—" : Format.int(steps.reduce(0, +) / steps.count))
                        .font(.metric(22)).foregroundStyle(C.steps)
                }
            }
        }
    }

    private func row(_ d: DayLog) -> some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(d.date == s.today ? "Today" : Weekday.short[d.date.dayOfWeek - 1]).font(.system(size: 12, weight: .semibold)).foregroundStyle(C.mute)
                Text("\(d.date.dayOfMonth) \(Format.months[d.date.month - 1])").font(.system(size: 15, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
            }
            .frame(width: 58, alignment: .leading)
            VStack(alignment: .leading, spacing: 5) {
                if let b = d.balance {
                    Text(Words.balanceShort(b)).font(.system(size: 13.5, weight: .bold)).foregroundStyle(b >= 0 ? C.green : C.grow)
                    GlowBar(fraction: min(1, Double(abs(b)) / 1000), colors: [b >= 0 ? C.green : C.grow], height: 5, glow: abs(b) > 600)
                } else {
                    Text(d.date == s.today ? "so far: —" : "no food logged").font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute)
                }
            }
            .padding(.trailing, 10)
            Spacer(minLength: 0)
            Text(d.steps.map(Format.int) ?? "—").font(.system(size: 13.5, weight: .bold, design: .rounded)).foregroundStyle(C.steps).frame(width: 64, alignment: .trailing)
            Text(d.weightKg.map { Words.kg1($0) } ?? "—").font(.system(size: 13.5, weight: .bold, design: .rounded)).foregroundStyle(C.ink).frame(width: 52, alignment: .trailing)
        }
        .padding(.vertical, 11)
        .contentShape(Rectangle())
    }
}

extension DayLog: Identifiable { var id: Int { date.epochDay } }

/// Everything about one past day.
struct DayDetail: View {
    let d: DayLog
    let today: LocalDate

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(d.date == today ? "Today" : "\(Weekday.full[d.date.dayOfWeek - 1]), \(Format.date(d.date, today))")
                .font(.system(size: 22, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
            if let b = d.balance {
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text(Format.int(abs(b))).font(.metric(40)).foregroundStyle(b >= 0 ? C.green : C.grow)
                    Text(Words.balance(eatenMore: b < 0)).font(.system(size: 15, weight: .bold)).foregroundStyle(b >= 0 ? C.green : C.grow)
                }
                .padding(.vertical, 10)
            }
            FieldRow(label: "Burned", value: d.burnedTotal.map { "\(Format.int($0)) kcal" } ?? "—")
            FieldRow(label: "Eaten", value: d.eaten.map { "\(Format.int($0)) kcal" } ?? "nothing logged")
            FieldRow(label: "Steps", value: d.steps.map(Format.int) ?? "—", valueColor: C.steps)
            FieldRow(label: "Weight", value: d.weightKg.map { "\(Words.kg1($0)) kg" } ?? "—", valueColor: C.wt)
            FieldRow(label: "Sleep", value: d.sleepMinutes.map(Words.hm) ?? "—")
            FieldRow(label: "Resting heart", value: d.restingHr.map { "\($0) bpm" } ?? "—", divider: false)
        }
    }
}
