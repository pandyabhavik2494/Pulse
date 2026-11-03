import SwiftUI

/// A dark rounded card with an optional soft colour wash and border.
struct Card<Content: View>: View {
    var tint: Color? = nil
    var border: Color = C.line
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(C.s1)
                    .overlay {
                        if let tint {
                            RoundedRectangle(cornerRadius: 22, style: .continuous)
                                .fill(LinearGradient(colors: [tint.opacity(0.13), .clear], startPoint: .topLeading, endPoint: .bottomTrailing))
                        }
                    }
            }
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(border, lineWidth: 1))
    }
}

/// Small caps label with an icon in a tinted circle.
struct Lbl: View {
    let text: String
    var icon: String? = nil
    var color: Color = C.mute

    var body: some View {
        HStack(spacing: 8) {
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(color)
                    .frame(width: 24, height: 24)
                    .background(color.opacity(0.15), in: Circle())
            }
            Text(text.uppercased())
                .font(.system(size: 12, weight: .bold))
                .tracking(1)
                .foregroundStyle(C.mute)
        }
    }
}

/// A label and value on one line with a hairline under it.
struct FieldRow: View {
    let label: String
    let value: String
    var valueColor: Color = C.ink
    var note: String? = nil
    var dot: Color? = nil
    var divider = true

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                if let dot { Circle().fill(dot).frame(width: 8, height: 8) }
                Text(label).font(.system(size: 15, weight: .medium)).foregroundStyle(C.soft)
                Spacer(minLength: 8)
                if let note { Text(note).font(.system(size: 13, weight: .medium)).foregroundStyle(C.mute) }
                Text(value).font(.system(size: 15, weight: .bold, design: .rounded)).foregroundStyle(valueColor)
            }
            .padding(.vertical, 11)
            if divider { Rectangle().fill(C.line).frame(height: 1) }
        }
    }
}

/// A rounded progress bar with an optional glow.
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
                    .shadow(color: glow ? colors[0].opacity(0.6) : .clear, radius: 6)
            }
        }
        .frame(height: height)
    }
}

/// A dot and a word: "● On track".
struct StatusTag: View {
    let status: Status
    var size: CGFloat = 12

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(C.mood(status.mood)).frame(width: 7, height: 7)
                .shadow(color: C.mood(status.mood).opacity(0.7), radius: 3)
            Text(status.word).font(.system(size: size, weight: .heavy)).foregroundStyle(C.mood(status.mood))
        }
    }
}

/// A pill showing "6 weeks sooner" (green) or "4 days later" (soft orange, never red).
struct DeltaPill: View {
    let text: String
    let sooner: Bool

    var body: some View {
        let c = sooner ? C.green : C.hot
        Text(text)
            .font(.system(size: 11.5, weight: .heavy))
            .foregroundStyle(c)
            .padding(.horizontal, 9).padding(.vertical, 3)
            .background(c.opacity(0.14), in: Capsule())
    }
}

/// The screen title block: a small line over a big one, with trailing items.
struct Header<Trailing: View>: View {
    let small: String
    let big: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Text(small).font(.system(size: 14, weight: .semibold)).foregroundStyle(C.mute)
                Text(big).font(.system(size: 30, weight: .heavy, design: .rounded)).foregroundStyle(C.ink)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            Spacer()
            trailing
        }
        .padding(.top, 8)
    }
}

/// Sheet chrome shared by section and customize sheets.
extension View {
    func pulseSheet() -> some View {
        self.presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .presentationBackground(C.bg)
            .presentationCornerRadius(28)
    }

    /// The page background with a faint glow at the top.
    func pulsePage() -> some View {
        self.background {
            ZStack(alignment: .top) {
                C.bg
                RadialGradient(colors: [C.wt.opacity(0.10), .clear], center: .topTrailing, startRadius: 0, endRadius: 420)
                RadialGradient(colors: [C.green.opacity(0.07), .clear], center: .topLeading, startRadius: 0, endRadius: 360)
            }
            .ignoresSafeArea()
        }
    }
}
