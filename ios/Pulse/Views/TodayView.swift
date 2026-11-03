import SwiftUI

struct TodayView: View {
    @Environment(PulseStore.self) private var store
    let s: HomeState
    @State private var open: Area?
    @State private var editing = false

    private var tiles: [DashTile] {
        let picked = store.dashboard.compactMap { Tiles.byId[$0] }
        return picked.isEmpty ? Tiles.defaultIds.compactMap { Tiles.byId[$0] } : picked
    }

    var body: some View {
        let statuses = Dictionary(uniqueKeysWithValues: tiles.map { ($0.id, Tiles.status($0.id, s)) })
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Header(small: Words.dayShort(s.today), big: "\(Words.partOfDay(s.time)), \(s.profile.name.isEmpty ? "there" : s.profile.name)") {
                    SyncChip(s: s)
                }
                if !store.healthAsked && !store.isDemo { ConnectCard() }
                if let r = s.recap { RecapCard(r: r) }
                VerdictCard(verdict: Verdict(tiles.compactMap { statuses[$0.id] ?? nil }, s.time))
                ForEach(Array(Tiles.groups.enumerated()), id: \.offset) { i, group in
                    let list = tiles.filter { group.1.contains($0.group) }
                    if !list.isEmpty {
                        HStack {
                            Text(group.0.uppercased()).font(.system(size: 12, weight: .heavy)).tracking(1.2).foregroundStyle(C.mute)
                            Spacer()
                            if i == firstGroupIndex {
                                Button { editing = true } label: {
                                    Label("Customize", systemImage: "slider.horizontal.3")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(C.soft)
                                }
                                .accessibilityIdentifier("dashboard_edit")
                            }
                        }
                        .padding(.horizontal, 4).padding(.top, 6)
                        Grid(horizontalSpacing: 10, verticalSpacing: 10) {
                            ForEach(stride(from: 0, to: list.count, by: 2).map { $0 }, id: \.self) { j in
                                GridRow {
                                    TileCell(tile: list[j], s: s, status: statuses[list[j].id] ?? nil) { open = list[j].area }
                                    if j + 1 < list.count {
                                        TileCell(tile: list[j + 1], s: s, status: statuses[list[j + 1].id] ?? nil) { open = list[j + 1].area }
                                    } else {
                                        Color.clear.gridCellUnsizedAxes([.horizontal, .vertical])
                                    }
                                }
                            }
                        }
                    }
                }
                Legend().padding(.top, 4)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .refreshable { await store.sync() }
        .pulsePage()
        .sheet(item: $open) { area in
            ScrollView {
                SectionView(area: area, s: s).padding(16)
            }
            .pulseSheet()
        }
        .sheet(isPresented: $editing) {
            CustomizeSheet(s: s, current: store.dashboard) { store.setDashboard($0) }
                .pulseSheet()
        }
    }

    private var firstGroupIndex: Int {
        Tiles.groups.firstIndex { g in tiles.contains { g.1.contains($0.group) } } ?? 0
    }
}

/// "● Health · 3 min ago", tap to refresh.
struct SyncChip: View {
    @Environment(PulseStore.self) private var store
    let s: HomeState

    var body: some View {
        Button { Task { await store.sync() } } label: {
            HStack(spacing: 6) {
                Circle().fill(store.syncing ? C.steps : C.green).frame(width: 7, height: 7)
                Text(store.syncing ? "Syncing…" : store.isDemo ? "Sample data" : Words.syncAgo(s.lastSync, s.now))
                    .font(.system(size: 12, weight: .semibold)).foregroundStyle(C.soft)
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(C.s2, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Shown until Health access has been asked for.
struct ConnectCard: View {
    @Environment(PulseStore.self) private var store

    var body: some View {
        Button { Task { await store.connectHealth() } } label: {
            Card(tint: C.food, border: C.food.opacity(0.3)) {
                HStack(spacing: 12) {
                    Image(systemName: "heart.text.square.fill").font(.system(size: 22)).foregroundStyle(C.food)
                    Text("Connect Apple Health so Pulse can read your burn, food from MyFitnessPal and weigh-ins from VeSync.")
                        .font(.system(size: 14, weight: .medium)).foregroundStyle(C.soft).multilineTextAlignment(.leading)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

/// One number: label, value (green only when on track), unit, a calm status, a bar and one kind line.
struct TileCell: View {
    let tile: DashTile
    let s: HomeState
    let status: Status?
    let onTap: () -> Void

    var body: some View {
        let v = tile.read(s)
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                Text(tile.label.uppercased())
                    .font(.system(size: 11.5, weight: .bold)).tracking(1).foregroundStyle(C.mute).lineLimit(1)
                Text(v?.value ?? "—")
                    .font(.metric(29))
                    .foregroundStyle(v == nil ? C.mute : status?.mood == .good ? C.green : C.ink)
                    .shadow(color: status?.mood == .good ? C.green.opacity(0.45) : .clear, radius: 10)
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .padding(.top, 6)
                Text(v?.unit ?? "no data yet")
                    .font(.system(size: 13, weight: .medium)).foregroundStyle(C.soft).lineLimit(1)
                Spacer(minLength: 10)
                if let st = status {
                    StatusTag(status: st)
                    if let bar = st.bar {
                        GlowBar(fraction: bar, colors: [C.mood(st.mood)], height: 4).padding(.top, 7)
                    }
                    Text(st.why)
                        .font(.system(size: 12, weight: .medium)).foregroundStyle(C.mute)
                        .lineLimit(2).fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 6)
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 13)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(C.s2, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("dash_\(tile.id)")
    }
}

/// The verdict on top: a kind headline, one win, one small next step, and the count per status.
struct VerdictCard: View {
    let verdict: Verdict

    var body: some View {
        let head = verdict.anyGrow ? C.steady : C.green
        Card(tint: head, border: head.opacity(0.25)) {
            HStack(spacing: 10) {
                Circle().fill(head).frame(width: 10, height: 10).shadow(color: head, radius: 5)
                Text(verdict.headline).font(.system(size: 22, weight: .heavy, design: .rounded)).foregroundStyle(head)
            }
            Text(verdict.line)
                .font(.system(size: 15, weight: .medium)).foregroundStyle(C.soft)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
            FlowCounts(verdict: verdict).padding(.top, 12)
        }
        .accessibilityIdentifier("verdict")
    }
}

private struct FlowCounts: View {
    let verdict: Verdict
    var body: some View {
        let items: [(Mood, String)] = [(.good, "on track"), (.steady, "steady"), (.grow, "room to grow"), (.wait, "filling in")]
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) { chips(items) }
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) { chips(Array(items.prefix(2))) }
                HStack(spacing: 8) { chips(Array(items.suffix(2))) }
            }
        }
    }

    @ViewBuilder private func chips(_ items: [(Mood, String)]) -> some View {
        ForEach(items, id: \.0) { m, word in
            let c = verdict.count(m)
            if c > 0 {
                HStack(spacing: 6) {
                    Circle().fill(C.mood(m)).frame(width: 8, height: 8)
                    Text("\(c) \(word)").font(.system(size: 12.5, weight: .semibold)).foregroundStyle(C.soft)
                }
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(C.s2, in: Capsule())
            }
        }
    }
}

private struct Legend: View {
    var body: some View {
        HStack(spacing: 12) {
            ForEach([(Mood.good, "On track"), (.steady, "Steady"), (.grow, "Room to grow"), (.wait, "Filling in")], id: \.0) { m, w in
                HStack(spacing: 5) {
                    Circle().fill(C.mood(m)).frame(width: 7, height: 7)
                    Text(w).font(.system(size: 11, weight: .medium)).foregroundStyle(C.mute)
                }
            }
        }
        .padding(.horizontal, 4)
    }
}

/// The Monday recap of last week.
struct RecapCard: View {
    let r: WeeklyRecap
    @AppStorage("recapDismissed") private var dismissed = 0

    var body: some View {
        if dismissed != r.weekStart.epochDay {
            Card(tint: C.green, border: C.green.opacity(0.22)) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("WEEK \(r.weekNumber), DONE").font(.system(size: 12, weight: .heavy)).tracking(1).foregroundStyle(C.green)
                        Text("Trend this week").font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute)
                    }
                    Spacer()
                    Button { dismissed = r.weekStart.epochDay } label: {
                        Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(C.mute)
                            .frame(width: 32, height: 32).background(C.s2, in: Circle())
                    }
                    .buttonStyle(.plain)
                }
                Text("\(Words.signedKg(r.trendChangeKg)) kg")
                    .font(.metric(38)).foregroundStyle(r.trendChangeKg <= 0 ? C.green : C.ink)
                    .padding(.top, 6)
                Text(r.headline).font(.system(size: 14, weight: .medium)).foregroundStyle(C.soft).padding(.top, 2)
                HStack(spacing: 8) {
                    stat("\(r.daysUnder)/7", "days burned more", C.green)
                    stat(Format.int(r.totalSteps), "steps", C.steps)
                    stat(r.bestDayBalance.map(Format.int) ?? "—", r.bestDay.map { "best day, \(Weekday.short[$0.dayOfWeek - 1])" } ?? "best day", C.ink)
                }
                .padding(.top, 12)
                Text("Beat last week: \(r.focusDaysUnder) days burning more than you ate.")
                    .font(.system(size: 15, weight: .bold)).foregroundStyle(C.ink).padding(.top, 12)
                Text(r.focusTip).font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute).padding(.top, 4)
            }
        }
    }

    private func stat(_ v: String, _ l: String, _ c: Color) -> some View {
        VStack(spacing: 4) {
            Text(v).font(.metric(19)).foregroundStyle(c).lineLimit(1).minimumScaleFactor(0.6)
            Text(l).font(.system(size: 11, weight: .medium)).foregroundStyle(C.mute).multilineTextAlignment(.center).lineLimit(2)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10).padding(.horizontal, 4)
        .background(C.s2, in: RoundedRectangle(cornerRadius: 14))
    }
}

/// Pick the dashboard's numbers: tap to add or remove; they show in the order picked.
struct CustomizeSheet: View {
    let s: HomeState
    let current: [String]
    let onSave: ([String]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var picked: [String] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Customize Today").font(.system(size: 22, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
            Text("Tap numbers to add or remove them. They show in the order you pick them.")
                .font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute).padding(.top, 4)
            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(["Calories", "Activity", "Weight", "Heart", "Sleep"], id: \.self) { group in
                        Text(group.uppercased()).font(.system(size: 12, weight: .heavy)).tracking(1).foregroundStyle(C.mute).padding(.top, 12)
                        ForEach(Tiles.all.filter { $0.group == group }) { t in row(t) }
                    }
                }
                .padding(.vertical, 8)
            }
            HStack(spacing: 10) {
                Button { picked = Tiles.defaultIds } label: {
                    Text("Reset").font(.system(size: 16, weight: .bold)).frame(maxWidth: .infinity).frame(height: 50)
                        .foregroundStyle(C.ink).background(C.s3, in: RoundedRectangle(cornerRadius: 16))
                }
                Button { onSave(picked); dismiss() } label: {
                    Text(picked.isEmpty ? "Pick one" : "Save").font(.system(size: 16, weight: .bold)).frame(maxWidth: .infinity).frame(height: 50)
                        .foregroundStyle(C.bg).background(C.go, in: RoundedRectangle(cornerRadius: 16))
                }
                .disabled(picked.isEmpty)
            }
            .buttonStyle(.plain)
        }
        .padding(20)
        .onAppear { picked = current.filter { Tiles.byId[$0] != nil } }
    }

    private func row(_ t: DashTile) -> some View {
        let pos = picked.firstIndex(of: t.id)
        let on = pos != nil
        let v = t.read(s)
        return Button {
            if let pos { picked.remove(at: pos) } else { picked.append(t.id) }
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(on ? t.color : C.s3).frame(width: 26, height: 26)
                    if let pos { Text("\(pos + 1)").font(.system(size: 13, weight: .heavy)).foregroundStyle(C.bg) }
                }
                Text(t.label).font(.system(size: 15, weight: .semibold)).foregroundStyle(C.ink)
                Spacer()
                Text(v.map { "\($0.value) \($0.unit)" } ?? "no data yet").font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute).lineLimit(1)
            }
            .padding(.horizontal, 14).frame(minHeight: 52)
            .background(on ? t.color.opacity(0.12) : C.s2, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(on ? t.color.opacity(0.6) : C.line))
        }
        .buttonStyle(.plain)
    }
}
