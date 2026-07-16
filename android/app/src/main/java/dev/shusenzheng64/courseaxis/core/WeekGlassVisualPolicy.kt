package dev.shusenzheng64.courseaxis.core

object WeekGlassVisualPolicy {
    const val backdropTopArgb: Int = 0xC210172E.toInt()
    const val backdropBottomArgb: Int = 0xD12B2446.toInt()
    const val headerSurfaceArgb: Int = 0x8A202A49.toInt()
    const val navigationSurfaceArgb: Int = 0xD4141B31.toInt()
    const val courseSurfaceArgb: Int = 0xC46B7FD7.toInt()
    const val examSurfaceArgb: Int = 0xD2D06A3A.toInt()
    const val primaryTextArgb: Int = 0xFFFFFFFF.toInt()
    const val secondaryTextArgb: Int = 0xD6FFFFFF.toInt()
    const val subtleTextArgb: Int = 0xAFFFFFFF.toInt()
    const val highlightArgb: Int = 0x66FFFFFF
    const val gridArgb: Int = 0x24FFFFFF
    const val accentArgb: Int = 0xFF78E9FF.toInt()
    const val filterMinimumTouchDp: Float = 44f
    const val usesSystemBlur: Boolean = true
    const val systemBlurRadiusPx: Int = 84
    const val usesLiveBlur: Boolean = false
    const val usesContinuousAnimation: Boolean = false

    fun alpha(argb: Int): Int = argb ushr 24 and 0xFF
}
