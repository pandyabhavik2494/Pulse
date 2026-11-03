import Foundation
import WatchConnectivity

/// Phone <-> watch: the phone sends the profile and today's glance; the watch reads Health itself and
/// uses the phone's glance only to fill gaps (for example food the watch's Health store hasn't synced yet).
final class WatchLink: NSObject, WCSessionDelegate, @unchecked Sendable {
    static let shared = WatchLink()

    /// Called on the main queue when the phone's context arrives (watch side).
    var onContext: (@MainActor (Profile?, Snapshot?) -> Void)?

    private var session: WCSession? { WCSession.isSupported() ? WCSession.default : nil }

    func activate() {
        guard let s = session, s.delegate == nil else { return }
        s.delegate = self
        s.activate()
    }

    func send(profile: Profile, snapshot: Snapshot) {
        #if os(iOS)
        guard let s = session, s.activationState == .activated, s.isPaired, s.isWatchAppInstalled else { return }
        var ctx: [String: Any] = [:]
        if let p = try? JSONEncoder().encode(profile) { ctx["profile"] = p }
        if let v = try? JSONEncoder().encode(snapshot) { ctx["snapshot"] = v }
        try? s.updateApplicationContext(ctx)
        #endif
    }

    /// The last context received (watch side).
    var received: (Profile?, Snapshot?) {
        guard let ctx = session?.receivedApplicationContext else { return (nil, nil) }
        return Self.decode(ctx)
    }

    private static func decode(_ ctx: [String: Any]) -> (Profile?, Snapshot?) {
        let p = (ctx["profile"] as? Data).flatMap { try? JSONDecoder().decode(Profile.self, from: $0) }
        let s = (ctx["snapshot"] as? Data).flatMap { try? JSONDecoder().decode(Snapshot.self, from: $0) }
        return (p, s)
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        let (p, s) = Self.decode(applicationContext)
        DispatchQueue.main.async { [weak self] in
            MainActor.assumeIsolated { self?.onContext?(p, s) }
        }
    }

    #if os(iOS)
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { session.activate() }
    #endif
}
