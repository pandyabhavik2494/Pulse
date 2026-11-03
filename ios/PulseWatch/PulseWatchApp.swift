import SwiftUI
import WidgetKit

@main
struct PulseWatchApp: App {
    @State private var store = WatchStore()

    var body: some Scene {
        WindowGroup {
            WatchToday()
                .environment(store)
                .task { await store.launch() }
        }
    }
}

/// Reads Health on the watch directly; the phone sends the profile (and its own glance, used only to fill
/// gaps such as food the watch's Health store hasn't received yet).
@MainActor
@Observable
final class WatchStore {
    private(set) var state: HomeState?
    private(set) var phone: Snapshot?
    private var profile: Profile?
    private var days: [DayLog] = []
    private let demo = ProcessInfo.processInfo.arguments.contains("-demo") || UserDefaults.standard.bool(forKey: "PulseDemoData")

    init() {
        let (p, s) = WatchLink.shared.received
        profile = p ?? (Snapshot.defaults.data(forKey: "profile")).flatMap { try? JSONDecoder().decode(Profile.self, from: $0) }
        phone = s
        if demo {
            profile = DemoData.profile
            days = DemoData.days()
        }
        rebuild()
        WatchLink.shared.onContext = { [weak self] p, s in
            guard let self else { return }
            if let p { self.save(p) }
            self.phone = s
            self.rebuild()
        }
    }

    func launch() async {
        WatchLink.shared.activate()
        guard !demo, HealthReader.isAvailable else { return }
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-seedHealth") {
            try? await HealthSeeder.seed(HealthReader.shared.store)
            save(DemoData.profile)
        }
        #endif
        try? await HealthReader.shared.requestAuthorization()
        if profile == nil { await guessProfile() }
        HealthReader.shared.observe { [weak self] in await self?.refresh() }
        await refresh()
    }

    func refresh() async {
        guard !demo else { rebuild(); return }
        let today = LocalDate.today()
        days = await HealthReader.shared.days(from: today.minusDays(14), to: today)
        rebuild()
    }

    private func save(_ p: Profile) {
        profile = p
        if let data = try? JSONEncoder().encode(p) { Snapshot.defaults.set(data, forKey: "profile") }
    }

    /// Before the phone has sent a profile: Health's own height, age, sex and weight, goal 85 kg.
    private func guessProfile() async {
        let b = await HealthReader.shared.body()
        guard let kg = b.weightKg else { return }
        save(Profile(name: "", heightCm: b.heightCm ?? 175, age: b.age ?? 32, male: b.male ?? true, startKg: kg, goalKg: 85, startDate: .today()))
    }

    private func rebuild() {
        guard let p = profile else { state = nil; return }
        var merged = days
        let today = LocalDate.today()
        // Food is logged on the phone; if the watch hasn't got it yet, use the phone's numbers for today.
        if let phone, phone.day == today {
            if let i = merged.firstIndex(where: { $0.date == today }) {
                if merged[i].eaten == nil { merged[i].eaten = phone.eaten }
                if merged[i].burnedTotal == nil && merged[i].burnedActive == nil { merged[i].burnedTotal = phone.burned }
                if (merged[i].steps ?? 0) < phone.steps { merged[i].steps = phone.steps }
            } else {
                merged.append(DayLog(date: today, steps: phone.steps, burnedTotal: phone.burned, eaten: phone.eaten))
            }
        }
        let s = HomeState.build(profile: p, days: merged, now: Date())
        state = s
        let snap = Snapshot(s)
        var old = Snapshot.load()
        old?.updated = snap.updated
        if snap != old {
            snap.save()
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}

struct WatchToday: View {
    @Environment(WatchStore.self) private var store

    var body: some View {
        if let s = store.state {
            TabView {
                BalancePage(s: s)
                LeftPage(s: s)
                StepsPage(s: s)
            }
            .tabViewStyle(.verticalPage)
            .background(C.bg)
        } else {
            VStack(spacing: 8) {
                Image(systemName: "iphone").font(.title2).foregroundStyle(C.green)
                Text("Open Pulse on your iPhone to set up.").font(.system(size: 15, weight: .semibold)).multilineTextAlignment(.center)
            }
            .padding()
        }
    }
}

private struct HeadlineChip: View {
    let s: HomeState
    var body: some View {
        let v = Verdict(s.glanceStatuses, s.time)
        let c = v.anyGrow ? C.steady : C.green
        HStack(spacing: 6) {
            Circle().fill(c).frame(width: 7, height: 7).shadow(color: c, radius: 4)
            Text(v.headline).font(.system(size: 15, weight: .heavy, design: .rounded)).foregroundStyle(c).lineLimit(1)
        }
    }
}

private struct BalancePage: View {
    let s: HomeState
    var body: some View {
        let color = s.eatenMore ? C.grow : C.green
        VStack(alignment: .leading, spacing: 2) {
            HeadlineChip(s: s)
            Spacer(minLength: 4)
            if let b = s.balance {
                Text(Format.int(abs(b)))
                    .font(.metric(50)).foregroundStyle(color)
                    .shadow(color: color.opacity(0.6), radius: 12)
                    .minimumScaleFactor(0.6).lineLimit(1)
                Text(Words.balance(eatenMore: s.eatenMore)).font(.system(size: 15, weight: .bold)).foregroundStyle(color)
            } else {
                Text("No food yet").font(.system(size: 26, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
                Text("Log in MyFitnessPal").font(.system(size: 14, weight: .semibold)).foregroundStyle(C.mute)
            }
            Spacer(minLength: 4)
            HStack(spacing: 4) {
                Text(s.burned.map(Format.int) ?? "—").foregroundStyle(C.ink).fontWeight(.bold)
                Text("burned −").foregroundStyle(C.mute)
                Text(s.eaten.map(Format.int) ?? "0").foregroundStyle(C.ink).fontWeight(.bold)
                Text("eaten").foregroundStyle(C.mute)
            }
            .font(.system(size: 13, design: .rounded)).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .containerBackground(LinearGradient(colors: [color.opacity(0.28), C.bg], startPoint: .top, endPoint: .bottom), for: .tabView)
    }
}

private struct LeftPage: View {
    let s: HomeState
    var body: some View {
        let b = s.budget
        let st = Moods.left(b.left, b.budget)
        VStack(alignment: .leading, spacing: 2) {
            Label("Left to eat", systemImage: "fork.knife").font(.system(size: 14, weight: .bold)).foregroundStyle(C.food)
            Spacer(minLength: 4)
            Text(Format.int(abs(b.left)))
                .font(.metric(50)).foregroundStyle(st.mood == .good ? C.green : C.ink)
                .shadow(color: st.mood == .good ? C.green.opacity(0.6) : .clear, radius: 12)
                .minimumScaleFactor(0.6).lineLimit(1)
            Text(b.left >= 0 ? "kcal to stay on pace" : "kcal past today’s plan").font(.system(size: 14, weight: .semibold)).foregroundStyle(C.soft)
            Spacer(minLength: 4)
            StatusLine(status: st)
            GlowBar(fraction: st.bar ?? 0, colors: [C.mood(st.mood)], height: 6).padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .containerBackground(LinearGradient(colors: [C.food.opacity(0.22), C.bg], startPoint: .top, endPoint: .bottom), for: .tabView)
    }
}

private struct StepsPage: View {
    let s: HomeState
    var body: some View {
        let goal = s.profile.stepGoal
        let st = Moods.steps(s.steps, goal, s.time)
        VStack(alignment: .leading, spacing: 2) {
            Label("Steps", systemImage: "figure.walk").font(.system(size: 14, weight: .bold)).foregroundStyle(C.steps)
            Spacer(minLength: 4)
            Text(Format.int(s.steps))
                .font(.metric(48)).foregroundStyle(st.mood == .good ? C.green : C.ink)
                .minimumScaleFactor(0.6).lineLimit(1)
            Text("of \(Format.int(goal))").font(.system(size: 14, weight: .semibold)).foregroundStyle(C.soft)
            Spacer(minLength: 4)
            StatusLine(status: st)
            GlowBar(fraction: Double(s.steps) / Double(max(goal, 1)), colors: [C.steps, C.green], height: 6, glow: true).padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .containerBackground(LinearGradient(colors: [C.steps.opacity(0.25), C.bg], startPoint: .top, endPoint: .bottom), for: .tabView)
    }
}

private struct StatusLine: View {
    let status: Status
    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(C.mood(status.mood)).frame(width: 6, height: 6)
            Text(status.word).font(.system(size: 12, weight: .heavy)).foregroundStyle(C.mood(status.mood))
            Text(status.why).font(.system(size: 12, weight: .medium)).foregroundStyle(C.mute).lineLimit(1)
        }
    }
}

/// The same rounded bar as on the phone.
struct GlowBar: View {
    let fraction: Double
    var colors: [Color] = [C.green]
    var height: CGFloat = 8
    var glow = false

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(C.s3)
                Capsule()
                    .fill(LinearGradient(colors: colors.count == 1 ? [colors[0], colors[0]] : colors, startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(height, g.size.width * fraction.clamped(0.02, 1)))
                    .shadow(color: glow ? colors[0].opacity(0.6) : .clear, radius: 5)
            }
        }
        .frame(height: height)
    }
}
