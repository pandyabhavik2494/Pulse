import SwiftUI

struct SettingsView: View {
    @Environment(PulseStore.self) private var store
    let s: HomeState
    @State private var editing = false
    @State private var confirmReset = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Header(small: "Settings", big: s.profile.name.isEmpty ? "You" : s.profile.name) { EmptyView() }
                Card {
                    Lbl(text: "Your plan", icon: "flag.fill", color: C.wt)
                        .padding(.bottom, 6)
                    FieldRow(label: "Start", value: "\(Words.kgShort(s.profile.startKg)) kg · \(Format.date(s.profile.startDate, s.today))")
                    FieldRow(label: "Goal", value: "\(Words.kgShort(s.profile.goalKg)) kg", valueColor: C.wt2)
                    FieldRow(label: "Height", value: "\(Int(s.profile.heightCm.rounded())) cm")
                    FieldRow(label: "Age", value: "\(s.profile.age)")
                    FieldRow(label: "Step goal", value: Format.int(s.profile.stepGoal))
                    FieldRow(label: "Pace", value: "0.75 kg a week · \(Format.int(Budget.paceKcal)) kcal a day", divider: false)
                    Button { editing = true } label: {
                        Text("Edit profile").font(.system(size: 15, weight: .bold)).foregroundStyle(C.bg)
                            .frame(maxWidth: .infinity).frame(height: 46)
                            .background(C.go, in: RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 10)
                }
                Card {
                    Lbl(text: "Apple Health", icon: "heart.fill", color: C.heart).padding(.bottom, 6)
                    Text("Pulse only reads Health. Weight from VeSync, food from MyFitnessPal, everything else from your Apple Watch and iPhone. It never writes or shares anything.")
                        .font(.system(size: 13.5, weight: .medium)).foregroundStyle(C.soft)
                    FieldRow(label: "Last read", value: Words.syncAgo(s.lastSync, s.now), divider: false)
                    HStack(spacing: 10) {
                        Button { Task { await store.connectHealth() } } label: {
                            Text("Check access").frame(maxWidth: .infinity).frame(height: 44)
                                .background(C.s3, in: RoundedRectangle(cornerRadius: 14))
                        }
                        Button { Task { await store.sync() } } label: {
                            Text("Read now").frame(maxWidth: .infinity).frame(height: 44)
                                .background(C.s3, in: RoundedRectangle(cornerRadius: 14))
                        }
                    }
                    .font(.system(size: 14, weight: .bold)).foregroundStyle(C.ink)
                    .buttonStyle(.plain)
                    .padding(.top, 6)
                    Text("To change what Pulse can read: Settings app → Health → Data Access & Devices → Pulse.")
                        .font(.system(size: 12, weight: .medium)).foregroundStyle(C.mute).padding(.top, 10)
                }
                Card {
                    Lbl(text: "Stored here", icon: "internaldrive.fill", color: C.steps).padding(.bottom, 6)
                    FieldRow(label: "Days saved on this iPhone", value: "\(store.days.count)", divider: false)
                    Button("Start over", role: .destructive) { confirmReset = true }
                        .font(.system(size: 14, weight: .semibold)).tint(C.grow)
                        .padding(.top, 8)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .pulsePage()
        .sheet(isPresented: $editing) {
            OnboardingView(existing: s.profile).pulseSheet()
        }
        .confirmationDialog("Start over? Your profile and saved days on this iPhone are cleared. Apple Health keeps everything.", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Start over", role: .destructive) { store.reset() }
        }
    }
}
