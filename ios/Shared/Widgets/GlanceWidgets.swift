import SwiftUI
import WidgetKit

/// Timeline of the glance the app (or watch app) last wrote. Refreshed by the app after each Health read.
struct GlanceProvider: TimelineProvider {
    func placeholder(in context: Context) -> GlanceEntry { GlanceEntry(date: Date(), snap: .preview) }

    func getSnapshot(in context: Context, completion: @escaping (GlanceEntry) -> Void) {
        completion(GlanceEntry(date: Date(), snap: context.isPreview ? .preview : (Snapshot.load() ?? .preview)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<GlanceEntry>) -> Void) {
        let entry = GlanceEntry(date: Date(), snap: Snapshot.load())
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(30 * 60))))
    }
}

struct GlanceEntry: TimelineEntry {
    let date: Date
    let snap: Snapshot?

    /// Today's glance, or nil when it's from an earlier day (a fresh day starts empty).
    var today: Snapshot? { snap.flatMap { $0.day == LocalDate(date) ? $0 : nil } }
}

private func balanceColor(_ s: Snapshot) -> Color { (s.balance ?? 0) < 0 ? C.grow : C.green }

// MARK: - Views

struct BalanceGlance: View {
    @Environment(\.widgetFamily) private var family
    let entry: GlanceEntry

    var body: some View {
        let s = entry.today
        switch family {
        case .accessoryInline:
            if let b = s?.balance {
                Text("\(Format.int(abs(b))) \(b < 0 ? "more eaten" : "more burned")")
            } else {
                Text("Pulse · log food to see balance")
            }
        case .accessoryCircular:
            Gauge(value: s.map { Double(max(0, $0.balance ?? 0)) } ?? 0, in: 0...Double(max(1, Budget.paceKcal))) {
                Image(systemName: "flame.fill")
            } currentValueLabel: {
                Text(s?.balance.map { Format.int(abs($0)) } ?? "—").font(.system(size: 13, weight: .heavy, design: .rounded))
            }
            .gaugeStyle(.accessoryCircular)
            .tint(s.map(balanceColor) ?? C.mute)
            .widgetAccentable()
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Label("Balance", systemImage: "flame.fill").font(.system(size: 12, weight: .bold)).widgetAccentable()
                Text(s?.balance.map { Format.int(abs($0)) } ?? "—").font(.system(size: 22, weight: .heavy, design: .rounded))
                Text(s?.balance.map { Words.balance(eatenMore: $0 < 0) } ?? "log food to see it").font(.system(size: 12)).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        #if os(watchOS)
        case .accessoryCorner:
            Text(s?.balance.map { Format.int(abs($0)) } ?? "—")
                .font(.system(size: 18, weight: .heavy, design: .rounded))
                .widgetLabel { Text(s?.balance.map { $0 < 0 ? "more eaten" : "more burned" } ?? "balance") }
        #endif
        default:
            #if os(iOS)
            HomeGlance(s: s)
            #else
            EmptyView()
            #endif
        }
    }
}

struct LeftGlance: View {
    @Environment(\.widgetFamily) private var family
    let entry: GlanceEntry

    var body: some View {
        let s = entry.today ?? entry.snap.map(emptied)
        let left = s?.left ?? 0
        let budget = max(1, s?.budget ?? 1)
        switch family {
        case .accessoryInline:
            Text(left >= 0 ? "\(Format.int(left)) kcal left to eat" : "Tomorrow is a fresh budget")
        case .accessoryCircular:
            Gauge(value: Double(max(0, left)), in: 0...Double(budget)) {
                Image(systemName: "fork.knife")
            } currentValueLabel: {
                Text(left >= 0 ? Format.int(left) : "0").font(.system(size: 13, weight: .heavy, design: .rounded))
            }
            .gaugeStyle(.accessoryCircular)
            .tint(left >= 200 ? C.green : left >= 0 ? C.steady : C.grow)
            .widgetAccentable()
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Label("Left to eat", systemImage: "fork.knife").font(.system(size: 12, weight: .bold)).widgetAccentable()
                Text(Format.int(abs(left))).font(.system(size: 22, weight: .heavy, design: .rounded))
                Text(left >= 0 ? "kcal to stay on pace" : "kcal past today’s plan").font(.system(size: 12)).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        #if os(watchOS)
        case .accessoryCorner:
            Text(Format.int(max(0, left)))
                .font(.system(size: 18, weight: .heavy, design: .rounded))
                .widgetLabel { Gauge(value: Double(max(0, left)), in: 0...Double(budget)) { Text("left") }.tint(C.green) }
        #endif
        default:
            #if os(iOS)
            HomeGlance(s: entry.today)
            #else
            EmptyView()
            #endif
        }
    }

    /// Yesterday's glance carried into a new day: full budget, nothing eaten yet.
    private func emptied(_ s: Snapshot) -> Snapshot {
        var x = s
        x.left = s.budget
        x.balance = nil
        x.eaten = nil
        return x
    }
}

#if os(iOS)
/// Home screen: balance big, then left to eat and steps, and the verdict headline.
struct HomeGlance: View {
    @Environment(\.widgetFamily) private var family
    let s: Snapshot?

    var body: some View {
        if let s {
            if family == .systemSmall { small(s) } else { medium(s) }
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Label("Pulse", systemImage: "waveform.path.ecg").font(.system(size: 13, weight: .heavy)).foregroundStyle(C.green)
                Spacer()
                Text("A fresh day").font(.system(size: 20, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
                Text("Open Pulse to read today").font(.system(size: 12, weight: .medium)).foregroundStyle(C.mute)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func balanceBlock(_ s: Snapshot, size: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(s.balance.map { Format.int(abs($0)) } ?? "—")
                .font(.metric(size)).foregroundStyle(s.balance == nil ? C.mute : balanceColor(s))
                .shadow(color: balanceColor(s).opacity(0.5), radius: 8)
                .minimumScaleFactor(0.6).lineLimit(1)
            Text(s.balance.map { Words.balance(eatenMore: $0 < 0) } ?? "log food to see balance")
                .font(.system(size: 12, weight: .semibold)).foregroundStyle(C.soft).lineLimit(2)
        }
    }

    private func headline(_ s: Snapshot) -> some View {
        HStack(spacing: 6) {
            Circle().fill(s.headlineGrow ? C.steady : C.green).frame(width: 7, height: 7)
            Text(s.headline).font(.system(size: 12.5, weight: .heavy)).foregroundStyle(s.headlineGrow ? C.steady : C.green).lineLimit(1)
        }
    }

    private func small(_ s: Snapshot) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            headline(s)
            Spacer(minLength: 0)
            balanceBlock(s, size: 34)
            Text("\(Format.int(max(0, s.left))) kcal left to eat").font(.system(size: 11.5, weight: .semibold)).foregroundStyle(C.mute).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func medium(_ s: Snapshot) -> some View {
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 6) {
                headline(s)
                Spacer(minLength: 0)
                balanceBlock(s, size: 38)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .leading, spacing: 10) {
                mini("LEFT TO EAT", s.left >= 0 ? Format.int(s.left) : "0", s.left >= 0 ? "kcal" : "fresh budget tomorrow", C.green)
                mini("STEPS", Format.int(s.steps), "of \(Format.int(s.stepGoal))", C.steps)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func mini(_ label: String, _ value: String, _ unit: String, _ c: Color) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).font(.system(size: 10.5, weight: .heavy)).tracking(0.8).foregroundStyle(C.mute)
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(value).font(.metric(20)).foregroundStyle(c)
                Text(unit).font(.system(size: 11, weight: .semibold)).foregroundStyle(C.mute).lineLimit(1)
            }
        }
    }
}
#endif

// MARK: - Widgets

struct BalanceWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PulseBalance", provider: GlanceProvider()) { entry in
            BalanceGlance(entry: entry)
                .containerBackground(for: .widget) { C.bg }
                .widgetURL(URL(string: "pulse://today"))
        }
        .configurationDisplayName("Balance")
        .description("Burned versus eaten today.")
        #if os(watchOS)
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryCorner, .accessoryInline])
        #else
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
        #endif
    }
}

struct LeftWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PulseLeft", provider: GlanceProvider()) { entry in
            LeftGlance(entry: entry)
                .containerBackground(for: .widget) { C.bg }
                .widgetURL(URL(string: "pulse://today"))
        }
        .configurationDisplayName("Left to eat")
        .description("How much you can still eat today and stay on pace.")
        #if os(watchOS)
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryCorner, .accessoryInline])
        #else
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryInline])
        #endif
    }
}

@main
struct PulseWidgets: WidgetBundle {
    var body: some Widget {
        BalanceWidget()
        LeftWidget()
    }
}
