import SwiftUI

enum CourseAxisDesign {
    static let pagePadding: CGFloat = 26
    static let cardRadius: CGFloat = 16
    static let compactSpacing: CGFloat = 8
    static let sectionSpacing: CGFloat = 18
    static let accent = Color(red: 0.26, green: 0.43, blue: 0.96)
    static let pageBackground = Color(nsColor: .windowBackgroundColor).opacity(0.72)
    static let surface = Color(nsColor: .controlBackgroundColor)
    static let border = Color.primary.opacity(0.08)
}

struct AmbientBackground: View {
    var body: some View {
        ZStack {
            CourseAxisDesign.pageBackground
            LinearGradient(colors: [CourseAxisDesign.accent.opacity(0.07), .clear, Color.purple.opacity(0.045)], startPoint: .topLeading, endPoint: .bottomTrailing)
            GeometryReader { proxy in
                Circle().fill(CourseAxisDesign.accent.opacity(0.055)).frame(width: 320, height: 320).blur(radius: 70).offset(x: proxy.size.width * 0.62, y: -170)
                Circle().fill(Color.purple.opacity(0.04)).frame(width: 280, height: 280).blur(radius: 80).offset(x: -110, y: proxy.size.height * 0.64)
            }
        }.allowsHitTesting(false)
    }
}

private struct InteractiveSurfaceModifier: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hovering = false
    func body(content: Content) -> some View {
        content
            .scaleEffect(VisualMotionPolicy.scale(isHovering: hovering, reduceMotion: reduceMotion))
            .shadow(color: .black.opacity(hovering && !reduceMotion ? 0.08 : 0), radius: hovering ? 12 : 0, y: hovering ? 5 : 0)
            .animation(.easeOut(duration: VisualMotionPolicy.duration(reduceMotion: reduceMotion)), value: hovering)
            .onHover { hovering = $0 }
    }
}

extension View {
    func interactiveSurface() -> some View { modifier(InteractiveSurfaceModifier()) }
}

struct PageHeader<Actions: View>: View {
    let eyebrow: String
    let title: String
    let subtitle: String
    let icon: String
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        HStack(spacing: 15) {
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(LinearGradient(colors: [CourseAxisDesign.accent, CourseAxisDesign.accent.opacity(0.72)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 52, height: 52)
                Image(systemName: icon).font(.title2.bold()).foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(eyebrow).font(.caption2.weight(.bold)).tracking(1.2).foregroundStyle(CourseAxisDesign.accent)
                Text(title).font(.largeTitle.bold())
                Text(subtitle).foregroundStyle(.secondary)
            }
            Spacer()
            HStack(spacing: 10, content: actions)
        }.padding(CourseAxisDesign.pagePadding)
    }
}

struct SurfacePanel<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        content().padding(14)
            .background(CourseAxisDesign.surface, in: RoundedRectangle(cornerRadius: CourseAxisDesign.cardRadius))
            .overlay(RoundedRectangle(cornerRadius: CourseAxisDesign.cardRadius).stroke(CourseAxisDesign.border))
            .shadow(color: .black.opacity(0.035), radius: 10, y: 3)
    }
}

struct StatusBadge: View {
    let text: String
    var color: Color = .secondary
    var icon: String? = nil
    var body: some View {
        HStack(spacing: 5) { if let icon { Image(systemName: icon) }; Text(text) }
            .font(.caption.weight(.medium)).foregroundStyle(color)
            .padding(.horizontal, 9).padding(.vertical, 4)
            .background(color.opacity(0.11), in: Capsule())
    }
}

struct MetricCard: View {
    let title: String; let value: String; let icon: String; var tint: Color = .accentColor
    var body: some View { HStack(spacing: 12) { ZStack{RoundedRectangle(cornerRadius:10).fill(tint.opacity(0.12)).frame(width:38,height:38);Image(systemName: icon).font(.title3).foregroundStyle(tint)}; VStack(alignment:.leading,spacing:2){Text(value).font(.title2.bold()).lineLimit(1);Text(title).font(.caption).foregroundStyle(.secondary)};Spacer() }.padding(15).background(CourseAxisDesign.surface,in:RoundedRectangle(cornerRadius:CourseAxisDesign.cardRadius)).overlay(RoundedRectangle(cornerRadius:CourseAxisDesign.cardRadius).stroke(CourseAxisDesign.border)).shadow(color:.black.opacity(0.03),radius:8,y:2).interactiveSurface() }
}

extension EventKind {
    var displayName:String{switch self{case .COURSE:"课程";case .EXAM:"考试";case .CUSTOM:"自定义";case .ADJUSTMENT:"调课"}}
    var symbolName:String{switch self{case .COURSE:"book.closed.fill";case .EXAM:"checkmark.seal.fill";case .CUSTOM:"calendar.badge.plus";case .ADJUSTMENT:"arrow.triangle.2.circlepath"}}
}

extension String { func character(at offset:Int)->Character{self[index(startIndex,offsetBy:offset)]} }
