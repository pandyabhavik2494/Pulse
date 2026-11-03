import SwiftUI

/// Name, height, age, sex, start weight and goal (85 kg by default), then Apple Health access.
/// Also used from Settings to edit the profile.
struct OnboardingView: View {
    @Environment(PulseStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    var existing: Profile? = nil

    @State private var name = ""
    @State private var height = ""
    @State private var age = ""
    @State private var male = true
    @State private var startKg = ""
    @State private var goalKg = "85"
    @State private var stepGoal = "10000"
    @State private var step = 0
    @State private var prefilled = false
    @AppStorage("onboardingDone") private var onboardingDone = false

    private var profile: Profile? {
        guard let h = num(height), let a = Int(age), let s = num(startKg), let g = num(goalKg), h > 50, a > 0, s > 0, g > 0 else { return nil }
        return Profile(name: name.trimmingCharacters(in: .whitespaces), heightCm: h, age: a, male: male, startKg: s, goalKg: g,
                       startDate: existing?.startDate ?? .today(), stepGoal: Int(stepGoal) ?? 10_000)
    }

    private func num(_ s: String) -> Double? { Double(s.replacingOccurrences(of: ",", with: ".")) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if existing == nil {
                    Image(systemName: "waveform.path.ecg")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundStyle(C.go)
                        .shadow(color: C.green.opacity(0.6), radius: 14)
                        .padding(.top, 30)
                    Text(step == 0 ? "Hi, I’m Pulse" : "One last thing")
                        .font(.system(size: 34, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
                    Text(step == 0 ? "A calm buddy for your weight loss. A few numbers about you, then Pulse does the maths."
                         : "Pulse reads Apple Health: your burn and steps from your Apple Watch, food from MyFitnessPal and weigh-ins from VeSync. It only reads, never writes.")
                        .font(.system(size: 16, weight: .medium)).foregroundStyle(C.soft)
                } else {
                    Text("Your profile").font(.system(size: 26, weight: .heavy, design: .rounded)).foregroundStyle(C.ink).padding(.top, 12)
                }

                if step == 0 {
                    Card {
                        field("Name", $name, "Bhavik", keyboard: .default)
                        field("Height", $height, "cm", keyboard: .decimalPad)
                        field("Age", $age, "years", keyboard: .numberPad)
                        HStack {
                            Text("Sex (for the burn estimate)").font(.system(size: 15, weight: .medium)).foregroundStyle(C.soft)
                            Spacer()
                            Picker("", selection: $male) {
                                Text("Male").tag(true)
                                Text("Female").tag(false)
                            }
                            .pickerStyle(.segmented).frame(width: 160)
                        }
                        .padding(.vertical, 8)
                        Rectangle().fill(C.line).frame(height: 1)
                        field("Start weight", $startKg, "kg", keyboard: .decimalPad)
                        field("Goal weight", $goalKg, "kg", keyboard: .decimalPad)
                        field("Daily steps goal", $stepGoal, "steps", keyboard: .numberPad, divider: false)
                    }
                    Button {
                        guard let p = profile else { return }
                        store.saveProfile(p)
                        if existing != nil { dismiss() } else { step = 1 }
                    } label: {
                        Text(existing == nil ? "Next" : "Save")
                            .font(.system(size: 17, weight: .bold)).foregroundStyle(C.bg)
                            .frame(maxWidth: .infinity).frame(height: 54)
                            .background(profile == nil ? AnyShapeStyle(C.s3) : AnyShapeStyle(C.go), in: RoundedRectangle(cornerRadius: 18))
                    }
                    .buttonStyle(.plain)
                    .disabled(profile == nil)
                    .accessibilityIdentifier("onboarding_next")
                } else {
                    Button {
                        Task { await store.connectHealth(); onboardingDone = true }
                    } label: {
                        Label("Connect Apple Health", systemImage: "heart.fill")
                            .font(.system(size: 17, weight: .bold)).foregroundStyle(C.bg)
                            .frame(maxWidth: .infinity).frame(height: 54)
                            .background(C.go, in: RoundedRectangle(cornerRadius: 18))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("onboarding_connect")
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 30)
            .frame(maxWidth: 600)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .pulsePage()
        .task {
            guard !prefilled else { return }
            prefilled = true
            if let p = existing {
                name = p.name; height = Words.kgShort(p.heightCm); age = "\(p.age)"; male = p.male
                startKg = Words.kgShort(p.startKg); goalKg = Words.kgShort(p.goalKg); stepGoal = "\(p.stepGoal)"
            }
        }
    }

    private func field(_ label: String, _ text: Binding<String>, _ unit: String, keyboard: UIKeyboardType, divider: Bool = true) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text(label).font(.system(size: 15, weight: .medium)).foregroundStyle(C.soft)
                Spacer()
                TextField(unit, text: text)
                    .keyboardType(keyboard)
                    .multilineTextAlignment(.trailing)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(C.ink)
                    .frame(maxWidth: 150)
            }
            .padding(.vertical, 12)
            if divider { Rectangle().fill(C.line).frame(height: 1) }
        }
    }
}
