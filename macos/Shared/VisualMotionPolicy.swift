import Foundation

enum VisualMotionPolicy {
    static func scale(isHovering: Bool, reduceMotion: Bool) -> Double {
        guard isHovering, !reduceMotion else { return 1.0 }
        return 1.018
    }

    static func duration(reduceMotion: Bool) -> Double {
        reduceMotion ? 0 : 0.22
    }
}
