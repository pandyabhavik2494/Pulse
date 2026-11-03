import Foundation
import CoreMotion

/// Today's steps straight from this device's motion sensor, updated within seconds while Pulse is open.
/// Health gets steps in batches, so Pulse shows whichever is higher: Health's total (phone and watch
/// together) or this device's own live count.
final class LiveSteps: @unchecked Sendable {
    static let shared = LiveSteps()
    private let pedometer = CMPedometer()
    private var day: LocalDate?

    /// Starts counting from midnight; [onUpdate] gets the steps so far today on the main queue.
    func start(_ onUpdate: @escaping @MainActor (Int) -> Void) {
        guard CMPedometer.isStepCountingAvailable() else { return }
        pedometer.stopUpdates()
        let today = LocalDate.today()
        day = today
        pedometer.startUpdates(from: today.startDate()) { data, _ in
            guard let n = data?.numberOfSteps.intValue else { return }
            DispatchQueue.main.async { MainActor.assumeIsolated { onUpdate(n) } }
        }
    }

    func stop() { pedometer.stopUpdates() }

    /// True when the day has changed since counting started (restart to count from the new midnight).
    var stale: Bool { day != LocalDate.today() }
}

extension Array where Element == DayLog {
    /// Today's steps raised to the live count when the sensor is ahead of Health.
    func withLiveSteps(_ live: Int?, today: LocalDate = .today()) -> [DayLog] {
        guard let live, live > 0 else { return self }
        var out = self
        if let i = out.firstIndex(where: { $0.date == today }) {
            if (out[i].steps ?? 0) < live { out[i].steps = live }
        } else {
            out.append(DayLog(date: today, steps: live))
        }
        return out
    }
}
