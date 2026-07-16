package dev.shusenzheng64.courseaxis.core

object SettingsJellyVisualPolicy {
    const val backdropTopArgb: Int = 0xC210172E.toInt()
    const val backdropBottomArgb: Int = 0xD12B2446.toInt()
    const val topBarSurfaceArgb: Int = 0xA51A2340.toInt()
    const val navigationSurfaceArgb: Int = 0xD4141B31.toInt()
    const val cardSurfaceArgb: Int = 0x76283255
    const val cardStrongSurfaceArgb: Int = 0x9A303A63.toInt()
    const val fieldSurfaceArgb: Int = 0x66202A49
    const val primaryTextArgb: Int = 0xFFFFFFFF.toInt()
    const val secondaryTextArgb: Int = 0xD6FFFFFF.toInt()
    const val subtleTextArgb: Int = 0xAFFFFFFF.toInt()
    const val accentArgb: Int = 0xFF78E9FF.toInt()
    const val accentSoftArgb: Int = 0x2878E9FF
    const val highlightArgb: Int = 0x70FFFFFF
    const val secondaryButtonArgb: Int = 0x4D596B9C
    const val primaryButtonStartArgb: Int = 0xFF38C8F1.toInt()
    const val primaryButtonEndArgb: Int = 0xFF778CFF.toInt()

    const val cardCornerDp: Float = 22f
    const val fieldCornerDp: Float = 16f
    const val cardBorderDp: Float = 0.8f
    const val fieldBorderDp: Float = 0.7f
    const val focusedFieldBorderDp: Float = 1.5f
    const val cardElevationDp: Float = 2f
    const val minimumTouchDp: Float = 48f

    const val usesSystemBlur: Boolean = true
    const val usesLiveBlur: Boolean = false
    const val usesContinuousAnimation: Boolean = false

    fun alpha(argb: Int): Int = argb ushr 24 and 0xFF
}
