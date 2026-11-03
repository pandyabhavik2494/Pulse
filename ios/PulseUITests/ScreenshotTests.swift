import XCTest

/// Seeds the simulator's Health store with sample data, allows Health access, and saves a screenshot of
/// each screen to SCREENSHOT_DIR (default: the project's screenshots folder). Simulator only.
final class ScreenshotTests: XCTestCase {
    private var dir: String {
        ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] ?? "/Volumes/SSD/Code/PulseiOS/screenshots"
    }
    private var prefix: String { ProcessInfo.processInfo.environment["SCREENSHOT_PREFIX"] ?? "iphone" }

    private func shot(_ name: String) {
        sleep(1)
        let data = XCUIScreen.main.screenshot().pngRepresentation
        try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        try? data.write(to: URL(fileURLWithPath: "\(dir)/\(prefix)-\(name).png"))
    }

    func testScreens() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-seedHealth"]
        app.launch()

        // Health's permission sheet (only the first time on a simulator). Its wording changes between iOS
        // versions, so tap whatever "select all" and "allow" control is there until Today shows.
        let selectAll = NSPredicate(format: "label BEGINSWITH 'Select All' OR label BEGINSWITH 'Turn On All' OR label BEGINSWITH 'Turn on All'")
        let go = NSPredicate(format: "label IN {'Allow', 'Continue'}")
        for _ in 0..<12 {
            if app.buttons["dash_burned"].exists { break }
            let all = app.descendants(matching: .any).matching(selectAll).firstMatch
            if all.exists && all.isHittable { all.tap(); sleep(1) }
            let history = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'All Recorded Data'")).firstMatch
            if history.exists && history.isHittable { history.tap(); sleep(1) }
            let next = app.descendants(matching: .any).matching(go).firstMatch
            if next.exists {
                // "Allow" sits at the very end of the sheet's long list.
                var n = 0
                while !next.isHittable && n < 25 { app.swipeUp(velocity: .fast); n += 1 }
                if next.isHittable { next.tap(); sleep(2); continue }
            }
            if app.staticTexts["Health Access"].exists {
                try? app.debugDescription.write(toFile: "\(dir)/health-sheet-tree.txt", atomically: true, encoding: .utf8)
            }
            sleep(2)
        }
        XCTAssertTrue(app.buttons["dash_burned"].waitForExistence(timeout: 30))
        sleep(4)
        shot("1-today")
        app.swipeUp()
        shot("2-today-tiles")
        app.swipeUp(velocity: .fast)
        shot("3-today-recovery")
        app.swipeDown(velocity: .fast); app.swipeDown(velocity: .fast)

        let burned = app.buttons["dash_burned"]
        if burned.waitForExistence(timeout: 5) {
            burned.tap()
            shot("4-section-calories")
            app.swipeDown(velocity: .fast)
            sleep(1)
        }
        let weight = app.buttons["dash_weight"]
        if weight.waitForExistence(timeout: 5) {
            weight.tap()
            shot("5-section-body")
            app.swipeDown(velocity: .fast)
            sleep(1)
        }
        app.buttons["dashboard_edit"].firstMatch.tap()
        shot("6-customize")
        app.swipeDown(velocity: .fast)
        sleep(1)

        app.tabBars.buttons["Journey"].tap()
        shot("7-journey")
        app.buttons["1,000"].firstMatch.tap()
        shot("8-journey-what-if")
        app.swipeUp()
        shot("9-journey-milestones")

        app.tabBars.buttons["History"].tap()
        shot("10-history")
        app.tabBars.buttons["Settings"].tap()
        shot("11-settings")
    }
}
