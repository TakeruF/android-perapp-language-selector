import AppKit
import Foundation

private let canvasSize = NSSize(width: 1024, height: 500)
private let assetRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
private let iconURL = assetRoot.appendingPathComponent("app-icon-512.png")

private struct LocaleGraphic {
    let locale: String
    let subtitle: String
    let privacyLabel: String
}

private let graphics = [
    LocaleGraphic(locale: "en-US", subtitle: "Choose a language for every app", privacyLabel: "ON-DEVICE  •  PRIVATE"),
    LocaleGraphic(locale: "zh-CN", subtitle: "为每个应用选择显示语言", privacyLabel: "仅限设备端  •  隐私保护"),
    LocaleGraphic(locale: "fr-FR", subtitle: "Choisissez la langue de chaque application", privacyLabel: "LOCAL  •  PRIVÉ"),
    LocaleGraphic(locale: "ja-JP", subtitle: "アプリごとに表示言語を選択", privacyLabel: "端末内処理  •  プライバシー"),
    LocaleGraphic(locale: "ko-KR", subtitle: "앱마다 표시 언어를 선택하세요", privacyLabel: "기기 내 처리  •  개인정보 보호"),
    LocaleGraphic(locale: "es-419", subtitle: "Elige el idioma de cada app", privacyLabel: "EN EL DISPOSITIVO  •  PRIVADO"),
    LocaleGraphic(locale: "es-ES", subtitle: "Elige el idioma de cada app", privacyLabel: "EN EL DISPOSITIVO  •  PRIVADO"),
]

private func color(_ red: Int, _ green: Int, _ blue: Int, alpha: CGFloat = 1) -> NSColor {
    NSColor(
        calibratedRed: CGFloat(red) / 255,
        green: CGFloat(green) / 255,
        blue: CGFloat(blue) / 255,
        alpha: alpha
    )
}

private func makeBitmap() -> NSBitmapImageRep {
    NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: Int(canvasSize.width),
        pixelsHigh: Int(canvasSize.height),
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bitmapFormat: [],
        bytesPerRow: 0,
        bitsPerPixel: 0
    )!
}

private func drawBackground() {
    let background = NSGradient(
        starting: color(239, 244, 255),
        ending: color(211, 225, 255)
    )!
    background.draw(in: NSRect(origin: .zero, size: canvasSize), angle: -10)

    color(101, 137, 244, alpha: 0.14).setFill()
    NSBezierPath(ovalIn: NSRect(x: -100, y: -155, width: 540, height: 540)).fill()

    color(123, 99, 240, alpha: 0.10).setFill()
    NSBezierPath(ovalIn: NSRect(x: 790, y: 280, width: 330, height: 330)).fill()

    color(255, 255, 255, alpha: 0.46).setFill()
    NSBezierPath(roundedRect: NSRect(x: 56, y: 54, width: 912, height: 392), xRadius: 54, yRadius: 54).fill()
}

private func drawIcon(_ icon: NSImage) {
    let iconRect = NSRect(x: 92, y: 82, width: 336, height: 336)
    let clipPath = NSBezierPath(roundedRect: iconRect, xRadius: 78, yRadius: 78)

    NSGraphicsContext.saveGraphicsState()
    let shadow = NSShadow()
    shadow.shadowColor = color(37, 61, 125, alpha: 0.24)
    shadow.shadowBlurRadius = 26
    shadow.shadowOffset = NSSize(width: 0, height: -11)
    shadow.set()
    color(242, 246, 255).setFill()
    clipPath.fill()
    NSGraphicsContext.restoreGraphicsState()

    NSGraphicsContext.saveGraphicsState()
    clipPath.addClip()
    icon.draw(in: iconRect, from: .zero, operation: .sourceOver, fraction: 1)
    NSGraphicsContext.restoreGraphicsState()

    color(255, 255, 255, alpha: 0.72).setStroke()
    clipPath.lineWidth = 2
    clipPath.stroke()
}

private func drawText(subtitle: String, privacyLabel: String) {
    let titleStyle = NSMutableParagraphStyle()
    titleStyle.alignment = .left

    let title = NSAttributedString(
        string: "Per-App Language",
        attributes: [
            .font: NSFont.systemFont(ofSize: 57, weight: .bold),
            .foregroundColor: color(31, 49, 91),
            .paragraphStyle: titleStyle,
            .kern: -1.4,
        ]
    )
    title.draw(in: NSRect(x: 474, y: 261, width: 474, height: 76))

    let subtitleStyle = NSMutableParagraphStyle()
    subtitleStyle.alignment = .left
    subtitleStyle.lineBreakMode = .byWordWrapping
    subtitleStyle.minimumLineHeight = 36
    subtitleStyle.maximumLineHeight = 36

    var fontSize: CGFloat = 31
    var subtitleText: NSAttributedString!
    while true {
        subtitleText = NSAttributedString(
            string: subtitle,
            attributes: [
                .font: NSFont.systemFont(ofSize: fontSize, weight: .medium),
                .foregroundColor: color(73, 91, 134),
                .paragraphStyle: subtitleStyle,
                .kern: -0.25,
            ]
        )
        let bounds = subtitleText.boundingRect(
            with: NSSize(width: 466, height: 88),
            options: [.usesLineFragmentOrigin, .usesFontLeading]
        )
        if bounds.height <= 78 || fontSize <= 25 { break }
        fontSize -= 1
    }
    subtitleText.draw(with: NSRect(x: 477, y: 158, width: 466, height: 92), options: [.usesLineFragmentOrigin, .usesFontLeading])

    let privacy = NSAttributedString(
        string: privacyLabel,
        attributes: [
            .font: NSFont.systemFont(ofSize: 12, weight: .semibold),
            .foregroundColor: color(67, 98, 194),
            .kern: 0.45,
        ]
    )
    let privacyWidth = min(310, ceil(privacy.size().width) + 28)
    let pill = NSBezierPath(roundedRect: NSRect(x: 477, y: 114, width: privacyWidth, height: 30), xRadius: 15, yRadius: 15)
    color(77, 113, 226, alpha: 0.12).setFill()
    pill.fill()
    privacy.draw(in: NSRect(x: 491, y: 122, width: privacyWidth - 28, height: 18))
}

private func render(subtitle: String, privacyLabel: String, to outputURL: URL) throws {
    guard let icon = NSImage(contentsOf: iconURL) else {
        throw NSError(domain: "FeatureGraphic", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unable to load app icon"])
    }
    let bitmap = makeBitmap()
    let context = NSGraphicsContext(bitmapImageRep: bitmap)!

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = context
    drawBackground()
    drawIcon(icon)
    drawText(subtitle: subtitle, privacyLabel: privacyLabel)
    context.flushGraphics()
    NSGraphicsContext.restoreGraphicsState()

    guard let png = bitmap.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "FeatureGraphic", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unable to encode PNG"])
    }
    try FileManager.default.createDirectory(at: outputURL.deletingLastPathComponent(), withIntermediateDirectories: true)
    try png.write(to: outputURL, options: .atomic)
}

for graphic in graphics {
    let output = assetRoot
        .appendingPathComponent("localized")
        .appendingPathComponent(graphic.locale)
        .appendingPathComponent("feature-graphic.png")
    try render(subtitle: graphic.subtitle, privacyLabel: graphic.privacyLabel, to: output)
    print("Rendered \(graphic.locale): \(output.path)")
}

let defaultOutput = assetRoot.appendingPathComponent("feature-graphic-1024x500.png")
try render(subtitle: graphics[0].subtitle, privacyLabel: graphics[0].privacyLabel, to: defaultOutput)
print("Updated default: \(defaultOutput.path)")
