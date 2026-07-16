package dev.shusenzheng64.courseaxis.widget

object WidgetGlassVisualPolicy {
    const val backdropLightArgb: Long = 0xD91C2442
    const val backdropDarkArgb: Long = 0xD9141830
    const val primaryTextArgb: Long = 0xFFF8F7FF
    const val secondaryTextArgb: Long = 0xC7F3F0FF
    const val dividerArgb: Long = 0x38FFFFFF
    const val rowSurfaceArgb: Long = 0x18FFFFFF
    const val accentArgb: Long = 0xFF63E6FF
    const val usesLiveBlur: Boolean = false
    const val usesContinuousAnimation: Boolean = false

    fun alpha(argb: Long): Int = ((argb ushr 24) and 0xFF).toInt()
}
